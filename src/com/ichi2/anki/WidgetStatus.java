/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The status of the widget.
 * <p>
 * It contains the status of each of the decks.
 */
public final class WidgetStatus {
    /** This class should not be instantiated. */
    private WidgetStatus() {}

    /** Request the widget to update its status. */
    public static void update(Context context) {
        Log.d(AnkiDroidApp.TAG, "WidgetStatus.update()");
        AsyncTask<Context,Void,Context> updateDeckStatusAsyncTask = new UpdateDeckStatusAsyncTask();
        updateDeckStatusAsyncTask.execute(context);
    }

    /** Returns the status of each of the decks. */
    public static DeckStatus[] fetch(Context context) {
        return MetaDB.getWidgetStatus(context);
    }

    private static class UpdateDeckStatusAsyncTask extends AsyncTask<Context, Void, Context> {
        private static final DeckStatus[] EMPTY_DECK_STATUS = new DeckStatus[0];

        private static DeckStatus[] mDecks = EMPTY_DECK_STATUS;

        @Override
        protected Context doInBackground(Context... params) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()");

            Context context = params[0];

            SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
            String deckPath = preferences.getString("deckPath",
                    AnkiDroidApp.getStorageDirectory() + "/AnkiDroid");

            File dir = new File(deckPath);

            File[] fileList = dir.listFiles(new AnkiFileFilter());

            if (fileList == null || fileList.length == 0) {
                mDecks = EMPTY_DECK_STATUS;
                return context;
            }

            // For the deck information
            ArrayList<DeckStatus> decks = new ArrayList<DeckStatus>(fileList.length);

            for (File file : fileList) {
                try {
                    // Run through the decks and get the information
                    String absPath = file.getAbsolutePath();
                    String deckName = file.getName().replaceAll(".anki", "");

                    Log.i(AnkiDroidApp.TAG, "Found deck: " + absPath);

                    Deck deck = Deck.openDeck(absPath, false);
                    int dueCards = deck.getDueCount();
                    int newCards = deck.getNewCount();
                    int failedCards = deck.getFailedSoonCount();
                    // Close the database connection, but only if this is not the current database.
                    // Probably we need to make this atomic to be sure it will not cause a failure.
                    Deck currentDeck = AnkiDroidApp.deck();
                    if (currentDeck != null && currentDeck.getDB() != deck.getDB()) {
                        deck.closeDeck();
                    }

                    // Add the information about the deck
                    decks.add(new DeckStatus(absPath, deckName, newCards, dueCards, failedCards));
                } catch (SQLException e) {
                    Log.i(AnkiDroidApp.TAG, "Could not open deck");
                    Log.e(AnkiDroidApp.TAG, e.toString());
                }
            }

            if (!decks.isEmpty() && decks.size() > 1) {
                // Sort and reverse the list if there are decks
                Log.i(AnkiDroidApp.TAG, "Sorting deck");

                // Ordered by reverse due cards number
                Collections.sort(decks, new ByDueComparator());
            }

            mDecks = decks.toArray(EMPTY_DECK_STATUS);
            return context;
        }

        @Override
        protected void onPostExecute(Context context) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.onPostExecute()");
            MetaDB.storeWidgetStatus(context, mDecks);
            Intent intent = new Intent(context, AnkiDroidWidget.UpdateService.class);
            intent.setAction(AnkiDroidWidget.UpdateService.ACTION_UPDATE);
            context.startService(intent);
        }

        /** Comparator that sorts instances of {@link DeckStatus} based on number of due cards. */
        private static class ByDueComparator implements java.util.Comparator<DeckStatus> {
            @Override
            public int compare(DeckStatus deck1, DeckStatus deck2) {
                // Reverse due cards number order
                return deck2.mDueCards - deck1.mDueCards;
            }
        }

        /** Filter for Anki files. */
        private static final class AnkiFileFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().endsWith(".anki");
            }
        }
    }
}
