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
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.IntentHandler;
import com.ichi2.anki.NotificationChannels;
import com.ichi2.anki.R;
import com.ichi2.anki.receiver.ReminderReceiver;
import com.ichi2.libanki.Sched;

/**
 * This service handles notifications for each deck when work is due
 */
public class ReminderService extends IntentService {
    public static final String EXTRA_DECK_ID = "EXTRA_DECK_ID";

    public ReminderService() {
        super("ReminderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final long deckId = intent.getLongExtra(ReminderService.EXTRA_DECK_ID, 0);

        if (null == CollectionHelper.getInstance().getCol(this).getDecks().get(deckId, false)) {
            final AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

            final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                    this,
                    (int) deckId,
                    new Intent(this, ReminderReceiver.class).putExtra(ReminderService.EXTRA_DECK_ID, deckId),
                    0
            );

            alarmManager.cancel(reminderIntent);
        }

        Sched.DeckDueTreeNode deckDue = null;

        for (Sched.DeckDueTreeNode node : CollectionHelper.getInstance().getCol(this).getSched().deckDueTree()) {
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

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        if (notificationManager.areNotificationsEnabled()) {
            final Notification notification =
                    new NotificationCompat.Builder(this,
                            NotificationChannels.getId(NotificationChannels.Channel.DECK_REMINDERS))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentTitle(this.getString(R.string.reminder_title))
                    .setContentText(this.getResources().getQuantityString(
                            R.plurals.reminder_text,
                            total,
                            CollectionHelper.getInstance().getCol(this).getDecks().name(deckId),
                            total
                    ))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setColor(ContextCompat.getColor(this, R.color.material_light_blue_700))
                    .setContentIntent(PendingIntent.getActivity(
                            this,
                            (int) deckId,
                            new Intent(this, IntentHandler.class).putExtra(EXTRA_DECK_ID, deckId),
                            PendingIntent.FLAG_UPDATE_CURRENT
                    ))
                    .setAutoCancel(true)
                    .build();

            notificationManager.notify((int) deckId, notification);
        }
    }
}
