// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.StudyOptionsFragment.Companion.registerStudyOptionsStudyHandler
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption.SCHEDULE_REMINDERS
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.reviewreminders.ReminderTroubleshootingFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeckPickerPaneHistoryTest : RobolectricTest() {
    @Before
    fun prepareCollection() {
        RuntimeEnvironment.setQualifiers(TABLET)
        setIntroductionSlidesShown(true)
        addBasicNote()
        Prefs.newReviewRemindersEnabled = true
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    fun resetSpace() = BackupManagerTestUtilities.reset()

    @Test
    fun `shrinking removes reminders and their navigation history`() {
        val controller = launchWithReminders()
        shrink(controller)

        assertNoPane(controller.get())
        assertFalse(controller.get().supportFragmentManager.popBackStackImmediate())
        assertNoPane(controller.get())

        RuntimeEnvironment.setQualifiers(TABLET)
        controller.recreate()
        settle(controller.get())
        val studyOptions = assertNotNull(controller.get().fragment)
        assertEquals(0, studyOptions.parentFragmentManager.backStackEntryCount)
    }

    @Test
    fun `shrinking clears nested troubleshooting navigation`() {
        val controller = launchWithReminders()
        val reminders = fragments(controller.get().supportFragmentManager).filterIsInstance<ScheduleRemindersFragment>().single()
        val toolbar = reminders.requireView().findViewById<Toolbar>(R.id.non_collapsible_toolbar)
        toolbar.menu.performIdentifierAction(R.id.action_troubleshoot, 0)
        advanceRobolectricLooper()
        assertTrue(fragments(controller.get().supportFragmentManager).any { it is ReminderTroubleshootingFragment })

        shrink(controller)

        assertNoPane(controller.get())
        assertFalse(controller.get().supportFragmentManager.popBackStackImmediate())
    }

    @Test
    fun `shrinking preserves an unrelated dialog and its back stack entry`() {
        val controller = launchWithReminders()
        val manager = controller.get().supportFragmentManager
        UnrelatedDialog().show(manager.beginTransaction().addToBackStack("unrelated"), "unrelated")
        advanceRobolectricLooper()

        shrink(controller)

        val restoredManager = controller.get().supportFragmentManager
        val dialog = assertNotNull(restoredManager.findFragmentByTag("unrelated") as? UnrelatedDialog)
        assertTrue(dialog.requireDialog().isShowing)
        assertEquals(1, restoredManager.backStackEntryCount)
        assertNoPane(controller.get())
        assertTrue(restoredManager.popBackStackImmediate())
        assertEquals(0, restoredManager.backStackEntryCount)
        assertNoPane(controller.get())
    }

    @Test
    fun `study options actions still reach the activity`() {
        val controller = launchWithReminders()
        val activity = controller.get()
        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()
        val studyOptions = assertNotNull(activity.fragment)
        var studyRequests = 0
        activity.registerStudyOptionsStudyHandler { studyRequests++ }

        studyOptions.requireView().findViewById<View>(R.id.studyoptions_start).performClick()
        assertEquals(1, studyRequests)

        val toolbar = Toolbar(activity)
        toolbar.inflateMenu(R.menu.study_options_fragment)
        studyOptions.onMenuItemSelected(toolbar.menu.findItem(R.id.action_schedule_reminders))
        advanceRobolectricLooper()
        assertTrue(fragments(activity.supportFragmentManager).any { it is ScheduleRemindersFragment })
    }

    @Test
    fun `back returns from reminders to study options on tablet`() {
        val controller = launchWithReminders()

        controller.get().onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        assertFalse(controller.get().isFinishing)
        assertNotNull(controller.get().fragment)
        assertFalse(fragments(controller.get().supportFragmentManager).any { it is ScheduleRemindersFragment })
    }

    private fun launchWithReminders(): ActivityController<DeckPicker> {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        settle(controller.get())
        assertNotNull(controller.get().fragment)
        controller.get().supportFragmentManager.setDeckPickerContextMenuResult(DeckPickerContextMenuResult(1L, SCHEDULE_REMINDERS))
        advanceRobolectricLooper()
        val reminders = fragments(controller.get().supportFragmentManager).filterIsInstance<ScheduleRemindersFragment>().single()
        assertEquals(1, reminders.parentFragmentManager.backStackEntryCount)
        return controller
    }

    private fun shrink(controller: ActivityController<DeckPicker>) {
        val original = controller.get()
        RuntimeEnvironment.setQualifiers("sw400dp-w400dp-h800dp-port")
        controller.recreate()
        settle(controller.get())
        assertNotSame(original, controller.get())
        assertFalse(controller.get().fragmented)
    }

    private fun settle(activity: DeckPicker) {
        activity.viewModel.awaitStartup()
        advanceRobolectricLooper()
    }

    private fun assertNoPane(activity: DeckPicker) {
        assertNull(activity.fragment)
        assertFalse(
            fragments(activity.supportFragmentManager).any {
                it is StudyOptionsFragment || it is ScheduleRemindersFragment || it is ReminderTroubleshootingFragment
            },
        )
    }

    private fun fragments(manager: FragmentManager): List<Fragment> =
        manager.fragments.flatMap { listOf(it) + fragments(it.childFragmentManager) }

    class UnrelatedDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(requireContext()).setMessage("Unrelated").create()
    }

    companion object {
        private const val TABLET = "sw800dp-w800dp-h1200dp-port"
    }
}
