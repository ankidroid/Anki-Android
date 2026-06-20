// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.PendingIntentCompat
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.services.NotificationService
import com.ichi2.utils.AlarmManagement
import timber.log.Timber
import java.util.Calendar

/**
 * Schedules review reminder notifications.
 * See [ReviewReminder] for the distinction between a "review reminder" and a "notification".
 * Actual notification firing is handled by [NotificationService], which this object triggers
 * by dispatching [NotificationService.NotificationServiceAction.ScheduleRecurringNotifications] requests.
 *
 * Snoozing is handled by [com.ichi2.anki.services.SnoozeService], which calls
 * [scheduleSnoozedNotification] on this object after receiving a snooze broadcast.
 */
object ReviewReminderAlarmManager {
    /**
     * Gets the pending intent of a review reminder's scheduled notifications.
     * This pending intent can then be used to either schedule those notifications or cancel them.
     *
     * If a review reminder with an identical ID has already had notifications scheduled via the pending intent
     * returned by this method, new notifications scheduled using this pending intent will update the existing
     * notifications rather than create duplicate new ones.
     *
     * @param context
     * @param reviewReminderId The ID of the review reminder whose notification pending intent should be retrieved.
     * @param reviewReminderScope The scope that the review reminder ID is stored within.
     * @param intentAction Schedules normal recurring notifications if set to
     * [NotificationService.NotificationServiceAction.ScheduleRecurringNotifications] or one-time snoozed
     * notifications if set to [NotificationService.NotificationServiceAction.SnoozeNotification].
     *
     * @see NotificationService.NotificationServiceAction
     */
    private fun getReviewReminderNotificationPendingIntent(
        context: Context,
        reviewReminderId: ReviewReminderId,
        reviewReminderScope: ReviewReminderScope,
        intentAction: NotificationService.NotificationServiceAction,
    ): PendingIntent? {
        val intent =
            NotificationService.getIntent(
                context,
                reviewReminderId,
                reviewReminderScope,
                intentAction,
            )
        Timber.v(
            "Created reminder notif intent with action ${intent.action} for review reminder ID $reviewReminderId",
        )
        return PendingIntentCompat.getBroadcast(
            context,
            reviewReminderId.value,
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
     * the current day's notification, we fall back to setting alarms whenever the application process is started: see
     * [com.ichi2.anki.AnkiDroidApp]'s call to [AlarmManagement.scheduleAllNotifications].
     *
     * If an old version of this review reminder with the same review reminder ID has already had
     * its notifications scheduled, this will merely update the existing notifications. If, however,
     * an old version of this review reminder with a different review reminder ID has already had its
     * notifications scheduled, this will NOT delete the old scheduled notifications. They must be
     * manually deleted via [unscheduleReviewReminderNotifications].
     *
     * @param context
     * @param reviewReminder
     * @param attemptImmediateNotification If true, attempts to fire the notification immediately as well.
     * This is to handle cases where the most recent notification firing may have been missed.
     * [NotificationService] protects against deduplication and aborts redundant sends, so when in doubt,
     * it's safe to set this to true.
     *
     * @see NotificationService.handleReviewReminderNotification
     * @see NotificationService.NotificationServiceAction.ScheduleRecurringNotifications
     */
    fun scheduleReviewReminderNotification(
        context: Context,
        reviewReminder: ReviewReminder,
        attemptImmediateNotification: Boolean,
    ) {
        Timber.d("Beginning scheduleReviewReminderNotifications for ${reviewReminder.id}")
        Timber.v("Review reminder: $reviewReminder")
        val pendingIntent =
            getReviewReminderNotificationPendingIntent(
                context,
                reviewReminder.id,
                reviewReminder.scope,
                NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
            ) ?: return
        Timber.v("Pending intent for ${reviewReminder.id} is $pendingIntent")

        if (attemptImmediateNotification) {
            // Attempt an immediate notification: If it has already been fired for the most recent scheduled time,
            // NotificationService will detect it and abort the notification.
            val immediateNotificationIntent =
                NotificationService.getIntent(
                    context,
                    reviewReminder.id,
                    reviewReminder.scope,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            context.sendBroadcast(immediateNotificationIntent)
        }

        // Schedule the next notification
        val currentTimestamp = TimeManager.time.calendar()
        val alarmTimestamp = currentTimestamp.clone() as Calendar
        alarmTimestamp.apply {
            set(Calendar.HOUR_OF_DAY, reviewReminder.time.hour)
            set(Calendar.MINUTE, reviewReminder.time.minute)
            set(Calendar.SECOND, 0)
            if (before(currentTimestamp) || this == currentTimestamp) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        AlarmManagement.useAlarmManager(context) { alarmManager ->
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                alarmTimestamp.timeInMillis,
                AlarmManagement.WINDOW_LENGTH_MS,
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
                reviewReminder.id,
                reviewReminder.scope,
                NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
            ) ?: return
        Timber.v("Pending intent for ${reviewReminder.id} is $pendingIntent")
        AlarmManagement.useAlarmManager(context) { alarmManager ->
            alarmManager.cancel(pendingIntent)
            Timber.d("Successfully unscheduled review reminder notifications for ${reviewReminder.id}")
        }
    }

    /**
     * Schedules notifications for all currently-enabled review reminders. Reads from the [ReviewRemindersDatabase].
     * Also attempts a notification send just in case the most recent one was missed. Deduplication and aborting
     * redundant notifications is handled by [NotificationService].
     *
     * If, for a review reminder in the database, an old version of a review reminder with the same review
     * reminder ID has already had its notifications scheduled, this will merely update the existing notifications.
     * If, however, an old version of a review reminder with a different review reminder ID has already had its
     * notifications scheduled, this will NOT delete the old scheduled notifications. They must be
     * manually deleted via [unscheduleReviewReminderNotifications].
     */
    suspend fun scheduleAllEnabledReviewReminderNotifications(context: Context) {
        Timber.d("scheduleAllEnabledReviewReminderNotifications")
        val enabledReviewReminders =
            ReviewRemindersDatabase
                .getAllReminders()
                .getRemindersList()
                .filter { it.enabled }

        for (reviewReminder in enabledReviewReminders) {
            scheduleReviewReminderNotification(context, reviewReminder, attemptImmediateNotification = true)
        }
    }

    /**
     * Schedules a one-time notification for a review reminder after a set amount of minutes.
     * Used for snoozing functionality. Called by [com.ichi2.anki.services.SnoozeService].
     *
     * We could instead use WorkManager and enqueue a OneTimeWorkRequest with an initial delay of [snoozeIntervalInMinutes],
     * but WorkManager work is sometimes deferred for long periods of time by the OS.
     * Setting an explicit alarm via AlarmManager, either via [AlarmManager.set] or [AlarmManager.setWindow],
     * tends to result in more timely snooze notification recurrences. Here, we use [AlarmManager.setWindow]
     * to ensure the OS does not delay the notification for longer than at most [AlarmManagement.WINDOW_LENGTH_MS].
     *
     * @see NotificationService.NotificationServiceAction.SnoozeNotification
     */
    fun scheduleSnoozedNotification(
        context: Context,
        reviewReminder: ReviewReminder,
        snoozeIntervalInMinutes: Int,
    ) {
        Timber.d("Beginning scheduleSnoozedNotification for ${reviewReminder.id}")
        Timber.v("Review reminder: $reviewReminder")
        val pendingIntent =
            getReviewReminderNotificationPendingIntent(
                context,
                reviewReminder.id,
                reviewReminder.scope,
                NotificationService.NotificationServiceAction.SnoozeNotification,
            ) ?: return
        Timber.v("Pending intent for ${reviewReminder.id} is $pendingIntent")

        val alarmTimestamp = TimeManager.time.calendar()
        alarmTimestamp.add(Calendar.MINUTE, snoozeIntervalInMinutes)
        AlarmManagement.useAlarmManager(context) { alarmManager ->
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                alarmTimestamp.timeInMillis,
                AlarmManagement.WINDOW_LENGTH_MS,
                pendingIntent,
            )
            Timber.d("Successfully scheduled snoozed review reminder notifications for ${reviewReminder.id}")
        }
    }
}
