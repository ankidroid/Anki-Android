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

package com.ichi2.anki

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.ichi2.anki.worker.NotificationWorker
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MINUTE
import java.util.concurrent.TimeUnit
import kotlin.collections.HashSet

/**
 * Helper class for Notification. Manages all the notification for AnkiDroid.
 * */
class NotificationHelper(val context: Context) {

    private val TAG = "NOTIFICATION_WORKER"

    /**
     * Adds a daily recurring notification for the provided deck to the notification schedule.
     * @param did A deck's id.
     * @param hourOfDay Hour of day when notification trigger.
     * @param minutesOfHour Minutes of day when notification trigger.
     * */
    fun scheduledDeckNotification(
        did: DeckId,
        hourOfDay: Int,
        minutesOfHour: Int,
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            Timber.d("CreateScheduledDeckNotification-> did: $did hour: $hourOfDay min: $minutesOfHour")
            val notificationDatastore = NotificationDatastore.getInstance(context)

            // Collect all the time deck data.
            val timeDeckData: NotificationTodo =
                notificationDatastore.getTimeDeckData()
                    ?: NotificationTodo()

            // Calculate the Notification Work Type and schedule time of notification.
            val notificationData = notificationData(hourOfDay, minutesOfHour, timeDeckData)

            // Add schedule time in deck time data.
            timeDeckData.append(notificationData.scheduleTime, did)

            saveScheduledNotification(
                notificationDatastore,
                timeDeckData,
                notificationData.notificationWorkType,
            )
        }
    }

    /**
     * Set the notificationTimeOfToday for schedule time. It will return the time according to user time zone.
     * @param hourOfDay hour of notification time.
     * @param minutesOfHour minutes of notification time
     * */
    private fun hourMinuteTimestampToday(hourOfDay: Int, minutesOfHour: Int) =
        TimeManager.time.calendar().apply {
            this.set(HOUR_OF_DAY, hourOfDay)
            this.set(MINUTE, minutesOfHour)
        }.timeInMillis

    /**
     * Calculates the notification time in MS and [NotificationWorkType] that needs to be done to trigger notification.
     * Note: Time in the next 24 hours.
     * @param hourOfDay Hour of day when notification trigger.
     * @param minutesOfHour Minutes of day when notification trigger.
     * @param timeDeckData allDeckTimeData map to compute work type
     * @return [NotificationData] which contains schedule time and notification work type.
     * */
    private fun notificationData(
        hourOfDay: Int,
        minutesOfHour: Int,
        timeDeckData: NotificationTodo
    ) = NotificationData(timestampToSchedule(hourOfDay, minutesOfHour), notificationWorkType(hourOfDay, minutesOfHour, timeDeckData))

    /**
     * Calculates the notification work type.
     * Need to reschedule only if, as far as we currently know, this is the next notification to trigger in the future.
     * @param hourOfDay Hour of day when notification trigger.
     * @param minutesOfHour Minutes of day when notification trigger.
     * @param timeDeckData allDeckTimeData map to compute work type
     * @return [NotificationWorkType] notification work type.
     * */
    private fun notificationWorkType(
        hourOfDay: Int,
        minutesOfHour: Int,
        timeDeckData: NotificationTodo
    ): NotificationWorkType {
        val currentTimeMS = TimeManager.time.intTimeMS()
        val notificationTimeInMS = hourMinuteTimestampToday(hourOfDay, minutesOfHour)
        return if (notificationTimeInMS < currentTimeMS) {
            Timber.d("Scheduled time is already passed for today")
            NotificationWorkType.SAVE
        } else if (timeDeckData.size == 0) {
            Timber.d("Creating new time deck data ")
            NotificationWorkType.SAVE_AND_RESCHEDULE
        } else if (notificationTimeInMS < timeDeckData.earliestNotifications()!!.key.toLong()) {
            Timber.d("Scheduled time will come today only. And it will come before the current scheduled deck.")
            NotificationWorkType.SAVE_AND_RESCHEDULE
        } else {
            Timber.d("Scheduled time will come today only. And it will come after the current scheduled deck.")
            NotificationWorkType.SAVE
        }
    }

    /**
     * Calculates the notification time in MS and [NotificationWorkType] that needs to be done to trigger notification.
     * Note: Time in the next 24 hours.
     * @param hourOfDay Hour of day when notification trigger.
     * @param minutesOfHour Minutes of day when notification trigger.
     * @return Timestamp of the moment at which notification should be sent.
     * */
    private fun timestampToSchedule(
        hourOfDay: Int,
        minutesOfHour: Int,
    ): Long {
        val currentTimeMS = TimeManager.time.intTimeMS()
        val notificationTimeInMS = hourMinuteTimestampToday(hourOfDay, minutesOfHour)
        return if (notificationTimeInMS < currentTimeMS) {
            Timber.d("Scheduled time is already passed for today")
            // Notification time is passed for today.
            // Save the notification for tomorrow. Just save it, no need to reschedule.
            val calendar = TimeManager.time.calendar().apply {
                this.set(HOUR_OF_DAY, hourOfDay)
                this.set(MINUTE, minutesOfHour)
                add(Calendar.DAY_OF_YEAR, 1)
            }
            calendar.timeInMillis
        } else {
            Timber.d("Scheduled time is not yet passed.")
            notificationTimeInMS
        }
    }

    /**
     * Sets the schedule notification according to [NotificationWorkType].
     * [NotificationWorkType.SAVE] can be used when we want new notification data to save
     * but [NotificationWorkType.SAVE_AND_RESCHEDULE] can be used when we want to save and reschedule next worker
     * */
    private suspend inline fun saveScheduledNotification(
        notificationDatastore: NotificationDatastore,
        allTimeAndDecksMap: NotificationTodo,
        notificationWorkType: NotificationWorkType,
    ) {
        // Save the data in data store
        notificationDatastore.setTimeDeckData(allTimeAndDecksMap)

        // Rescheduling work manager.
        if (notificationWorkType == NotificationWorkType.SAVE_AND_RESCHEDULE) {
            // Replacing old work manager with new one. (initialDiff = currentTime - nextTriggerTime)
            val nextTriggerTime = NotificationWorker.getTriggerTime(allTimeAndDecksMap)
            val initialDiff = nextTriggerTime - TimeManager.time.intTimeMS()
            Timber.d("Next trigger time $nextTriggerTime")
            startNotificationWorker(initialDiff, true)
        }
    }

    /**
     * Adds the did and schedule time in [NotificationTodo]
     * */
    private fun NotificationTodo.append(
        scheduleTimeMS: Long,
        did: DeckId
    ) = getOrPut(scheduleTimeMS.toString()) { HashSet() }.add(did)

    /**
     * Recreates the deck time map.
     * Generally used when user time zone changes.
     * While scheduling the notification we saves the data in datastore So, Data in datastore will be always fresh.
     * */
    fun calibrateNotificationTime() {
        CoroutineScope(Dispatchers.Default).launch {
            Timber.d("Calibrating the time deck data...")
            val notificationDatastore = NotificationDatastore.getInstance(context)

            // Notification. Their timestamp may be wrong
            val oldTimeDeckData: NotificationTodo =
                notificationDatastore.getTimeDeckData()
                    ?: NotificationTodo()

            // Notifications with timestamp adapted to the current timezone.
            val newTimeDeckData = NotificationTodo()

            // Filtering all deck ids from the map
            val listOfDeck = oldTimeDeckData.values.flatten()
            Timber.d("List of all deck %s".format(listOfDeck.toString()))

            // Compute new sched timing for all deck ids.
            for (deck in listOfDeck) {
                val deckData = notificationDatastore.getDeckSchedData(deck)
                    ?: continue
                val notificationData = notificationData(
                    deckData.schedHour,
                    deckData.schedMinutes,
                    newTimeDeckData
                )
                newTimeDeckData.append(notificationData.scheduleTime, deckData.did)
            }

            // Save and reschedule work manager.
            saveScheduledNotification(
                notificationDatastore,
                newTimeDeckData,
                NotificationWorkType.SAVE_AND_RESCHEDULE
            )
        }
    }

    /**
     * Start the new notification worker i.e new Instance of [NotificationWorker]
     * @param initialDelay delay after work manager should start.
     * @param force if true then it will destroy the existing work manager and recreates the new one.
     * true: @see [ExistingWorkPolicy.REPLACE], false: @see [ExistingWorkPolicy.KEEP]
     * */
    fun startNotificationWorker(initialDelay: Long, force: Boolean) {
        Timber.d("Starting work manager with initial delay $initialDelay force: $force")

        // Create a One Time Work Request with initial delay.
        val deckMetaDataWorker = OneTimeWorkRequest.Builder(
            NotificationWorker::class.java,
        )
            .addTag(TAG)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        val workPolicy = if (force) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        // Enqueue the periodic work manager.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                TAG,
                workPolicy,
                deckMetaDataWorker
            )
    }

    /**
     * Cancels the previously scheduled notification Worker.
     * */
    fun cancelScheduledDeckNotification() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    /**
     * Cancel/Removes the deck id from the [NotificationTodo]. To avoid notification triggering.
     * NOTE: It is a O(|deckIds| * numberOfDeckInNotification) and It is modifying NotificationDatastore, so no other change should be done at the same time.
     * */
    fun removeDeckNotification(vararg deckIds: DeckId) {
        CoroutineScope(Dispatchers.Default).launch {
            val notificationDatastore = NotificationDatastore.getInstance(context)
            val allTimeAndDecksMap = notificationDatastore.getTimeDeckData() ?: return@launch
            val notificationDataToDelete = mutableListOf<TimeOfNotification>()
            deckIds.forEach { did ->
                for (timeDeckData in allTimeAndDecksMap) {
                    if (!timeDeckData.value.contains(did)) {
                        // list of deck doesn't contains did.
                        break
                    }
                    // Deck id found delete the deck.
                    if (timeDeckData.value.size == 1) {
                        Timber.d("List of deck contains only one deck id.")
                        notificationDataToDelete.add(timeDeckData.key)
                    } else {
                        Timber.d("List of deck contains more than one deck ids.")
                        timeDeckData.value.remove(did)
                    }
                }
            }
            notificationDataToDelete.forEach {
                allTimeAndDecksMap.remove(it)
            }
            notificationDatastore.setTimeDeckData(allTimeAndDecksMap)
        }
    }

    /**
     * Triggers the notification immediately.
     * It will check internally whether notification is enabled or not.
     * @param id Notification id
     * @param notification Notification which should be displayed.
     *        Build Notification using [NotificationHelper.buildNotification]
     * */
    fun triggerNotificationNow(id: Int, notification: Notification) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        // Check notification is enabled or not.
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            Timber.v("Notifications disabled")
            return
        }

        notificationManager.notify(id, notification)
    }

    /**
     * Builds the notification that is going to trigger.
     * @param notificationChannel Channel on which notification should trigger.
     * @param title Title of notification.
     * @param body Text message for Body of Notification.
     * @param pendingIntent Activity which need to open on notification tap.
     * */
    fun buildNotification(
        notificationChannel: NotificationChannels.Channel,
        title: String,
        body: String,
        pendingIntent: PendingIntent
    ) = NotificationCompat.Builder(
        context,
        NotificationChannels.getId(notificationChannel)
    ).apply {
        setCategory(NotificationCompat.CATEGORY_REMINDER)
        setContentTitle(title)
        setContentText(body)
        setSmallIcon(R.drawable.ic_stat_notify)
        color = ContextCompat.getColor(context, R.color.material_light_blue_700)
        setContentIntent(pendingIntent)
        setAutoCancel(true)
    }.build()

    /**
     * Work that needs to be done so that notification can trigger.
     * SAVE: It only saves the data in notification datastore.
     * SAVE_AND_RESCHEDULE: Saves the data and reschedule the notification work manager.
     * @see [saveScheduledNotification] for enum implementation.
     * */
    private enum class NotificationWorkType {
        SAVE,
        SAVE_AND_RESCHEDULE,
    }

    /**
     * Data of notification that need to be scheduled.
     * */
    private data class NotificationData(
        val scheduleTime: Long,
        val notificationWorkType: NotificationWorkType
    )
}
