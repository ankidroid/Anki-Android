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
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.worker.NotificationWorker.Companion.getTriggerTime
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber
import java.util.*

/**
 * Worker class to collect the data for notification and triggers all the notifications.
 * It will calculate the next time of execution after completing the task of current execution i.e Execution time is dynamic.
 * It will run at most in one hour. Sooner if we'll have notification to send earlier @see [getTriggerTime].
 * If the device is switched off at the time of notification then Notification work manager will run whenever
 * devices gets turned on and it will fire all the previous pending notifications.
 * If there is no deck notification within 1 hour then also notification work manager will run after 1 hour to trigger all deck notification.
 * **NOTE: It is a coroutine worker i.e it will run asynchronously on the Dispatcher.DEFAULT**
 * */
class NotificationWorker(val context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    /**
     * Send all notifications and schedule next worker.
     * */
    override suspend fun doWork() = notifies().also {
        rescheduleNextWorker()
    }

    /**
     * Send single and all deck notifications.
     * */
    suspend fun notifies(): Result {
        Timber.d("NotificationManagerWorker: Worker status -> STARTED")

        // Collect the deck details
        val topLevelDecks = try {
            withCol {
                sched.deckDueTree().map { it.value }
            }
        } catch (ex: Exception) {
            // Unable to access collection
            return Result.failure()
        }

        processSingleDeckNotifications(topLevelDecks)
        fireAllDeckNotification(topLevelDecks)

        Timber.d("NotificationManagerWorker: Worker status -> FINISHED")
        return Result.success() // Done work successfully...
    }

    /**
     * Trigger all top level decks whose notification is due and has card.
     * Replaying those decks 24 hours later
     * Remove decks which are not top level anymore from notification setting.
     * */
    private suspend fun processSingleDeckNotifications(topLevelDecks: List<DeckDueTreeNode>) {
        Timber.d("Processing single deck notification...")
        val currentTime = TimeManager.time.currentDate.time

        // Collect all the notification data which need to triggered.
        val timeDeckData = NotificationDatastore.getInstance(context).getTimeDeckData()
        if (timeDeckData.isNullOrEmpty()) {
            Timber.d("No time deck data found, not firing any notifications")
            return
        }

        // Filtered all the decks whose notification time is less than current time.
        val timeDeckDataToTrigger = timeDeckData.filterTo(HashMap()) { it.key.toLong() <= currentTime }

        // Creating hash set of all decks whose notification is going to trigger
        val deckIdsToTrigger = timeDeckDataToTrigger
            .flatMap { it.value }
            .toHashSet()

        // Sorting deck notification data with help of deck id.
        val deckNotificationData = topLevelDecks.filter { deckIdsToTrigger.contains(it.did) }

        // Triggering the deck notification
        for (deck in deckNotificationData) {
            fireDeckNotification(deck)
            deckIdsToTrigger.remove(deck.did)
        }

        // Decks may have been deleted. This means that there are decks that should have been triggered but were not present.
        // The deck may not be here without having be deleted. The only thing we know is that it's not at top level
        if (deckIdsToTrigger.isNotEmpty()) {
            Timber.d(
                "Decks %s might be deleted but we didn't cancel deck notification for those decks. Canceling deck notification for these decks.".format(
                    deckIdsToTrigger.toString()
                )
            )
            // TODO: Cancel deck notification when user deletes a particular deck to handle case [user deletes a deck between the notification being added and executed]
        }

        // Updating time for next trigger.
        val calendar = TimeManager.time.calendar()
        timeDeckDataToTrigger.forEach {
            timeDeckData.remove(it.key)
            calendar.timeInMillis = it.key.toLong()
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            // TODO: We should ensure that we only plan the next notification for a time in the future.
            timeDeckData[calendar.timeInMillis.toString()] = it.value
        }

        // Saving the new Time Deck Data.
        NotificationDatastore.getInstance(context).setTimeDeckData(timeDeckData)
    }

    /**
     * Fire the notification for [deck] if needed.
     * We consider it is needed if [deck] or any of its subdecks have cards.
     * Even if subdecks have cards, we only trigger notification for [deck] itself, not for the subdecks
     */
    private fun fireDeckNotification(deck: DeckDueTreeNode) {
        Timber.d("Firing deck notification for did -> %d", deck.did)
        val title = context.getString(R.string.reminder_title)
        val counts =
            Counts(deck.newCount, deck.lrnCount, deck.revCount)
        val message = context.resources.getQuantityString(
            R.plurals.reminder_text,
            counts.count(),
            deck.fullDeckName
        )

        // TODO: Remove log used for now to remove compilation error.
        Timber.d("$title $counts $message")
        // TODO: Check the minimum no. of cards to send notification.
        // TODO: Build and fire notification.
    }

    /**
     * Fire a notification stating that there remains at least [minCardsDue] in the collection
     * */
    private fun fireAllDeckNotification(topLevelDecks: List<DeckDueTreeNode>) {
        Timber.d("Firing all deck notification.")
        val preferences = AnkiDroidApp.getSharedPrefs(context)
        val minCardsDue = preferences.getInt(
            Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION,
            Preferences.PENDING_NOTIFICATIONS_ONLY
        )

        // All Decks Notification.
        val totalDueCount = Counts()
        topLevelDecks.forEach {
            totalDueCount.addLrn(it.lrnCount)
            totalDueCount.addNew(it.newCount)
            totalDueCount.addRev(it.revCount)
        }

        if (totalDueCount.count() < minCardsDue) {
            // Due card limit is higher.
            return
        }
        // TODO: Build & Fire all deck notification.
    }

    /**
     * Reschedule Next Worker. It is calculated on the basis of [NotificationTodo] data by [getTriggerTime]
     * */
    private suspend fun rescheduleNextWorker() {
        Timber.d("Task Completed. Rescheduling...")
        val notificationDatastore = NotificationDatastore.getInstance(context)
        val timeAndDeckData = notificationDatastore.getTimeDeckData() ?: NotificationTodo()

        val nextTriggerTime = getTriggerTime(timeAndDeckData)
        val initialDiff = TimeManager.time.intTimeMS() - nextTriggerTime

        Timber.d("Next trigger time $nextTriggerTime in $initialDiff ms")
        // TODO: Start work manager with initial delay though Notification Helper.
    }

    companion object {
        const val ONE_HOUR_MS = 60 * 60 * 1000

        /**
         * Calculates the next time to trigger the Notification WorkManager.
         * it's not the next time a notification should be shown, but a time at most in one hour to check all deck notification.
         * @param allTimeDeckData Mapping from time to the list of decks whose notification should be sent at this time. [NotificationTodo]
         * @return next trigger time in milliseconds.
         * */
        fun getTriggerTime(allTimeDeckData: NotificationTodo): Long {
            val currentTime = TimeManager.time.currentDate.time

            val nextTimeKey = allTimeDeckData.keys.firstOrNull { it.toLong() >= currentTime }
                ?: return currentTime + ONE_HOUR_MS // No deck within 1 hour. Restarting after 1 hour for all deck notification

            val timeDiff = nextTimeKey.toLong() - currentTime

            return if (timeDiff < ONE_HOUR_MS) {
                nextTimeKey.toLong()
            } else {
                // No deck is scheduled in next hour. Restart service after 1 hour.
                currentTime + ONE_HOUR_MS
            }
        }
    }
}
