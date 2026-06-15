// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.utils

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

/**
 * Utility object which holds common constants and functions for interfacing with the Android [AlarmManager].
 */
object AlarmManagement {
    /**
     * Interval passed to [AlarmManager.setWindow], in milliseconds. The OS is allowed to delay AnkiDroid's alarms
     * by at most this amount of time. We set it to 10 minutes, which is the minimum allowable duration
     * according to [the docs](https://developer.android.com/reference/android/app/AlarmManager).
     */
    val WINDOW_LENGTH_MS: Long = 10.minutes.inWholeMilliseconds

    /**
     * Retrieves the [AlarmManager] system service and passes it to [block]. Shows an error toast if
     * the service is unavailable or if the block throws a [SecurityException] or other exception.
     * This function wraps all calls to [AlarmManager] in AnkiDroid.
     */
    fun useAlarmManager(
        context: Context,
        block: (AlarmManager) -> Unit,
    ) {
        var error: String? = null
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
            error = "Too many alarms set"
        } catch (e: Exception) {
            Timber.w(e)
            error = "Failed to schedule alarm"
        }
        if (error != null) {
            try {
                showThemedToast(context, error, false)
            } catch (e: Exception) {
                Timber.w(e, "Failed to show AlarmManager exception toast for error: $error")
            }
        }
    }

    /**
     * Schedules all notifications defined by AnkiDroid.
     * Since notifications are deleted when the device restarts, this method should be called on
     * device start-up, on app start-up, etc.
     * To extend the notifications created by AnkiDroid, add more functionality to the body of this method.
     */
    suspend fun scheduleAllNotifications(context: Context) {
        // currently, the only scheduled notifications supported by AnkiDroid are review reminder notifications
        ReviewReminderAlarmManager.scheduleAllEnabledReviewReminderNotifications(context)
    }
}
