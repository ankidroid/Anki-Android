// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import com.google.android.material.appbar.AppBarLayout
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.launchActivity
import com.ichi2.anki.databinding.FragmentReminderTroubleshootingBinding
import com.ichi2.anki.databinding.FragmentScheduleRemindersBinding
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.utils.dp
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Covers all [FragmentHost] configurations of the fragment.
 */
class ReviewRemindersScreenshotTest : ScreenshotTest() {
    @Test
    fun `settings host`() {
        captureSettingsHost("settingsHost")
    }

    @Test
    fun `settings host tablet`() {
        setTabletQualifiers()
        // The toolbar is not collapsible on wide screens
        captureSettingsHost("settingsHostTablet", captureScrolled = false)
    }

    private fun captureSettingsHost(
        prefix: String,
        captureScrolled: Boolean = true,
    ) {
        withSettingsScheduleReminders { _, fm ->
            captureScreen("${prefix}_scheduleReminders")
            if (captureScrolled) {
                fm.collapseToolbar()
                captureScreen("${prefix}_scheduleReminders_scrolled")
            }
            commitTroubleshootingAndCapture(
                fragmentManager = fm,
                containerId = R.id.settings_container,
                host = FragmentHost.SETTINGS,
                prefix = prefix,
            )
        }
    }

    @Test
    fun `settings host with a landscape display cutout`() {
        insertReminders(count = 6)
        RuntimeEnvironment.setQualifiers("+land")
        withSettingsScheduleReminders { activity, fm ->
            activity.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("settingsHost_landscapeCutout")

            // collapsed: the content scrim must extend behind the cutout band while the
            // toolbar content stays clear of it
            fm.collapseToolbar()
            captureScreen("settingsHost_landscapeCutout_collapsed")
        }
    }

    @Test
    fun `settings host with a landscape display cutout - RTL`() {
        insertReminders(count = 6)
        RuntimeEnvironment.setQualifiers("+ar")
        RuntimeEnvironment.setQualifiers("+land")
        withSettingsScheduleReminders { activity, _ ->
            // the cutout is physically on the left; the expanded title starts at the right
            // in RTL, so it is inset via its end margin
            activity.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("settingsHost_landscapeCutout_rtl")
        }
    }

    @Test
    fun `study options fragment host`() {
        setTabletQualifiers()
        withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
            val deckId = addDeck("Test Deck")
            commitScheduleRemindersAndCapture(
                fragmentManager = deckPicker.supportFragmentManager,
                containerId = R.id.studyoptions_fragment,
                host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                scope = ReviewReminderScope.DeckSpecific(deckId),
                prefix = "studyOptionsFragmentHost",
            )
            commitTroubleshootingAndCapture(
                fragmentManager = deckPicker.supportFragmentManager,
                containerId = R.id.studyoptions_fragment,
                host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                prefix = "studyOptionsFragmentHost",
            )
        }
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `study options frame host`() {
        val deckId = addDeck("Test Deck")
        launchActivity<StudyOptionsActivity>(StudyOptionsDestination).use { scenario ->
            scenario.onActivity { activity ->
                commitScheduleRemindersAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.studyoptions_frame,
                    host = FragmentHost.STUDY_OPTIONS_FRAME,
                    scope = ReviewReminderScope.DeckSpecific(deckId),
                    prefix = "studyOptionsFrameHost",
                )
                commitTroubleshootingAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.studyoptions_frame,
                    host = FragmentHost.STUDY_OPTIONS_FRAME,
                    prefix = "studyOptionsFrameHost",
                )
            }
        }
    }

    @Test
    fun `standalone activity host`() =
        withStandaloneScheduleReminders { activity ->
            captureScreen("standaloneActivityHost_scheduleReminders")
            commitTroubleshootingAndCapture(
                fragmentManager = activity.supportFragmentManager,
                containerId = R.id.fragment_container,
                host = FragmentHost.STANDALONE_ACTIVITY,
                prefix = "standaloneActivityHost",
            )
        }

    @Test
    fun `standalone activity host with system bars`() =
        withStandaloneScheduleReminders { activity ->
            activity.simulateSystemBars()
            captureScreen("standaloneActivityHost_systemBars")
        }

    @Test
    fun `standalone activity host with system bars and a scrollable list`() {
        insertReminders(count = 12)
        withStandaloneScheduleReminders { activity ->
            activity.simulateSystemBars()
            val binding = FragmentScheduleRemindersBinding.bind(activity.fragment!!.requireView())
            // scrolled to the end: the last reminder must clear the navigation bar band
            binding.recyclerView.scrollToPosition(binding.recyclerView.adapter!!.itemCount - 1)
            advanceRobolectricLooper()
            captureScreen("standaloneActivityHost_systemBars_scrolledToEnd")
        }
    }

    @Test
    fun `standalone activity host troubleshooting with system bars`() {
        // landscape: the checks overflow the screen, so the end of the content must scroll
        // clear of the navigation bar band
        RuntimeEnvironment.setQualifiers("+land")
        withStandaloneScheduleReminders { activity ->
            activity.supportFragmentManager.commit {
                replace(
                    R.id.fragment_container,
                    ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY),
                )
            }
            advanceRobolectricLooper()
            activity.simulateSystemBars()
            val binding =
                FragmentReminderTroubleshootingBinding.bind(
                    activity.supportFragmentManager
                        .findFragmentById(R.id.fragment_container)!!
                        .requireView(),
                )
            // scrolled to the end: the last check must clear the navigation bar band
            binding.scrollView.scrollTo(0, binding.scrollView.getChildAt(0).bottom)
            advanceRobolectricLooper()
            captureScreen("standaloneActivityHost_troubleshooting_systemBars")
        }
    }

    /** Inserts [count] reminders so the list has content to render behind the simulated bars */
    private fun insertReminders(count: Int) {
        runBlocking {
            repeat(count) { index ->
                ReviewRemindersDatabase.insertReminder(
                    ReviewReminder.createReviewReminder(ReviewReminderTime(hour = 8 + index, minute = 0)),
                )
            }
        }
    }

    private fun commitScheduleRemindersAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        scope: ReviewReminderScope,
        prefix: String,
    ) {
        fragmentManager.commit {
            replace(containerId, ScheduleRemindersFragment.newInstance(scope, host))
        }
        advanceRobolectricLooper()
        captureScreen("${prefix}_scheduleReminders")
    }

    private fun commitTroubleshootingAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        prefix: String,
    ) {
        fragmentManager.commit {
            replace(containerId, ReminderTroubleshootingFragment.newInstance(host))
            addToBackStack(null)
        }
        advanceRobolectricLooper()
        captureScreen("${prefix}_troubleshooting")
    }

    /** Launches [ScheduleRemindersFragment] hosted in the settings screen */
    private fun withSettingsScheduleReminders(block: (PreferencesActivity, FragmentManager) -> Unit) {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val fm = (activity.fragment as PreferencesFragment).childFragmentManager
                fm.commit {
                    replace(
                        R.id.settings_container,
                        ScheduleRemindersFragment.newInstance(ReviewReminderScope.Global, FragmentHost.SETTINGS),
                    )
                }
                advanceRobolectricLooper()
                block(activity, fm)
            }
        }
    }

    /** Launches [ScheduleRemindersFragment] in its standalone activity */
    private fun withStandaloneScheduleReminders(block: (ConfigAwareSingleFragmentActivity) -> Unit) {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity -> block(activity) }
        }
    }

    /** Collapses the settings host's toolbar, as when the list has been scrolled */
    private fun FragmentManager.collapseToolbar() {
        findFragmentById(R.id.settings_container)
            ?.view
            ?.findViewById<AppBarLayout>(R.id.appbar)
            ?.setExpanded(false, false)
        advanceRobolectricLooper()
    }
}
