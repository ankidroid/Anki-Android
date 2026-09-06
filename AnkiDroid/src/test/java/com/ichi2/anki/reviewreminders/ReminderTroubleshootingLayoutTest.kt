// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import androidx.fragment.app.commit
import androidx.recyclerview.widget.ListAdapter
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * Layout of [ReminderTroubleshootingFragment]'s checks list, which is nested inside a scroll
 * container and must be measured at its full content height.
 */
@RunWith(AndroidJUnit4::class)
class ReminderTroubleshootingLayoutTest : RobolectricTest() {
    /** The minimum height of a check item, from the item layout XML */
    private val checkItemMinHeight = 72.dp

    @Test
    fun `all checks are displayed when the list overflows the screen`() {
        // landscape: the checks list is much taller than the scroll viewport
        RuntimeEnvironment.setQualifiers("+land")
        val checks =
            listOf(
                TroubleshootingCheck.NotificationPermission(CheckResult.Failed),
                TroubleshootingCheck.NotificationChannelEnabled(CheckResult.Failed),
                TroubleshootingCheck.DoNotDisturbOff(CheckResult.Warning),
                TroubleshootingCheck.UnrestrictedOptimizationEnabled(CheckResult.Failed),
                TroubleshootingCheck.PowerSavingModeOff(CheckResult.Warning),
                TroubleshootingCheck.ExactAlarmPermission(CheckResult.Warning),
            )

        withTroubleshootingFragment {
            val checksList = binding.checksList
            @Suppress("UNCHECKED_CAST")
            (checksList.adapter as ListAdapter<TroubleshootingCheck, *>).submitList(checks)
            advanceRobolectricLooper()

            val itemCount = checksList.adapter!!.itemCount
            assertThat(
                "sanity: enough checks to overflow the landscape viewport",
                itemCount,
                greaterThanOrEqualTo(4),
            )
            assertThat(
                "every check has a laid-out item",
                checksList.childCount,
                equalTo(itemCount),
            )
            assertThat(
                "the list is at least as tall as its items' minimum heights",
                checksList.height,
                greaterThanOrEqualTo(itemCount * checkItemMinHeight.toPx(targetContext)),
            )
        }
    }

    /** Runs [block] on a [ReminderTroubleshootingFragment] in its standalone activity */
    private fun withTroubleshootingFragment(block: ReminderTroubleshootingFragment.() -> Unit) {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity ->
                val fragment = ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY)
                activity.supportFragmentManager.commit { replace(R.id.fragment_container, fragment) }
                advanceRobolectricLooper()
                fragment.block()
            }
        }
    }
}
