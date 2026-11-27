// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption.SCHEDULE_REMINDERS
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.ext.menu
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeckPickerPaneBackPriorityTest : RobolectricTest() {
    @Before
    fun prepareCollection() {
        RuntimeEnvironment.setQualifiers("sw800dp-w800dp-h1200dp-port")
        setIntroductionSlidesShown(true)
        addBasicNote()
        Prefs.newReviewRemindersEnabled = true
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        getPreferences().edit {
            putBoolean("backupPromptDisabled", true)
            putBoolean(targetContext.getString(R.string.safe_display_key), true)
        }
    }

    @After
    fun resetSpace() = BackupManagerTestUtilities.reset()

    @Test
    fun `back closes the drawer before leaving tablet reminders`() {
        val activity = launchWithReminders()
        val reminders = reminders(activity)
        activity.drawerLayout.openDrawer(GravityCompat.START, false)
        advanceRobolectricLooper()
        assertTrue(activity.drawerLayout.isDrawerOpen(GravityCompat.START))

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        assertFalse(activity.drawerLayout.isDrawerOpen(GravityCompat.START), "Back must close the drawer first")
        assertSame(reminders, reminders(activity), "Closing the drawer must preserve the reminders destination")
        assertFalse(activity.isFinishing)
        assertBackReturnsToStudyOptions(activity)
    }

    @Test
    fun `back closes the add menu before leaving tablet reminders`() {
        val activity = launchWithReminders()
        val reminders = reminders(activity)
        activity.floatingActionMenu.showFloatingActionMenu()
        advanceRobolectricLooper()
        assertTrue(activity.floatingActionMenu.isFABOpen)

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        assertFalse(activity.floatingActionMenu.isFABOpen, "Back must close the add menu first")
        assertSame(reminders, reminders(activity), "Closing the add menu must preserve the reminders destination")
        assertFalse(activity.isFinishing)
        assertBackReturnsToStudyOptions(activity)
    }

    @Test
    fun `back closes the drawer then add menu before leaving tablet reminders`() {
        val activity = launchWithReminders()
        val reminders = reminders(activity)
        activity.drawerLayout.openDrawer(GravityCompat.START, false)
        advanceRobolectricLooper()
        activity.floatingActionMenu.showFloatingActionMenu()
        advanceRobolectricLooper()
        assertTrue(activity.drawerLayout.isDrawerOpen(GravityCompat.START))
        assertTrue(activity.floatingActionMenu.isFABOpen)

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()
        assertFalse(activity.drawerLayout.isDrawerOpen(GravityCompat.START))
        assertTrue(activity.floatingActionMenu.isFABOpen)
        assertSame(reminders, reminders(activity))

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()
        assertFalse(activity.floatingActionMenu.isFABOpen)
        assertSame(reminders, reminders(activity))
        assertBackReturnsToStudyOptions(activity)
    }

    @Test
    fun `back closes the restored add menu after expanding to tablet reminders`() {
        RuntimeEnvironment.setQualifiers("sw400dp-w400dp-h800dp-port")
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val phone = controller.get()
        phone.viewModel.awaitStartup()
        advanceRobolectricLooper()
        assertFalse(phone.fragmented)
        phone.findViewById<View>(R.id.fab_main).performClick()
        assertTrue(phone.floatingActionMenu.isFABOpen)

        RuntimeEnvironment.setQualifiers("sw800dp-w800dp-h1200dp-port")
        controller.recreate()
        val tablet = controller.get()
        tablet.viewModel.awaitStartup()
        advanceRobolectricLooper()
        assertTrue(tablet.fragmented)
        assertNotNull(tablet.fragment)
        assertTrue(tablet.floatingActionMenu.isFABOpen)

        assertTrue(tablet.menu().performIdentifierAction(R.id.action_schedule_reminders, 0))
        advanceRobolectricLooper()
        val reminders = assertNotNull(reminders(tablet))
        assertTrue(tablet.floatingActionMenu.isFABOpen)

        tablet.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        assertFalse(tablet.floatingActionMenu.isFABOpen, "Back must close the restored Add menu first")
        assertSame(reminders, reminders(tablet))
        assertFalse(tablet.isFinishing)
        assertBackReturnsToStudyOptions(tablet)
    }

    private fun assertBackReturnsToStudyOptions(activity: DeckPicker) {
        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()
        assertFalse(activity.isFinishing)
        assertNotNull(activity.fragment)
        assertNull(reminders(activity))
    }

    private fun launchWithReminders(): DeckPicker {
        val activity = launchStudyOptions().get()
        openReminders(activity)
        return activity
    }

    private fun launchStudyOptions(): ActivityController<DeckPicker> {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val activity = controller.get()
        activity.viewModel.awaitStartup()
        advanceRobolectricLooper()
        assertNotNull(activity.fragment)
        return controller
    }

    private fun openReminders(activity: DeckPicker) {
        activity.supportFragmentManager.setDeckPickerContextMenuResult(DeckPickerContextMenuResult(1L, SCHEDULE_REMINDERS))
        advanceRobolectricLooper()
        assertNotNull(reminders(activity))
    }

    private fun reminders(activity: DeckPicker): ScheduleRemindersFragment? =
        fragments(activity.supportFragmentManager).filterIsInstance<ScheduleRemindersFragment>().singleOrNull()

    private fun fragments(manager: FragmentManager): List<Fragment> =
        manager.fragments.flatMap { listOf(it) + fragments(it.childFragmentManager) }
}
