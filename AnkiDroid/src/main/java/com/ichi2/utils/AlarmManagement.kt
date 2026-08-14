// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.utils

import android.app.AlarmManager
import android.content.Context
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
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
