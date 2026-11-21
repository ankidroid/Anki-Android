// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.throwOnShowError
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.ext.insertReminder
import com.ichi2.testutils.ext.snackbarText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [ReviewReminderMutations] through [ScheduleRemindersFragment]: reminder changes must be persisted,
 * and their alarms updated to match, even if the fragment is recreated while the change is waiting for
 * [ReviewRemindersDatabase].
 */
@RunWith(AndroidJUnit4::class)
class ReviewReminderMutationsTest : RobolectricTest() {
    private val deckScope = ReviewReminderScope.DeckSpecific(12345L)

    /** The order in which [ReviewReminderAlarmManager] was asked to schedule or unschedule notifications */
    private val alarmCalls = mutableListOf<String>()

    @Before
    fun setUpReminders() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        mockkObject(ReviewReminderAlarmManager)
        every { ReviewReminderAlarmManager.scheduleReviewReminderNotification(any(), any(), any()) } answers { alarmCalls += "schedule" }
        every { ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(any(), any()) } answers { alarmCalls += "unschedule" }
    }

    @After
    fun tearDownReminders() {
        if (ReviewRemindersDatabase.mutex.isLocked) ReviewRemindersDatabase.mutex.unlock()
        unmockkObject(ReviewReminderAlarmManager, ReviewRemindersDatabase)
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `edit is persisted if the fragment is recreated while waiting for the database`() {
        val original = createTestReminder()
        insertReminder(original)
        // Edits replace the reminder with a new ID, possibly in a different scope
        val edited = createTestReminder(scope = deckScope)

        withRecreatedFragmentWhileDatabaseIsLocked(
            act = { fragment ->
                fragment.handleAddEditDialogResult(edited, AddEditReminderDialog.DialogMode.Edit(original))
            },
            whileLocked = { fragment ->
                verify(exactly = 0) { ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(any(), any()) }
                verify(exactly = 0) { ReviewReminderAlarmManager.scheduleReviewReminderNotification(any(), any(), any()) }
                assertThat(fragment.snackbarText, not(equalTo(fragment.getString(CommonString.schedule_reminders_edited))))
            },
        ) { recreated ->
            assertThat(storedReminderIds(), equalTo(setOf(edited.id)))
            assertThat(recreated.displayedReminderIds(), equalTo(setOf(edited.id)))
            verify { ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(any(), match { it.id == original.id }) }
            verify { ReviewReminderAlarmManager.scheduleReviewReminderNotification(any(), match { it.id == edited.id }, false) }
        }
    }

    @Test
    fun `toggle is persisted if the fragment is recreated while waiting for the database`() {
        val original = createTestReminder(enabled = false)
        insertReminder(original)

        withRecreatedFragmentWhileDatabaseIsLocked(
            act = { fragment -> fragment.toggleReminder(fragment.displayedReminder(original.id)) },
            whileLocked = {
                verify(exactly = 0) { ReviewReminderAlarmManager.scheduleReviewReminderNotification(any(), any(), any()) }
            },
        ) { recreated ->
            assertThat(storedReminders().single().enabled, equalTo(true))
            assertThat(recreated.displayedReminder(original.id).enabled, equalTo(true))
            verify {
                ReviewReminderAlarmManager.scheduleReviewReminderNotification(
                    any(),
                    match { it.id == original.id && it.enabled },
                    false,
                )
            }
        }
    }

    @Test
    fun `failed toggle does not change alarms and reverts the UI`() {
        val original = createTestReminder(enabled = false)
        insertReminder(original)
        mockkObject(ReviewRemindersDatabase)
        coEvery { ReviewRemindersDatabase.setReminderEnabled(any(), any()) } throws IllegalStateException("write failed")
        throwOnShowError = false // The failure is shown to the user

        withScheduleRemindersFragment { fragment ->
            fragment.toggleReminder(fragment.displayedReminder(original.id))
            advanceRobolectricLooper()

            assertThat(fragment.displayedReminder(original.id).enabled, equalTo(false))
            assertThat(alarmCalls, empty())
        }
    }

    /**
     * Launches [ScheduleRemindersFragment], then holds [ReviewRemindersDatabase.mutex] while performing [act]
     * and recreating the activity. The mutex is released before [assertAfterRelease] runs on the recreated fragment.
     */
    private fun withRecreatedFragmentWhileDatabaseIsLocked(
        act: (ScheduleRemindersFragment) -> Unit,
        whileLocked: (ScheduleRemindersFragment) -> Unit,
        assertAfterRelease: (ScheduleRemindersFragment) -> Unit,
    ) {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            runBlocking { ReviewRemindersDatabase.mutex.lock() }
            scenario.onActivity { activity ->
                val fragment = activity.fragment as ScheduleRemindersFragment
                act(fragment)
                advanceRobolectricLooper()
                whileLocked(fragment)
            }

            scenario.recreate()
            ReviewRemindersDatabase.mutex.unlock()
            advanceRobolectricLooper()

            scenario.onActivity { activity -> assertAfterRelease(activity.fragment as ScheduleRemindersFragment) }
        }
    }

    private fun createTestReminder(
        scope: ReviewReminderScope = ReviewReminderScope.Global,
        enabled: Boolean = true,
    ) = ReviewReminder.createReviewReminder(ReviewReminderTime(9, 0), scope = scope, enabled = enabled)

    private fun insertReminder(reminder: ReviewReminder) = runBlocking { ReviewRemindersDatabase.insertReminder(reminder) }

    private fun storedReminders(): List<ReviewReminder> = runBlocking { ReviewRemindersDatabase.getAllReminders().getRemindersList() }

    private fun storedReminderIds() = storedReminders().map { it.id }.toSet()

    private fun ScheduleRemindersFragment.displayedReminders(): List<ReviewReminder> =
        (binding.recyclerView.adapter as ScheduleRemindersAdapter).currentList

    private fun ScheduleRemindersFragment.displayedReminderIds() = displayedReminders().map { it.id }.toSet()

    private fun ScheduleRemindersFragment.displayedReminder(id: ReviewReminderId) = displayedReminders().single { it.id == id }
}
