/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.showThemedToast
import timber.log.Timber
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Schedules review reminder notifications.
 * See [ReviewReminder] for the distinction between a "review reminder" and a "notification".
 * Actual notification firing is handled by [NotificationService], which this service triggers
 * by dispatching [NotificationService.NotificationServiceAction.ScheduleRecurringNotifications] requests.
 *
 * This service also handles scheduling snoozed instances of review reminders.
 * Notifications have snooze buttons (defined in [NotificationService]) which, when clicked,
 * trigger the [onReceive] method of this BroadcastReceiver. This service handles the snooze delay,
 * after which it dispatches a one-time [NotificationService.NotificationServiceAction.SnoozeNotification]
 * request to [NotificationService].
 */
class AlarmManagerService : BroadcastReceiver() {
    companion object {
        /**
         * Extra key for sending a review reminder as an extra to this BroadcastReceiver.
         */
        private const val EXTRA_REVIEW_REMINDER = "alarm_manager_service_review_reminder"

        /**
         * Extra key for sending a snooze delay interval as an extra to this BroadcastReceiver.
         * The stored value is an integer number of minutes.
         */
        private const val EXTRA_SNOOZE_INTERVAL = "alarm_manager_service_snooze_interval"

        /**
         * Interval passed to [AlarmManager.setWindow], in milliseconds. The OS is allowed to delay AnkiDroid's notifications
         * by at much this amount of time. We set it to 10 minutes, which is the minimum allowable duration
         * according to [the docs](https://developer.android.com/reference/android/app/AlarmManager).
         */
        private val WINDOW_LENGTH_MS: Long = 10.minutes.inWholeMilliseconds

        /**
         * Shows error messages if an error occurs when scheduling review reminders via AlarmManager.
         * This function wraps all calls to AlarmManager in this class.
         */
        private fun catchAlarmManagerExceptions(
            context: Context,
            block: (AlarmManager) -> Unit,
        ) {
            var error: Int? = null
            try {
                val alarmManager = context.getSystemService<AlarmManager>()
                if (alarmManager != null) {
                    block(alarmManager)
                } else {
                    Timber.w("Failed to get AlarmManager system service, aborting operation")
                }
            } catch (ex: SecurityException) {
                // #6332 - Too Many Alarms on Samsung Devices - this stops a fatal startup crash.
                // We warn the user if they breach this limit
                Timber.w(ex)
                error = R.string.boot_service_too_many_notifications
            } catch (e: Exception) {
                Timber.w(e)
                error = R.string.boot_service_failed_to_schedule_notifications
            }
            if (error != null) {
                try {
                    showThemedToast(context, context.getString(error), false)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to show AlarmManager exception toast for error: $error")
                }
            }
        }

        /**
         * Gets the pending intent of a review reminder's scheduled notifications, either the normal recurring ones
         * (if the action is set to [NotificationService.NotificationServiceAction.ScheduleRecurringNotifications])
         * or the one-time snoozed ones (if the action is set to [NotificationService.NotificationServiceAction.SnoozeNotification]).
         * This pending intent can then be used to either schedule those notifications or cancel them.
         *
         * If a review reminder with an identical ID has already had notifications scheduled via the pending intent
         * returned by this method, new notifications scheduled using this pending intent will update the existing
         * notifications rather than create duplicate new ones.
         *
         * @see NotificationService.NotificationServiceAction
         */
        private fun getReviewReminderNotificationPendingIntent(
            context: Context,
            reviewReminder: ReviewReminder,
            intentAction: NotificationService.NotificationServiceAction,
        ): PendingIntent? {
            val intent = NotificationService.getIntent(context, reviewReminder, intentAction)
            return PendingIntentCompat.getBroadcast(
                context,
                reviewReminder.id.value,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
        }

        /**
         * Queues a review reminder to have its notification fired at its specified time. Does not check
         * if the review reminder is enabled or not, the caller must handle this.
         *
         * Note that this only schedules the next upcoming notification, using [AlarmManager.setWindow]
         * rather than [AlarmManager.setRepeating]. This is because [AlarmManager.setRepeating] sometimes
         * postpones alarm firings for long periods of time, with intervals as long as one hour observed
         * in testing. In contrast, [AlarmManager.setWindow] permits us to specify a maximum allowable
         * length of time the OS can delay the alarm for, leading to a better UX. Each time an alarm is fired,
         * triggering [NotificationService.sendReviewReminderNotification], this method is called again to
         * schedule the next upcoming notification. If for some reason the next day's alarm fails to be set by
         * the current day's notification, we fall back to setting alarms whenever the app is opened: see
         * [com.ichi2.anki.AnkiDroidApp]'s call to [scheduleAllEnabledReviewReminderNotifications].
         *
         * If an old version of this review reminder with the same review reminder ID has already had
         * its notifications scheduled, this will merely update the existing notifications. If, however,
         * an old version of this review reminder with a different review reminder ID has already had its
         * notifications scheduled, this will NOT delete the old scheduled notifications. They must be
         * manually deleted via [unscheduleReviewReminderNotifications].
         *
         * @see NotificationService.NotificationServiceAction.ScheduleRecurringNotifications
         */
        fun scheduleReviewReminderNotification(
            context: Context,
            reviewReminder: ReviewReminder,
        ) {
            Timber.d("Beginning scheduleReviewReminderNotifications for ${reviewReminder.id}")
            Timber.v("Review reminder: $reviewReminder")
            val pendingIntent =
                getReviewReminderNotificationPendingIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                ) ?: return
            Timber.v("Pending intent for ${reviewReminder.id} is $pendingIntent")

            val currentTimestamp = TimeManager.time.calendar()
            val alarmTimestamp = currentTimestamp.clone() as Calendar
            alarmTimestamp.apply {
                set(Calendar.HOUR_OF_DAY, reviewReminder.time.hour)
                set(Calendar.MINUTE, reviewReminder.time.minute)
                set(Calendar.SECOND, 0)
                if (before(currentTimestamp)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            catchAlarmManagerExceptions(context) { alarmManager ->
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimestamp.timeInMillis,
                    WINDOW_LENGTH_MS,
                    pendingIntent,
                )
                Timber.d("Successfully scheduled review reminder notifications for ${reviewReminder.id}")
            }
        }

        /**
         * Deletes any scheduled notifications for this review reminder. Does not actually delete the
         * review reminder itself from anywhere, only deletes any queued alarms for the review reminder.
         *
         * @see NotificationService.NotificationServiceAction.ScheduleRecurringNotifications
         */
        fun unscheduleReviewReminderNotifications(
            context: Context,
            reviewReminder: ReviewReminder,
        ) {
            Timber.d("Beginning unscheduleReviewReminderNotifications for ${reviewReminder.id}")
            Timber.v("Review reminder: $reviewReminder")
            val pendingIntent =
                getReviewReminderNotificationPendingIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                ) ?: return
            Timber.v("Pending intent for ${reviewReminder.id} is $pendingIntent")
            catchAlarmManagerExceptions(context) { alarmManager ->
                alarmManager.cancel(pendingIntent)
                Timber.d("Successfully unscheduled review reminder notifications for ${reviewReminder.id}")
            }
        }

        /**
         * Schedules notifications for all currently-enabled review reminders. Reads from the [ReviewRemindersDatabase].
         *
         * If, for a review reminder in the database, an old version of a review reminder with the same review
         * reminder ID has already had its notifications scheduled, this will merely update the existing notifications.
         * If, however, an old version of a review reminder with a different review reminder ID has already had its
         * notifications scheduled, this will NOT delete the old scheduled notifications. They must be
         * manually deleted via [unscheduleReviewReminderNotifications].
         */
        private fun scheduleAllEnabledReviewReminderNotifications(context: Context) {
            Timber.d("scheduleAllEnabledReviewReminderNotifications")
            val allReviewRemindersAsMap =
                ReviewRemindersDatabase.getAllAppWideReminders() + ReviewRemindersDatabase.getAllDeckSpecificReminders()
            val enabledReviewReminders = allReviewRemindersAsMap.values.filter { it.enabled }
            for (reviewReminder in enabledReviewReminders) {
                scheduleReviewReminderNotification(context, reviewReminder)
            }
        }

        /**
         * Schedules a one-time notification for a review reminder after a set amount of minutes.
         * Used for snoozing functionality.
         *
         * We could instead use WorkManager and enqueue a OneTimeWorkRequest with an initial delay of [snoozeIntervalInMinutes],
         * but WorkManager work is sometimes deferred for long periods of time by the OS.
         * Setting an explicit alarm via AlarmManager, either via [AlarmManager.set] or [AlarmManager.setWindow],
         * tends to result in more timely snooze notification recurrences. Here, we use [AlarmManager.setWindow]
         * to ensure the OS does not delay the notification for longer than at most [WINDOW_LENGTH_MS].
         *
         * @see NotificationService.NotificationServiceAction.SnoozeNotification
         */
        private fun scheduleSnoozedNotification(
            context: Context,
            reviewReminder: ReviewReminder,
            snoozeIntervalInMinutes: Int,
        ) {
            Timber.d("Beginning scheduleSnoozedNotification for ${reviewReminder.id}")
            Timber.v("Review reminder: $reviewReminder")
            val pendingIntent =
                getReviewReminderNotificationPendingIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.SnoozeNotification,
                ) ?: return
            Timber.v("Pending intent for ${reviewReminder.id} is $pendingIntent")

            val alarmTimestamp = TimeManager.time.calendar()
            alarmTimestamp.add(Calendar.MINUTE, snoozeIntervalInMinutes)
            catchAlarmManagerExceptions(context) { alarmManager ->
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimestamp.timeInMillis,
                    WINDOW_LENGTH_MS,
                    pendingIntent,
                )
                Timber.d("Successfully scheduled snoozed review reminder notifications for ${reviewReminder.id}")
            }
        }

        /**
         * Schedules all notifications defined by AlarmManagerService.
         * Since notifications are deleted when the device restarts, this method should be called on
         * device start-up, on app start-up, etc.
         * To extend the notifications created by AnkiDroid, add more functionality to the body of this method.
         */
        fun scheduleAllNotifications(context: Context) {
            scheduleAllEnabledReviewReminderNotifications(context)
        }

        /**
         * Method for getting an intent to snooze a review reminder for this service.
         */
        fun getIntent(
            context: Context,
            reviewReminder: ReviewReminder,
            snoozeInterval: Duration,
        ) = Intent(context, AlarmManagerService::class.java).apply {
            val snoozeIntervalInMinutes = snoozeInterval.inWholeMinutes.toInt()
            // Includes the snooze interval in the action string so that the pending intents for different snooze interval
            // buttons on review reminder notifications are different.
            action = "com.ichi2.anki.ACTION_START_REMINDER_SNOOZING_$snoozeIntervalInMinutes"
            putExtra(EXTRA_REVIEW_REMINDER, reviewReminder)
            putExtra(EXTRA_SNOOZE_INTERVAL, snoozeIntervalInMinutes)
        }
    }

    /**
     * Begins snoozing a review reminder.
     * @see getIntent
     */
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Timber.d("onReceive")
        // Get the request type
        val extras = intent.extras ?: return
        val reviewReminder =
            BundleCompat.getParcelable(
                extras,
                EXTRA_REVIEW_REMINDER,
                ReviewReminder::class.java,
            ) ?: return
        // Dismiss the snoozed notification when the snooze button is clicked
        val manager = context.getSystemService<NotificationManager>()
        manager?.cancel(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value)
        // The following returns 0 if the key is not found, meaning the snooze interval is 0 minutes,
        // which is an acceptable error fallback case.
        val snoozeIntervalInMinutes = extras.getInt(EXTRA_SNOOZE_INTERVAL)
        scheduleSnoozedNotification(
            context,
            reviewReminder,
            snoozeIntervalInMinutes,
        )
    }
}
