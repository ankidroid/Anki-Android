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
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber
import java.time.Instant
import java.util.*
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MINUTE
import java.util.concurrent.TimeUnit

/**
 * Helper class for Notification. Manages all the reminder notification for AnkiDroid.
 * */
class ReminderNotificationHelper(val context: Context) {

    /**
     * Schedules the notification worker according to given time.
     * @param nextDay Schedule the global notification for next day.
     * */
    fun scheduleGlobalNotificationWorker(nextDay: Boolean = false) {
        val sharedPreferences = AnkiDroidApp.getSharedPrefs(context)
        val hourAndMinutes = sharedPreferences.getString(GLOBAL_NOTIFICATION_TIME, null)
            ?.split(":") ?: return

        val currTime = TimeManager.time.intTimeMS()
        val notificationTimestampForToday = timestampOfTodayAtGivenHourAndMinute(
            hourAndMinutes[0].toInt(),
            hourAndMinutes[1].toInt()
        )
        val timestampOfNextTrigger = if (notificationTimestampForToday < currTime || nextDay) {
            Timber.d("Scheduling notification for next day.")
            TimeManager.time.calendar().run {
                set(HOUR_OF_DAY, hourAndMinutes[0].toInt())
                set(MINUTE, hourAndMinutes[1].toInt())
                add(Calendar.DAY_OF_YEAR, 1)
                timeInMillis
            }
        } else {
            notificationTimestampForToday
        }

        val initialDelay = timestampOfNextTrigger - TimeManager.time.intTimeMS()
        Timber.d(
            "Next trigger time %s, Initial delay %d",
            Instant.ofEpochMilli(timestampOfNextTrigger).toString(),
            initialDelay
        )
        startNotificationWorker(initialDelay)
    }

    /**
     * Set the notificationTimeOfToday for schedule time. It will return the time according to user time zone.
     * @param hourOfDay hour of notification time.
     * @param minutesOfHour minutes of notification time
     * */
    fun timestampOfTodayAtGivenHourAndMinute(hourOfDay: Int, minutesOfHour: Int) =
        TimeManager.time.calendar().apply {
            this.set(HOUR_OF_DAY, hourOfDay)
            this.set(MINUTE, minutesOfHour)
        }.timeInMillis

    /**
     * Generally used when user time zone changes.
     * While scheduling the notification we save the data in SharedPreference.
     * So, The data in the shared preference will be always fresh.
     * */
    fun calibrateNotificationTime() {
        Timber.d("Calibrating the time deck data...")
        val sharedPreferences = AnkiDroidApp.getSharedPrefs(context)

        val globalNotificationEnabled = sharedPreferences.getBoolean(GLOBAL_NOTIFICATION_ENABLED, false)

        if (globalNotificationEnabled) {
            scheduleGlobalNotificationWorker()
        } else {
            // No Global notification scheduled.
            cancelNotificationWorker()
        }
    }

    /**
     * Start the new notification worker i.e new Instance of [NotificationWorker]
     * @param initialDelay delay after work manager should start.
     * */
    fun startNotificationWorker(initialDelay: Long) {
        Timber.d("Starting work manager with initial delay $initialDelay")

        // Create a One Time Work Request with initial delay.
        val deckMetaDataWorker = OneTimeWorkRequest.Builder(
            NotificationWorker::class.java,
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        // Enqueue the periodic work manager.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                TAG,
                ExistingWorkPolicy.REPLACE,
                deckMetaDataWorker
            )
    }

    /**
     * Cancels the previously scheduled notification Worker.
     * */
    fun cancelNotificationWorker() {
        WorkManager.getInstance(context).cancelUniqueWork(TAG)
    }

    /**
     * It will check whether notification is enabled or not.
     * */
    fun isNotificationEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Triggers the notification immediately if notification is enabled.
     * @param id Notification id
     * @param notification Notification which should be displayed.
     *        Build Notification using [NotificationHelper.buildNotification]
     * */
    fun triggerNotificationNow(id: Int, notification: Notification) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(id, notification)
    }

    /**
     * A notification, according to the given parameters
     * @param notificationChannel Channel on which notification should trigger.
     * @param title Title of notification.
     * @param body Text message for Body of Notification.
     * @param pendingIntent Activity which need to open on notification tap.
     * */
    fun buildNotification(
        notificationChannel: Channel,
        title: String,
        body: String?,
        pendingIntent: PendingIntent
    ) = NotificationCompat.Builder(
        context,
        notificationChannel.id
    ).apply {
        setCategory(NotificationCompat.CATEGORY_REMINDER)
        setContentTitle(title)
        setContentText(body)
        setSmallIcon(R.drawable.ic_stat_notify)
        color = ContextCompat.getColor(context, R.color.material_light_blue_700)
        setContentIntent(pendingIntent)
        setAutoCancel(true)
    }.build()

    companion object {
        private const val TAG = "NOTIFICATION_WORKER"
        const val GLOBAL_NOTIFICATION_TIME = "remindAt"
        const val GLOBAL_NOTIFICATION_ENABLED = "globalNotification"
        const val GLOBAL_NOTIFICATION_DEFAULT_TIME = "19:00"
    }
}
