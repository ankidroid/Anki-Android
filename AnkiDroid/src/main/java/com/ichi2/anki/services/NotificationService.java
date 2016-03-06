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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.IntentCompat;


import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.widget.WidgetStatus;

import timber.log.Timber;

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
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.i("NotificationService: OnStartCommand");

        Context context = getApplicationContext();
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        int minCardsDue = Integer.parseInt(preferences.getString("minimumCardsDueForNotification", "25"));
        int dueCardsCount = WidgetStatus.fetchDue(context);
        if (dueCardsCount >= minCardsDue) {
            // Build basic notification
            String cardsDueText = getString(R.string.widget_minimum_cards_due_notification_ticker_text, dueCardsCount);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setColor(ContextCompat.getColor(context, R.color.material_light_blue_700))
                    .setContentTitle(cardsDueText)
                    .setTicker(cardsDueText);
            // Enable vibrate and blink if set in preferences
            if (preferences.getBoolean("widgetVibrate", false)) {
                builder.setVibrate(new long[] { 1000, 1000, 1000});
            }
            if (preferences.getBoolean("widgetBlink", false)) {
                builder.setLights(Color.BLUE, 1000, 1000);
            }
            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(context, DeckPicker.class);
            resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(resultPendingIntent);
            // mId allows you to update the notification later on.
            mNotificationManager.notify(WIDGET_NOTIFY_ID, builder.build());
        } else {
            // Cancel the existing notification, if any.
            mNotificationManager.cancel(WIDGET_NOTIFY_ID);
        }
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
}
