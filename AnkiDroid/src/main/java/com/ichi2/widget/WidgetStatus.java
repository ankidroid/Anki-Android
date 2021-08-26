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

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.async.BaseAsyncTask;
import com.ichi2.anki.DecksMetaData;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import timber.log.Timber;

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
     */
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public static void update(Context context) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        sSmallWidgetEnabled = preferences.getBoolean("widgetSmallEnabled", false);
        boolean canExecuteTask = ((sUpdateDeckStatusAsyncTask == null) || (sUpdateDeckStatusAsyncTask.getStatus() == android.os.AsyncTask.Status.FINISHED));
        if (sSmallWidgetEnabled && canExecuteTask) {
            Timber.d("WidgetStatus.update(): updating");
            sUpdateDeckStatusAsyncTask = new UpdateDeckStatusAsyncTask();
            sUpdateDeckStatusAsyncTask.execute(context);
        } else {
            Timber.d("WidgetStatus.update(): already running or not enabled");
        }
    }


    /** Returns the card due and eta of all the due decks. */
    public static int[] fetchSmall(Context context) {
        DecksMetaData metaData = new DecksMetaData(context);
        Pair<Integer, Integer> pair = metaData.getTotalDueCards();
        return new int[] {pair.first, pair.second};
    }

    private static class UpdateDeckStatusAsyncTask extends BaseAsyncTask<Context, Void, Context> {

        @Override
        protected Context doInBackground(Context... params) {
            super.doInBackground(params);
            Timber.d("WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()");
            Context context = params[0];
            if (!AnkiDroidApp.isSdCardMounted() && sSmallWidgetEnabled) {
                return context;
            }
            new AnkiDroidWidgetSmall.UpdateService().doUpdate(context);

            // Shows the notification when widget is not set on Home Screen
            Intent intent = new Intent(NotificationService.INTENT_ACTION);
            Context appContext = context.getApplicationContext();
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
            return context;
        }
    }
}