// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import androidx.fragment.app.commit
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.reviewreminders.CheckResult.Failed
import com.ichi2.anki.reviewreminders.CheckResult.Warning
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import org.junit.Test

class ReminderTroubleshootingScreenshotTest : ScreenshotTest() {
    @Test
    fun `all checks passing`() {
        targetContext.setTroubleshootingChecks()
        captureReminderTroubleshooting("allChecksPassing")
    }

    @Test
    fun `checks with warnings`() {
        targetContext.setTroubleshootingChecks(
            doNotDisturb = Warning,
            batteryOptimization = Warning,
            powerSavingMode = Warning,
        )

        captureReminderTroubleshooting("checksWithWarnings")
    }

    @Test
    fun `failed checks`() {
        targetContext.setTroubleshootingChecks(
            notificationPermission = Failed,
            notificationChannel = Failed,
            batteryOptimization = Failed,
        )

        captureReminderTroubleshooting("failedChecks")
    }

    /** Shows [ReminderTroubleshootingFragment] as a standalone screen and captures it as [name] */
    private fun captureReminderTroubleshooting(name: String) =
        withStandaloneScheduleReminders { activity ->
            activity.supportFragmentManager.commit {
                replace(
                    R.id.fragment_container,
                    ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY),
                )
            }
            advanceRobolectricLooper()
            captureScreen(name)
        }
}
