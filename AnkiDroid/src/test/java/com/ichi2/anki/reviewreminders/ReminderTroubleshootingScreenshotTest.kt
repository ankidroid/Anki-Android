// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.reviewreminders.CheckResult.Warning
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
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

    /** Shows [ReminderTroubleshootingFragment] as a standalone screen and captures it as [name] */
    private fun captureReminderTroubleshooting(name: String) {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
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
    }
}
