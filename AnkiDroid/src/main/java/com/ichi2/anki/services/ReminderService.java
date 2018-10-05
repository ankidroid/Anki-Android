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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.IntentHandler;
import com.ichi2.anki.NotificationChannels;
import com.ichi2.anki.R;
import com.ichi2.libanki.Sched;

public class ReminderService extends BroadcastReceiver {

    public static final String EXTRA_DECK_ID = "EXTRA_DECK_ID";

    @Override
    public void onReceive(Context context, Intent intent) {

        final long deckId = intent.getLongExtra(EXTRA_DECK_ID, 0);

        if (CollectionHelper.getInstance().getCol(context).getDecks().get(deckId, false) == null) {
            final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                    context,
                    (int) deckId,
                    new Intent(context, ReminderService.class).putExtra(EXTRA_DECK_ID, deckId),
                    0
            );

            alarmManager.cancel(reminderIntent);
        }

        Sched.DeckDueTreeNode deckDue = null;

        for (Sched.DeckDueTreeNode node : CollectionHelper.getInstance().getCol(context).getSched().deckDueTree()) {
            if (node.did == deckId) {
                deckDue = node;
                break;
            }
        }

        if (null == deckDue) {
            return;
        }

        final int total = deckDue.revCount + deckDue.lrnCount + deckDue.newCount;

        if (total <= 0) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (notificationManager.areNotificationsEnabled()) {
            final Notification notification =
                    new NotificationCompat.Builder(context,
                            NotificationChannels.getId(NotificationChannels.Channel.DECK_REMINDERS))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentTitle(context.getString(R.string.reminder_title))
                    .setContentText(context.getResources().getQuantityString(
                            R.plurals.reminder_text,
                            total,
                            CollectionHelper.getInstance().getCol(context).getDecks().name(deckId),
                            total
                    ))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setColor(ContextCompat.getColor(context, R.color.material_light_blue_700))
                    .setContentIntent(PendingIntent.getActivity(
                            context,
                            (int) deckId,
                            new Intent(context, IntentHandler.class).putExtra(EXTRA_DECK_ID, deckId),
                            PendingIntent.FLAG_UPDATE_CURRENT
                    ))
                    .setAutoCancel(true)
                    .build();
            notificationManager.notify((int) deckId, notification);
        }
    }
}
