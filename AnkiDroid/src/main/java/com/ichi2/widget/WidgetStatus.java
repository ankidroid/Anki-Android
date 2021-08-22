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
import android.util.Pair;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.MetaDB;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.async.BaseAsyncTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.Counts;
import com.ichi2.libanki.sched.DeckDueTreeNode;

import java.util.List;

import timber.log.Timber;

import static com.ichi2.anki.Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION;

/**
 * The status of the widget.
 */
public final class WidgetStatus {

    private static boolean sSmallWidgetEnabled = false;
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    private static android.os.AsyncTask<Context, Void, Context> sUpdateDeckStatusAsyncTask;


    /** This class should not be instantiated. */
    private WidgetStatus() {
    }


    /**
     * Request the widget to update its status.
     * TODO Mike - we can reduce battery usage by widget users by removing updatePeriodMillis from metadata
     *             and replacing it with an alarm we set so device doesn't wake to update the widget, see:
     *             https://developer.android.com/guide/topics/appwidgets/#MetaData
     */
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public static void update(Context context) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        sSmallWidgetEnabled = preferences.getBoolean("widgetSmallEnabled", false);
        boolean notificationEnabled = Integer.parseInt(preferences.getString(MINIMUM_CARDS_DUE_FOR_NOTIFICATION, "1000001")) < 1000000;
        boolean canExecuteTask = ((sUpdateDeckStatusAsyncTask == null) || (sUpdateDeckStatusAsyncTask.getStatus() == android.os.AsyncTask.Status.FINISHED));
        if ((sSmallWidgetEnabled || notificationEnabled) && canExecuteTask) {
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
            Intent intent = new Intent(NotificationService.INTENT_ACTION);
            Context appContext = context.getApplicationContext();
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
        }


        private void updateCounts(Context context) {
            Counts total = new Counts();
            Collection col = CollectionHelper.getInstance().getCol(context);
            // Ensure queues are reset if we cross over to the next day.
            col.getSched()._checkDay();

            // Only count the top-level decks in the total
            List<DeckDueTreeNode> nodes = col.getSched().deckDueTree();
            for (DeckDueTreeNode node : nodes) {
                total.addNew(node.getNewCount());
                total.addLrn(node.getLrnCount());
                total.addRev(node.getRevCount());
            }
            int eta = col.getSched().eta(total, false);
            sSmallWidgetStatus = new Pair<>(total.count(), eta);
        }
    }
}
