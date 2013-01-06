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

package com.ichi2.widget;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.MetaDB;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;

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
import java.util.HashMap;
import java.util.TreeSet;

/**
 * The status of the widget.
 * <p>
 * It contains the status of each of the decks.
 */
public final class WidgetStatus {

    private static boolean mediumWidget = false;
    private static boolean smallWidget = false;
    private static boolean bigWidget = false;
    private static boolean notification = false;

    private static DeckStatus sDeckStatus;
    private static float[] sSmallWidgetStatus;
    private static TreeSet<Object[]> sDeckCounts;

    private static AsyncTask<Context, Void, Context> sUpdateDeckStatusAsyncTask;


    /** This class should not be instantiated. */
    private WidgetStatus() {
    }


    /** Request the widget to update its status. */
    public static void update(Context context) {
        update(context, true, null, null, null);
    }


    /** Request the widget to update its status. */
    public static void update(Context context, DeckStatus deckStatus) {
        update(context, true, deckStatus, null, null);
    }


    public static void update(Context context, TreeSet<Object[]> deckCounts) {
        update(context, true, null, null, deckCounts);
    }


    public static void update(Context context, float[] smallWidgetStatus) {
        update(context, true, null, smallWidgetStatus, null);
    }


    public static void update(Context context, boolean updateBigWidget, DeckStatus deckStatus, float[] smallWidgetStatus, TreeSet<Object[]> deckCounts) {
        sDeckStatus = deckStatus;
    	sSmallWidgetStatus = smallWidgetStatus;
    	sDeckCounts = deckCounts;

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        if (preferences.getBoolean("widgetMediumEnabled", false)) {
            mediumWidget = true;
        } else {
            mediumWidget = false;
        }
        if (preferences.getBoolean("widgetSmallEnabled", false)) {
            smallWidget = true;
        } else {
            smallWidget = false;
        }
        if (updateBigWidget && preferences.getBoolean("widgetBigEnabled", false)) {
            bigWidget = true;
        } else {
            bigWidget = false;
        }
        if (Integer.parseInt(preferences.getString("minimumCardsDueForNotification", "1000001")) < 1000000
                && sDeckStatus == null) {
            notification = true;
        } else {
            notification = false;
        }
        if ((mediumWidget || smallWidget || bigWidget || notification)
                && ((sUpdateDeckStatusAsyncTask == null) || (sUpdateDeckStatusAsyncTask.getStatus() == AsyncTask.Status.FINISHED))) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.update(): updating");
            sUpdateDeckStatusAsyncTask = new UpdateDeckStatusAsyncTask();
            sUpdateDeckStatusAsyncTask.execute(context);
        } else {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.update(): already running or not enabled");
        }
    }


    public static void waitToFinish() {
        try {
            if ((sUpdateDeckStatusAsyncTask != null)
                    && (sUpdateDeckStatusAsyncTask.getStatus() != AsyncTask.Status.FINISHED)) {
                Log.i(AnkiDroidApp.TAG, "WidgetStatus: wait to finish");
                sUpdateDeckStatusAsyncTask.get();
            }
        } catch (Exception e) {
            return;
        }
    }


    /** Returns the status of each of the decks. */
    public static DeckStatus[] fetch(Context context) {
        return MetaDB.getWidgetStatus(context);
    }


    /** Returns the status of each of the decks. */
    public static int[] fetchSmall(Context context) {
        return MetaDB.getWidgetSmallStatus(context);
    }


    public static int fetchDue(Context context) {
        return MetaDB.getNotificationStatus(context);
    }


    public static DeckStatus getDeckStatus(Decks deck) {
        if (deck == null) {
            return null;
        }
        int dueCards = 0;
        int newCards = 0;
        // int failedCards = deck.getFailedSoonCount();
        // int eta = 0;
        // int reps = deck.getSessionFinishedCards();
        //
        //
        // if(!deck.hasFinishScheduler()) {
        // dueCards = deck.getRevCount();
        // newCards = deck.getNewCountToday();
        // eta = deck.getETA();
        // }
        // return new DeckStatus(deck.getDeckPath(), deck.getDeckName(), newCards, dueCards, failedCards, eta, reps);
        // return new DeckStatus("aaa", "aaa", 1, 1, 1, 1, 1);
        return null;
    }

    private static class UpdateDeckStatusAsyncTask extends AsyncTask<Context, Void, Context> {
        private static final DeckStatus[] EMPTY_DECK_STATUS = new DeckStatus[0];

        private static DeckStatus[] mDecks = EMPTY_DECK_STATUS;
        private static float[] mSmallWidgetStatus = new float[]{0, 0, 0, 0};


        @Override
        protected Context doInBackground(Context... params) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()");
            Context context = params[0];

            if (!AnkiDroidApp.isSdCardMounted()) {
                return context;
            }

            // For the deck information
//            ArrayList<DeckStatus> decks = new ArrayList<DeckStatus>();

            // if (sDeckStatus != null && mDecks != null && mDecks.length > 0) {
            // decks = new ArrayList<DeckStatus>(mDecks.length);
            // int dues = 0;
            // for (DeckStatus m : mDecks) {
            // if (m.mDeckId == sDeckStatus.mDeckId) {
            // Log.i(AnkiDroidApp.TAG, "UpdateWidget - update information for deck " + sDeckStatus.mDeckName);
            // sDeckStatus.mDeckName = m.mDeckName;
            // sDeckStatus.mDepth = m.mDepth;
            // decks.add(sDeckStatus);
            // } else {
            // Log.i(AnkiDroidApp.TAG, "UpdateWidget - copy information for deck " + m.mDeckName);
            // decks.add(m);
            // }
            // }
            // } else {
            try {
            	if (sSmallWidgetStatus == null) {
                    Collection col = AnkiDroidApp.openCollection(AnkiDroidApp.getCollectionPath());
                    mSmallWidgetStatus = col.getSched().progressToday(sDeckCounts, null, true);
                    AnkiDroidApp.closeCollection(false);
            	} else {
            		mSmallWidgetStatus = sSmallWidgetStatus;
            	}
//                Collection col = Collection.currentCollection();              
//                Object[] di;
//                float progress;
//                if (col == null) {
//                    col = Collection.openCollection(AnkiDroidApp.getCollectionPath());
//                    di = col.getSched().deckCounts();
//                    progress = col.getSched().todaysProgress(null, true, true);
//                    col.close(false);
//                } else {
//                    di = col.getSched().deckCounts();
//                    progress = col.getSched().progressTodayAll(di);
//                }
//                int eta = (Integer) di[1];
//                for (Object[] d : (TreeSet<Object[]>) di[0]) {
//                    String[] sname = (String[]) d[0];
//                    StringBuilder name = new StringBuilder();
//                    name.append(sname[0]);
//                    for (int i = 1; i < sname.length; i++) {
//                        name.append("::").append(sname[i]);
//                    }
//                    decks.add(new DeckStatus((Long) d[1], name.toString(), (Integer) d[2], (Integer) d[3],
//                            (Integer) d[4], (int) (progress * 100), eta));
//                }
            } catch (SQLException e) {
                Log.i(AnkiDroidApp.TAG, "Widget: Problems on retrieving deck information");
            }
//             }
//
//            mDecks = decks.toArray(EMPTY_DECK_STATUS);
            return context;
        }


        @Override
        protected void onPostExecute(Context context) {
            Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.onPostExecute()");
            MetaDB.storeSmallWidgetStatus(context, mSmallWidgetStatus);
//            MetaDB.storeWidgetStatus(context, mDecks);
//            if (mediumWidget) {
//                Intent intent;
//                intent = new Intent(context, AnkiDroidWidgetMedium.UpdateService.class);
//                intent.setAction(AnkiDroidWidgetMedium.UpdateService.ACTION_UPDATE);
//                context.startService(intent);
//            }
            if (smallWidget) {
                Intent intent;
                intent = new Intent(context, AnkiDroidWidgetSmall.UpdateService.class);
                context.startService(intent);
            }
//            if (bigWidget) {
//                Intent intent;
//                intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);
//                intent.setAction(AnkiDroidWidgetBig.UpdateService.ACTION_UPDATE);
//                context.startService(intent);
//            }
            if (notification) {
                Intent intent;
                intent = new Intent(context, NotificationService.class);
                context.startService(intent);
            }
        }
    }

    // /** Comparator that sorts instances of {@link DeckStatus} based on number of due cards. */
    // public static class ByDueComparator implements java.util.Comparator<DeckStatus> {
    // @Override
    // public int compare(DeckStatus deck1, DeckStatus deck2) {
    // // Reverse due cards number order
    // return deck2.mDueCards - deck1.mDueCards;
    // }
    // }
    //
    //
    // /** Comparator that sorts instances of {@link DeckStatus} based on number of due cards. */
    // public static class ByNameComparator implements java.util.Comparator<DeckStatus> {
    // @Override
    // public int compare(DeckStatus deck1, DeckStatus deck2) {
    // return - deck2.mDeckName.compareTo(deck1.mDeckName);
    // }
    // }
    //
    // /** Filter for Anki files. */
    // public static final class AnkiFileFilter implements FileFilter {
    // @Override
    // public boolean accept(File pathname) {
    // return pathname.isFile() && pathname.getName().endsWith(".anki");
    // }
    // }
}
