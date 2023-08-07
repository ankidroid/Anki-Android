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

package com.ichi2.anki.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import com.ichi2.anki.Channel
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.sched.DeckNode
import org.json.JSONObject
import timber.log.Timber

class ReminderService : BroadcastReceiver() {
    /** Cancelling all deck reminder. We used to use them, now we have deck option reminders.  */
    private fun cancelDeckReminder(context: Context, intent: Intent) {
        // 0 Is not a valid deck id.
        val deckId = intent.getLongExtra(EXTRA_DECK_ID, 0)
        if (deckId == 0L) {
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderIntent = PendingIntentCompat.getBroadcast(
            context,
            deckId.toInt(),
            Intent(context, ReminderService::class.java).putExtra(EXTRA_DECK_OPTION_ID, deckId),
            0,
            false
        )
        alarmManager.cancel(reminderIntent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        cancelDeckReminder(context, intent)

        // 0 is not a valid dconf id.
        val dConfId = intent.getLongExtra(EXTRA_DECK_OPTION_ID, 0)
        if (dConfId == 0L) {
            Timber.w("onReceive - dConfId 0, returning")
            return
        }
        val colHelper: CollectionHelper
        val col: Collection?
        try {
            colHelper = CollectionHelper.instance
            col = colHelper.getCol(context)
        } catch (t: Throwable) {
            Timber.w(t, "onReceive - unexpectedly unable to get collection. Returning.")
            return
        }
        if (null == col || !colHelper.colIsOpen()) {
            Timber.w("onReceive - null or closed collection, unable to process reminders")
            return
        }
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            Timber.v("onReceive - notifications disabled, returning")
            return
        }
        val decksDue = getDeckOptionDue(col, dConfId, true)
        if (null == decksDue) {
            Timber.v("onReceive - no decks due, returning")
            return
        }
        for (deckDue in decksDue) {
            val deckId = deckDue.did
            val total = deckDue.revCount + deckDue.lrnCount + deckDue.newCount
            if (total <= 0) {
                Timber.v("onReceive - no cards due in deck %d", deckId)
                continue
            }
            Timber.v("onReceive - deck '%s' due count %d", deckDue.fullDeckName, total)
            val notification = NotificationCompat.Builder(
                context,
                Channel.DECK_REMINDERS.id
            )
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(
                    context.resources.getQuantityString(
                        R.plurals.reminder_text,
                        total,
                        deckDue.fullDeckName,
                        total
                    )
                )
                .setSmallIcon(R.drawable.ic_star_notify)
                .setColor(context.getColor(R.color.material_light_blue_700))
                .setContentIntent(
                    PendingIntentCompat.getActivity(
                        context,
                        deckId.toInt(),
                        getReviewDeckIntent(context, deckId),
                        PendingIntent.FLAG_UPDATE_CURRENT,
                        false
                    )
                )
                .setAutoCancel(true)
                .build()
            notificationManager.notify(deckId.toInt(), notification)
            Timber.v("onReceive - notification state: %s", notification)
        }
    }

    // getDeckOptionDue information, will recur one time to workaround collection close if recur is true
    private fun getDeckOptionDue(col: Collection, dConfId: Long, recur: Boolean): List<DeckNode>? {
        // Avoid crashes if the deck option group is deleted while we
        // are working
        if (col.dbClosed) {
            Timber.d("Deck option %s became unavailable while ReminderService was working. Ignoring", dConfId)
            return null
        }
        try {
            val dues = col.sched.deckDueTree().map { it.value }
            val decks: MutableList<DeckNode> = ArrayList(dues.size)
            // This loop over top level deck only. No notification will ever occur for subdecks.
            for (node in dues) {
                val deck: JSONObject? = col.decks.get(node.did, false)
                // Dynamic deck has no "conf", so are not added here.
                if (deck != null && deck.optLong("conf") == dConfId) {
                    decks.add(node)
                }
            }
            return decks
        } catch (e: Exception) {
            if (recur) {
                Timber.i(e, "getDeckOptionDue exception - likely database re-initialization from auto-sync. Will re-try after sleep.")
                try {
                    Thread.sleep(1000)
                } catch (ex: InterruptedException) {
                    Timber.i(ex, "Thread interrupted while waiting to retry. Likely unimportant.")
                    Thread.currentThread().interrupt()
                }
                return getDeckOptionDue(col, dConfId, false)
            } else {
                Timber.w(e, "Database unavailable while working. No re-tries left.")
            }
        }
        return null
    }

    companion object {
        const val EXTRA_DECK_OPTION_ID = "EXTRA_DECK_OPTION_ID"
        const val EXTRA_DECK_ID = "EXTRA_DECK_ID"

        fun getReviewDeckIntent(context: Context, deckId: DeckId): Intent {
            return Intent(context, IntentHandler::class.java).putExtra(EXTRA_DECK_ID, deckId)
        }
    }
}
