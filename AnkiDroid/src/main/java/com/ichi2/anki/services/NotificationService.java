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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.NotificationChannels;
import com.ichi2.anki.Preferences;
import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
import com.ichi2.widget.WidgetStatus;

import timber.log.Timber;

import static com.ichi2.anki.Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION;

public class NotificationService extends BroadcastReceiver {

    /** The id of the notification for due cards. */
    private static final int WIDGET_NOTIFY_ID = 1;

    public static final String INTENT_ACTION = "com.ichi2.anki.intent.action.SHOW_NOTIFICATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.i("NotificationService: OnStartCommand");
        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        int minCardsDue = Integer.parseInt(preferences.getString(MINIMUM_CARDS_DUE_FOR_NOTIFICATION, Integer.toString(Preferences.PENDING_NOTIFICATIONS_ONLY)));
        int dueCardsCount = WidgetStatus.fetchDue(context);
        if (dueCardsCount >= minCardsDue) {
            // Build basic notification
            String cardsDueText = context.getResources()
                    .getQuantityString(R.plurals.widget_minimum_cards_due_notification_ticker_text, dueCardsCount, dueCardsCount);

            // This generates a log warning "Use of stream types is deprecated..."
            // The NotificationCompat code uses setSound() no matter what we do and triggers it.
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context,
                            NotificationChannels.getId(NotificationChannels.Channel.GENERAL))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
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
            resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent resultPendingIntent = CompatHelper.getCompat().getImmutableActivityIntent(context, 0, resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(resultPendingIntent);
            // mId allows you to update the notification later on.
            manager.notify(WIDGET_NOTIFY_ID, builder.build());
        } else {
            // Cancel the existing notification, if any.
            manager.cancel(WIDGET_NOTIFY_ID);
        }
    }
}
