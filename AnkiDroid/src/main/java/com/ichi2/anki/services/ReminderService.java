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
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.DeckDueTreeNode;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ReminderService extends BroadcastReceiver {

    public static final String EXTRA_DECK_OPTION_ID = "EXTRA_DECK_OPTION_ID";
    public static final String EXTRA_DECK_ID = "EXTRA_DECK_ID";


    /** Cancelling all deck reminder. We used to use them, now we have deck option reminders. */
    private void cancelDeckReminder(Context context, Intent intent) {
        // 0 Is not a valid deck id.
        final long deckId = intent.getLongExtra(EXTRA_DECK_ID, 0);
        if (deckId == 0) {
            return;
        }
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        final PendingIntent reminderIntent = CompatHelper.getCompat().getImmutableBroadcastIntent(
            context,
            (int) deckId,
            new Intent(context, ReminderService.class).putExtra(EXTRA_DECK_OPTION_ID, deckId),
            0);

        alarmManager.cancel(reminderIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        cancelDeckReminder(context, intent);

        // 0 is not a valid dconf id.
        final long dConfId = intent.getLongExtra(EXTRA_DECK_OPTION_ID, 0);
        if (dConfId == 0) {
            Timber.w("onReceive - dConfId 0, returning");
            return;
        }

        CollectionHelper colHelper;
        Collection col;
        try {
            colHelper = CollectionHelper.getInstance();
            col = colHelper.getCol(context);
        } catch (Throwable t) {
            Timber.w(t,"onReceive - unexpectedly unable to get collection. Returning.");
            return;
        }

        if (null == col || !colHelper.colIsOpen()) {
            Timber.w("onReceive - null or closed collection, unable to process reminders");
            return;
        }

        if (col.getDecks().getConf(dConfId) == null) {
            final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            final PendingIntent reminderIntent = CompatHelper.getCompat().getImmutableBroadcastIntent(
                    context,
                    (int) dConfId,
                    new Intent(context, ReminderService.class).putExtra(EXTRA_DECK_OPTION_ID, dConfId),
                    0
            );

            alarmManager.cancel(reminderIntent);
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (!notificationManager.areNotificationsEnabled()) {
            Timber.v("onReceive - notifications disabled, returning");
            return;
        }
        List<DeckDueTreeNode> decksDue = getDeckOptionDue(col, dConfId, true);

        if (null == decksDue) {
            Timber.v("onReceive - no decks due, returning");
            return;
        }

        for (DeckDueTreeNode deckDue: decksDue) {
            long deckId = deckDue.getDid();
            final int total = deckDue.getRevCount() + deckDue.getLrnCount() + deckDue.getNewCount();

            if (total <= 0) {
                Timber.v("onReceive - no cards due in deck %d", deckId);
                continue;
            }


            Timber.v("onReceive - deck '%s' due count %d", deckDue.getFullDeckName(), total);
            final Notification notification =
                new NotificationCompat.Builder(context,
                    NotificationChannels.getId(NotificationChannels.Channel.DECK_REMINDERS))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setContentTitle(context.getString(R.string.reminder_title))
                        .setContentText(context.getResources().getQuantityString(
                                R.plurals.reminder_text,
                                total,
                                deckDue.getFullDeckName(),
                                total
                        ))
                        .setSmallIcon(R.drawable.ic_stat_notify)
                        .setColor(ContextCompat.getColor(context, R.color.material_light_blue_700))
                        .setContentIntent(CompatHelper.getCompat().getImmutableActivityIntent(
                                context,
                                (int) deckId,
                                getReviewDeckIntent(context, deckId),
                                PendingIntent.FLAG_UPDATE_CURRENT
                        ))
                        .setAutoCancel(true)
                        .build();
                notificationManager.notify((int) deckId, notification);
            Timber.v("onReceive - notification state: %s", notification);
        }
    }


    @NonNull
    public static Intent getReviewDeckIntent(@NonNull Context context, long deckId) {
        return new Intent(context, IntentHandler.class).putExtra(EXTRA_DECK_ID, deckId);
    }


    // getDeckOptionDue information, will recur one time to workaround collection close if recur is true
    @Nullable
    private List<DeckDueTreeNode> getDeckOptionDue(Collection col, long dConfId, boolean recur) {

        // Avoid crashes if the deck option group is deleted while we
        // are working
        if (col.getDb() == null || col.getDecks().getConf(dConfId) == null) {
            Timber.d("Deck option %s became unavailable while ReminderService was working. Ignoring", dConfId);
            return null;
        }

        try {
            List<DeckDueTreeNode> dues = col.getSched().deckDueTree();
            List<DeckDueTreeNode> decks = new ArrayList<>(dues.size());
            // This loop over top level deck only. No notification will ever occur for subdecks.
            for (DeckDueTreeNode node : dues) {
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
                return getDeckOptionDue(col, dConfId, false);
            } else {
                Timber.w(e, "Database unavailable while working. No re-tries left.");
            }
        }

        return null;
    }
}
