// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.runGloballyWithTimeout
import com.ichi2.anki.utils.ext.getParcelableCompat
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * BroadcastReceiver for handling snooze actions on review reminder notifications.
 * When a user taps a snooze button on a review reminder notification, this receiver is triggered.
 * It dismisses the current notification and schedules a one-time delayed notification via
 * [ReviewReminderAlarmManager.scheduleSnoozedNotification].
 *
 * Snooze button intents are created by [NotificationService] using [getIntent].
 * The alarm scheduling logic lives in [ReviewReminderAlarmManager].
 */
class SnoozeService : AnkiBroadcastReceiver() {
    companion object {
        /**
         * Extra key for sending a [ReviewReminderId] as an extra to this BroadcastReceiver.
         */
        private const val EXTRA_REVIEW_REMINDER_ID = "alarm_manager_service_review_reminder_id"

        /**
         * Extra key for sending a [ReviewReminderScope] as an extra to this BroadcastReceiver.
         */
        private const val EXTRA_REVIEW_REMINDER_SCOPE = "alarm_manager_service_review_reminder_scope"

        /**
         * Extra key for sending a snooze delay interval as an extra to this BroadcastReceiver.
         * The stored value is an integer number of minutes.
         */
        private const val EXTRA_SNOOZE_INTERVAL = "alarm_manager_service_snooze_interval"

        /**
         * Timeout for the process of snoozing a review reminder notification.
         */
        private val SNOOZE_REVIEW_REMINDER_TIMEOUT = 8.seconds

        /**
         * Triggered by [onReceiveBroadcast]. Retrieves the review reminder associated with the provided ID.
         * Cancels the currently shown notification for the review reminder and then begins the process
         * of scheduling the next notification for the review reminder.
         *
         * Extracted from the body of [onReceiveBroadcast] to allow for easier testing and to keep
         * the receiver method concise.
         *
         * @param context
         * @param reviewReminderId The ID of the review reminder to snooze.
         * @param reviewReminderScope The scope that the review reminder ID is stored within.
         * @param snoozeIntervalInMinutes The number of minutes to delay the review reminder notification by.
         */
        @VisibleForTesting
        suspend fun handleSnoozeReviewReminder(
            context: Context,
            reviewReminderId: ReviewReminderId,
            reviewReminderScope: ReviewReminderScope,
            snoozeIntervalInMinutes: Int,
        ) {
            Timber.d(
                "handleSnoozeReviewReminder for review reminder ID $reviewReminderId with snooze interval $snoozeIntervalInMinutes minutes",
            )

            // Dismiss the snoozed notification when the snooze button is clicked
            val manager = context.getSystemService<NotificationManager>()
            manager?.cancel(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminderId.value)

            val retrievedReminder = ReviewRemindersDatabase.getRemindersForScope(reviewReminderScope)[reviewReminderId]
            if (retrievedReminder == null) {
                Timber.i(
                    "Cancelling snoozed notification scheduling for reminder $reviewReminderId because it was not found in the database.",
                )
                return
            }
            ReviewReminderAlarmManager.scheduleSnoozedNotification(context, retrievedReminder, snoozeIntervalInMinutes)
        }

        /**
         * Helper method for getting an intent to snooze a review reminder.
         */
        private fun getIntent(
            context: Context,
            reviewReminderId: ReviewReminderId,
            reviewReminderScope: ReviewReminderScope,
            snoozeInterval: Duration,
        ) = Intent(context, SnoozeService::class.java).apply {
            val snoozeIntervalInMinutes = snoozeInterval.inWholeMinutes.toInt()
            // Includes the snooze interval in the action string so that the pending intents for different snooze interval
            // buttons on review reminder notifications are different.
            action = "com.ichi2.anki.ACTION_START_REMINDER_SNOOZING_$snoozeIntervalInMinutes"
            putExtra(EXTRA_REVIEW_REMINDER_ID, reviewReminderId)
            putExtra(EXTRA_REVIEW_REMINDER_SCOPE, reviewReminderScope)
            putExtra(EXTRA_SNOOZE_INTERVAL, snoozeIntervalInMinutes)
        }

        /**
         * Gets the review reminder snoozing pending intent for the review reminder with the given ID.
         * If this method is run twice for the same review reminder ID and snooze interval, it will return the same
         * pending intent.
         *
         * @param context
         * @param reviewReminderId The ID of the review reminder the snooze intent is for.
         * @param reviewReminderScope The scope that the review reminder ID is stored within.
         * @param snoozeInterval The amount of time before the review reminder fires again,
         * used to create a unique pending intent for each snooze option.
         */
        fun getPendingIntent(
            context: Context,
            reviewReminderId: ReviewReminderId,
            reviewReminderScope: ReviewReminderScope,
            snoozeInterval: Duration,
        ): PendingIntent? {
            val intent =
                getIntent(
                    context,
                    reviewReminderId,
                    reviewReminderScope,
                    snoozeInterval,
                )
            Timber.v(
                "Created snooze intent with action ${intent.action} for review reminder ID $reviewReminderId",
            )
            return PendingIntentCompat.getBroadcast(
                context,
                reviewReminderId.value,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
        }
    }

    /**
     * Begins snoozing a review reminder.
     * @see getPendingIntent
     */
    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        Timber.d("onReceiveBroadcast")
        val extras = intent.extras ?: return
        val reviewReminderId =
            extras.getParcelableCompat<ReviewReminderId>(EXTRA_REVIEW_REMINDER_ID) ?: return
        val reviewReminderScope =
            extras.getParcelableCompat<ReviewReminderScope>(EXTRA_REVIEW_REMINDER_SCOPE) ?: return

        // The following returns 0 if the key is not found, meaning the snooze interval is 0 minutes,
        // which is an acceptable error fallback case.
        val snoozeIntervalInMinutes = extras.getInt(EXTRA_SNOOZE_INTERVAL)
        Timber.d("onReceiveBroadcast: reminder: $reviewReminderId, scope: $reviewReminderScope, interval: $snoozeIntervalInMinutes")

        runGloballyWithTimeout(SNOOZE_REVIEW_REMINDER_TIMEOUT) {
            handleSnoozeReviewReminder(
                context,
                reviewReminderId,
                reviewReminderScope,
                snoozeIntervalInMinutes,
            )
        }
    }
}
