// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.content.Context
import com.ichi2.anki.common.coroutines.applicationScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Changes to [ReviewReminder]s which update both [ReviewRemindersDatabase] and the notifications
 * scheduled by [ReviewReminderAlarmManager].
 *
 * Each change runs in [applicationScope], so it completes even if the screen which requested it is destroyed
 * or recreated while waiting for the database. Alarms are only updated once the database write has succeeded,
 * so stored reminders and scheduled notifications stay consistent.
 */
object ReviewReminderMutations {
    /**
     * Ensures each change updates its alarms before the next change writes to the database, so the
     * alarms match the stored reminders when changes are queued.
     */
    private val mutex = Mutex()

    /**
     * Adds, edits, or deletes a reminder. Includes both the database write and modifying alarms.
     *
     * @param oldReminder The reminder being edited or deleted, or null if [newReminder] is being added.
     * @param newReminder The reminder to store, or null if [oldReminder] is being deleted.
     */
    suspend fun upsertOrDeleteReminder(
        context: Context,
        oldReminder: ReviewReminder?,
        newReminder: ReviewReminder?,
    ) {
        val appContext = context.applicationContext
        runToCompletion {
            ReviewRemindersDatabase.upsertOrDeleteReminder(oldReminder = oldReminder, newReminder = newReminder)
            oldReminder?.let { ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(appContext, it) }
            newReminder?.takeIf { it.enabled }?.let {
                ReviewReminderAlarmManager.scheduleReviewReminderNotification(appContext, it, attemptImmediateNotification = false)
            }
        }
    }

    /**
     * Enables or disables a reminder, then schedules or unschedules its notifications.
     */
    suspend fun setReminderEnabled(
        context: Context,
        reminder: ReviewReminder,
        enabled: Boolean,
    ) {
        val appContext = context.applicationContext
        runToCompletion {
            ReviewRemindersDatabase.setReminderEnabled(reminder, enabled)
            if (enabled) {
                ReviewReminderAlarmManager.scheduleReviewReminderNotification(
                    appContext,
                    reminder,
                    attemptImmediateNotification = false,
                )
            } else {
                ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(appContext, reminder)
            }
        }
    }

    /**
     * Runs [block] in [applicationScope], after any earlier changes, and awaits its result.
     * Failures are rethrown to the caller, and logged in case the caller is no longer awaiting.
     */
    private suspend fun runToCompletion(block: suspend () -> Unit) =
        applicationScope
            .async {
                try {
                    mutex.withLock { block() }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to change review reminder")
                    throw e
                }
            }.await()
}
