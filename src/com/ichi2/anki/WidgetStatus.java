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
import android.content.SharedPreferences;
import android.database.SQLException;
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
public class WidgetStatus {
    /** Returns the status of each of the decks. */
    public static ArrayList<DeckStatus> fetch(Context context) {
        Log.i(AnkiDroidApp.TAG, "fetchDeckStatus");

        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        String deckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory() + "/AnkiDroid");

        File dir = new File(deckPath);

        File[] fileList = dir.listFiles(new AnkiFileFilter());

        if (fileList == null || fileList.length == 0) {
            return new ArrayList<DeckStatus>();
        }

        // For the deck information
        ArrayList<DeckStatus> information = new ArrayList<DeckStatus>(fileList.length);

        for (File file : fileList) {
            try {
                // Run through the decks and get the information
                String absPath = file.getAbsolutePath();
                String deckName = file.getName().replaceAll(".anki", "");

                Deck deck = Deck.openDeck(absPath);
                int dueCards = deck.getDueCount();
                int newCards = deck.getNewCountToday();
                int failedCards = deck.getFailedSoonCount();
                deck.closeDeck();

                // Add the information about the deck
                information.add(new DeckStatus(deckName, newCards, dueCards, failedCards));
            } catch (SQLException e) {
                Log.i(AnkiDroidApp.TAG, "Could not open deck");
                Log.e(AnkiDroidApp.TAG, e.toString());
            }
        }

        if (!information.isEmpty() && information.size() > 1) {
            // Sort and reverse the list if there are decks
            Log.i(AnkiDroidApp.TAG, "Sorting deck");

            // Ordered by reverse due cards number
            Collections.sort(information, new ByDueComparator());
        }

        return information;
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
