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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.IntentHandler;
import com.ichi2.anki.NotificationChannels;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.sched.Sched;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ReminderService extends BroadcastReceiver {

    public static final String EXTRA_DECK_OPTION_ID = "EXTRA_DECK_OPTION_ID";
    public static final String EXTRA_DECK_ID = "EXTRA_DECK_ID";

    @Override
    public void onReceive(Context context, Intent intent) {

        final long dConfId = intent.getLongExtra(EXTRA_DECK_OPTION_ID, 0);

        if (CollectionHelper.getInstance().getCol(context).getDecks().getConf(dConfId) == null) {
            final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                    context,
                    (int) dConfId,
                    new Intent(context, ReminderService.class).putExtra(EXTRA_DECK_OPTION_ID, dConfId),
                    0
            );

            alarmManager.cancel(reminderIntent);
        }

        List<Sched.DeckDueTreeNode> decksDue = getDeckOptionDue(context, dConfId, true);

        if (null == decksDue) {
            return;
        }

        for (Sched.DeckDueTreeNode deckDue: decksDue) {
            long deckId = deckDue.getDid();
            final int total = deckDue.getRevCount() + deckDue.getLrnCount() + deckDue.getNewCount();

            if (total <= 0) {
                continue;
            }

            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

            Timber.v("Notify: study deck %s", deckDue.getFullDeckName());
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
                                        getReviewDeckIntent(context, deckId),
                                        PendingIntent.FLAG_UPDATE_CURRENT
                                ))
                                .setAutoCancel(true)
                                .build();
                notificationManager.notify((int) deckId, notification);
            }
        }
    }


    @NonNull
    public static Intent getReviewDeckIntent(@NonNull Context context, long deckId) {
        return new Intent(context, IntentHandler.class).putExtra(EXTRA_DECK_ID, deckId);
    }


    // getDeckOptionDue information, will recur one time to workaround collection close if recur is true
    @Nullable
    private List<Sched.DeckDueTreeNode> getDeckOptionDue(Context context, long dConfId, boolean recur) {

        Collection col = CollectionHelper.getInstance().getCol(context);
        // Avoid crashes if the deck option group is deleted while we
        // are working
        if (col.getDecks().getConf(dConfId) == null) {
            Timber.d("Deck option %s deleted while ReminderService was working. Ignoring", dConfId);
            return null;
        }

        List<Sched.DeckDueTreeNode> decks = new ArrayList<>();
        try {
            // This loop over top level deck only. No notification will ever occur for subdecks.
            for (Sched.DeckDueTreeNode node : CollectionHelper.getInstance().getCol(context).getSched().deckDueTree()) {
                JSONObject deck = col.getDecks().get(node.getDid(), false);
                // Dynamic deck has no "conf", so are not added here.
                if (deck != null && deck.optLong("conf") == dConfId) {
                    decks.add(node);
                }
            }
            return decks;
        } catch (Exception e) {
            if (recur) {
                Timber.i(e, "getDeckOptionDue exception - likely database re-initialization from auto-sync. Will re-try after sleep.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Timber.i(ex, "Thread interrupted while waiting to retry. Likely unimportant.");
                    Thread.currentThread().interrupt();
                }
                return getDeckOptionDue(context, dConfId, false);
            } else {
                Timber.w(e, "Database unavailable while working. No re-tries left.");
            }
        }

        return null;
    }
}
