// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2016 Siarhei Krukau <siarhei.krukau@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.services

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.storage.grantedStoragePermissions
import com.ichi2.anki.runGloballyWithTimeout
import com.ichi2.anki.scheduleAllNotifications
import com.ichi2.anki.widget.RECURRING_WIDGETS
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.restoreRecurringAlarms
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * BroadcastReceiver which listens to the Android system-level intent that fires when the device starts up.
 * Schedules notifications for review reminders.
 *
 * Note that Android battery optimizations may potentially block us from receiving the [Intent.ACTION_BOOT_COMPLETED]
 * intent, which could cause review reminders to not be scheduled.
 */
@NeedsTest("Check on various Android versions that this can execute")
class BootService : AnkiBroadcastReceiver() {
    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        if (!intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Timber.w("BootService - unexpected action received, ignoring: %s", intent.action)
            return
        }
        if (wasRun) {
            Timber.d("BootService - Already run")
            return
        }
        if (runCatching { grantedStoragePermissions(context, showToast = false) }.getOrNull() != true) {
            Timber.w("Boot Service did not execute - no permissions")
            return
        }
        Timber.i("Setting notifications upon boot")
        runGloballyWithTimeout(SCHEDULE_NOTIFICATIONS_TIMEOUT) {
            scheduleAllNotifications(context)
        }

        restoreRecurringAlarms(context, RECURRING_WIDGETS)
        DayRolloverAlarm.scheduleNext(context)
        wasRun = true
    }

    companion object {
        /**
         * Timeout for the process of scheduling all AnkiDroid notifications.
         * Should be below 10 seconds as BroadcastReceivers may ANR when onReceive takes longer than 10 seconds.
         * See [the docs](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()).
         */
        private val SCHEDULE_NOTIFICATIONS_TIMEOUT = 8.seconds

        /**
         * This service is also run when the app is started (from [com.ichi2.anki.AnkiDroidApp],
         * so we need to make sure that it isn't run twice.
         */
        private var wasRun = false
    }
}
