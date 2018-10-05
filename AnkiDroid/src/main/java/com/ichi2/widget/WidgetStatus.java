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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.MetaDB;
import com.ichi2.async.BaseAsyncTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Sched;

import java.util.List;

import timber.log.Timber;

/**
 * The status of the widget.
 */
public final class WidgetStatus {

    private static boolean sSmallWidgetEnabled = false;
    private static AsyncTask<Context, Void, Context> sUpdateDeckStatusAsyncTask;


    /** This class should not be instantiated. */
    private WidgetStatus() {
    }


    /**
     * Request the widget to update its status.
     * TODO Mike - we can reduce battery usage by widget users by removing updatePeriodMillis from metadata
     *             and replacing it with an alarm we set so device doesn't wake to update the widget, see:
     *             https://developer.android.com/guide/topics/appwidgets/#MetaData
     */
    public static void update(Context context) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        sSmallWidgetEnabled = preferences.getBoolean("widgetSmallEnabled", false);
        if (sSmallWidgetEnabled &&
                ((sUpdateDeckStatusAsyncTask == null) || (sUpdateDeckStatusAsyncTask.getStatus() == AsyncTask.Status.FINISHED))) {
            Timber.d("WidgetStatus.update(): updating");
            sUpdateDeckStatusAsyncTask = new UpdateDeckStatusAsyncTask();
            sUpdateDeckStatusAsyncTask.execute(context);
        } else {
            Timber.d("WidgetStatus.update(): already running or not enabled");
        }
    }


    /** Returns the status of each of the decks. */
    public static int[] fetchSmall(Context context) {
        return MetaDB.getWidgetSmallStatus(context);
    }


    public static int fetchDue(Context context) {
        return MetaDB.getNotificationStatus(context);
    }


    private static class UpdateDeckStatusAsyncTask extends BaseAsyncTask<Context, Void, Context> {

        // due, eta
        private static Pair<Integer, Integer> sSmallWidgetStatus = new Pair<>(0, 0);

        @Override
        protected Context doInBackground(Context... params) {
            super.doInBackground(params);
            Timber.d("WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()");
            Context context = params[0];
            if (!AnkiDroidApp.isSdCardMounted()) {
                return context;
            }
            try {
                updateCounts(context);
            } catch (Exception e) {
                Timber.e(e, "Could not update widget");
            }
            return context;
        }


        @Override
        protected void onPostExecute(Context context) {
            super.onPostExecute(context);
            Timber.d("WidgetStatus.UpdateDeckStatusAsyncTask.onPostExecute()");
            MetaDB.storeSmallWidgetStatus(context, sSmallWidgetStatus);
            if (sSmallWidgetEnabled) {
                new AnkiDroidWidgetSmall.UpdateService().doUpdate(context);
            }
        }


        private void updateCounts(Context context) {
            int[] total = {0, 0, 0};
            Collection col = CollectionHelper.getInstance().getCol(context);
            // Ensure queues are reset if we cross over to the next day.
            col.getSched()._checkDay();

            // Only count the top-level decks in the total
            List<Sched.DeckDueTreeNode> nodes = col.getSched().deckDueTree();
            for (Sched.DeckDueTreeNode node : nodes) {
                total[0] += node.newCount;
                total[1] += node.lrnCount;
                total[2] += node.revCount;
            }
            int due = total[0] + total[1] + total[2];
            int eta = col.getSched().eta(total, false);
            sSmallWidgetStatus = new Pair<>(due, eta);
        }
    }
}
