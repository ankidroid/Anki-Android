/*
 * Copyright (c) 2022 Prateek Singh <prateeksingh3212@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.sched.TreeNode
import timber.log.Timber

/**
 * Worker class to collect the data for notification and triggers all the notifications.
 * If the device is switched off at the time of notification then Notification work manager will run whenever
 * devices gets turned on and it will fire all the previous pending notifications.
 * **NOTE: It is a coroutine worker i.e it will run asynchronously on the Dispatcher.DEFAULT**
 * */
class NotificationWorker(val context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    /**
     * Send all notifications and schedule next worker.
     * */
    override suspend fun doWork() = notifiesUser().also {
        Timber.d("Rescheduling notification worker...")
        // TODO: Reschedule next notification with help of notification worker.
    }

    /**
     * Send all deck notifications.
     * */
    suspend fun notifiesUser(): Result {
        Timber.d("NotificationManagerWorker: Worker status -> STARTED")

        // Collect the deck details
        val topLevelDecks = try {
            withCol {
                sched.deckDueTree()
            }
        } catch (ex: Exception) {
            // Unable to access collection. It will rerun when the app restarts again.
            return Result.failure()
        }

        fireGlobalNotification(topLevelDecks)

        Timber.d("NotificationManagerWorker: Worker status -> FINISHED")
        return Result.success() // Done work successfully...
    }

    /**
     * Fire global notification. It requires only top level decks.
     * */
    private fun fireGlobalNotification(topLevelDecks: List<TreeNode<DeckDueTreeNode>>) {
        Timber.d("Firing Global notification.")

        // Calculating due count from top level decks also covers the due cards of subdecks and their limits.
        val totalDueCount = Counts().apply {
            topLevelDecks.forEach {
                addLrn(it.value.lrnCount)
                addNew(it.value.newCount)
                addRev(it.value.revCount)
            }
        }

        if (totalDueCount.count() < 1) {
            // No due card found.
            return
        }

        // TODO: Build & Fire global notification.
    }
}
