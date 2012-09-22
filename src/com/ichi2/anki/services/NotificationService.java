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

package com.ichi2.anki.services;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.widget.WidgetStatus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import android.util.Log;

public class NotificationService extends Service {

    /** The notification service to show notifications of due cards. */
    private NotificationManager mNotificationManager;

    /** The id of the notification for due cards. */
    private static final int WIDGET_NOTIFY_ID = 1;


    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }


    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(AnkiDroidApp.TAG, "NotificationService: OnStart");

        Context context = AnkiDroidApp.getInstance().getBaseContext();
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        int minimumCardsDueForNotification = Integer.parseInt(preferences.getString("minimumCardsDueForNotification",
                "25"));
        int dueCardsCount = WidgetStatus.fetchDue(context);
        if (dueCardsCount >= minimumCardsDueForNotification) {
            // Show a notification
            int icon = R.drawable.anki;
            CharSequence tickerText = String.format(
                    getString(R.string.widget_minimum_cards_due_notification_ticker_text), dueCardsCount);
            long when = System.currentTimeMillis();

            Notification notification = new Notification(icon, tickerText, when);

            if (preferences.getBoolean("widgetVibrate", false)) {
                notification.defaults |= Notification.DEFAULT_VIBRATE;
            }
            if (preferences.getBoolean("widgetBlink", false)) {
                notification.defaults |= Notification.DEFAULT_LIGHTS;
            }

            Context appContext = getApplicationContext();
            CharSequence contentTitle = getText(R.string.widget_minimum_cards_due_notification_ticker_title);

            Intent ankiDroidIntent = new Intent(context, DeckPicker.class);
            ankiDroidIntent.setAction(Intent.ACTION_MAIN);
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingAnkiDroidIntent = PendingIntent.getActivity(context, 0, ankiDroidIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            notification.setLatestEventInfo(appContext, contentTitle, tickerText, pendingAnkiDroidIntent);

            mNotificationManager.notify(WIDGET_NOTIFY_ID, notification);
        } else {
            // Cancel the existing notification, if any.
            mNotificationManager.cancel(WIDGET_NOTIFY_ID);
        }
    }


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
}
