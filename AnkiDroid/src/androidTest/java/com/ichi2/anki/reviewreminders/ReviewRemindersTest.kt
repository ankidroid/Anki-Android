// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.text.format.DateFormat
import android.widget.EditText
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.google.android.material.R as MaterialR

/** Exercises the reminder dialogs through the UI and checks their persisted results. */
@RunWith(AndroidJUnit4::class)
class ReviewRemindersTest : InstrumentedTest() {
    @get:Rule
    val storagePermissionRule = GrantStoragePermission.instance

    private var reminderNotifsRequestShown = false

    private val allDecksName: String
        get() = with(testContext) { TR.sentenceCase.allDecks }

    @Before
    fun setUpReminders() {
        CollectionManager.setColForTests(emptyCol)
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        reminderNotifsRequestShown = Prefs.reminderNotifsRequestShown
        Prefs.reminderNotifsRequestShown = true
    }

    @After
    fun tearDownReminders() {
        storedReminders().forEach {
            ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(testContext, it)
        }
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        Prefs.reminderNotifsRequestShown = reminderNotifsRequestShown
    }

    @Test
    fun createReminderWithDefaults() {
        withReminders {
            openAddDialog()
            onView(withId(android.R.id.button1)).perform(click())

            val reminder = awaitSingleReminder()
            assertEquals(ReviewReminderScope.Global, reminder.scope)
            assertEquals(ReviewReminderCardTriggerThreshold(1), reminder.cardTriggerThreshold)
            assertTrue(reminder.enabled)
            assertFalse(reminder.onlyNotifyIfNoReviews)
            assertReminderRow(reminder, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(storedReminders().single(), allDecksName)
        }
    }

    @Test
    fun customizeReminderBeforeCreatingIt() {
        val deckName = "Reminder deck"
        val deckId = col.decks.id(deckName)
        val time = ReviewReminderTime(9, 15)
        withReminders {
            openAddDialog()
            selectDeck(deckName)
            selectTime(time)
            onView(withId(R.id.add_edit_reminder_advanced_dropdown)).perform(click())
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).perform(scrollTo(), replaceText("5"), closeSoftKeyboard())
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).perform(click())

            val reminder = awaitSingleReminder()
            assertEquals(ReviewReminderScope.DeckSpecific(deckId), reminder.scope)
            assertEquals(time, reminder.time)
            assertEquals(ReviewReminderCardTriggerThreshold(5), reminder.cardTriggerThreshold)
            assertTrue(reminder.onlyNotifyIfNoReviews)
            assertTrue(reminder.enabled)
            assertReminderRow(reminder, deckName)
        }
        withReminders(scope = ReviewReminderScope.DeckSpecific(deckId), reminderCount = 1) {
            assertReminderRow(storedReminders().single(), deckName)
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            onView(withId(R.id.add_edit_reminder_advanced_dropdown)).perform(click())
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).check(matches(withText("5")))
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).check(matches(isChecked()))
        }
    }

    @Test
    fun cancelCreatingReminder() {
        withReminders {
            openAddDialog()
            onView(withId(android.R.id.button3)).perform(click())

            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
            onView(withId(R.id.no_reminders_placeholder)).check(matches(isDisplayed()))
            assertTrue(storedReminders().isEmpty())
        }
    }

    private fun withReminders(
        scope: ReviewReminderScope = ReviewReminderScope.Global,
        reminderCount: Int = 0,
        action: () -> Unit,
    ) {
        ActivityScenario
            .launch<ConfigAwareSingleFragmentActivity>(ScheduleRemindersFragment.getIntent(testContext, scope))
            .use {
                assertReminderCount(reminderCount)
                action()
            }
    }

    private fun openAddDialog() {
        onView(withId(R.id.floating_action_button_add)).perform(click())
        onView(withId(R.id.add_edit_reminder_deck_name)).checkWithTimeout(matches(withText(allDecksName)))
    }

    private fun selectDeck(deckName: String) {
        onView(withId(R.id.add_edit_reminder_deck_name)).perform(click())
        onView(withText(deckName)).checkWithTimeout(matches(isDisplayed()))
        onView(withText(deckName)).perform(click())
        onView(withId(R.id.add_edit_reminder_deck_name)).checkWithTimeout(matches(withText(deckName)))
    }

    /** Uses the time picker's keyboard mode; the selected time is in the morning. */
    private fun selectTime(time: ReviewReminderTime) {
        onView(withId(R.id.add_edit_reminder_time_button)).perform(click())
        onView(withId(MaterialR.id.material_timepicker_mode_button)).perform(click())
        onView(allOf(isAssignableFrom(EditText::class.java), isDescendantOfA(withId(MaterialR.id.material_hour_text_input))))
            .perform(replaceText(time.hour.toString()), closeSoftKeyboard())
        onView(allOf(isAssignableFrom(Chip::class.java), isDescendantOfA(withId(MaterialR.id.material_minute_text_input))))
            .perform(click())
        onView(allOf(isAssignableFrom(EditText::class.java), isDescendantOfA(withId(MaterialR.id.material_minute_text_input))))
            .perform(replaceText(time.minute.toString()), closeSoftKeyboard())
        if (!DateFormat.is24HourFormat(testContext)) {
            onView(allOf(withId(MaterialR.id.material_clock_period_am_button), isDisplayed())).perform(click())
        }
        onView(withId(MaterialR.id.material_timepicker_ok_button)).perform(click())
        onView(withId(R.id.add_edit_reminder_time_button)).check(matches(withText(time.toFormattedString(testContext))))
    }

    private fun storedReminders(): List<ReviewReminder> = runBlocking { ReviewRemindersDatabase.getAllReminders().getRemindersList() }

    private fun awaitSingleReminder(): ReviewReminder {
        waitUntil(message = { "Expected one saved reminder, found ${storedReminders()}" }) { storedReminders().size == 1 }
        return storedReminders().single()
    }

    private fun assertReminderCount(count: Int) {
        onView(withId(R.id.recycler_view)).checkWithTimeout(
            { view, exception ->
                if (exception != null) throw exception
                assertEquals(count, (view as RecyclerView).adapter!!.itemCount)
            },
        )
        onView(withId(R.id.no_reminders_placeholder)).checkWithTimeout(matches(if (count == 0) isDisplayed() else not(isDisplayed())))
    }

    private fun assertReminderRow(
        reminder: ReviewReminder,
        deckName: String,
    ) {
        assertReminderCount(1)
        onView(withId(R.id.reminders_list_deck_text)).checkWithTimeout(matches(withText(deckName)))
        onView(withId(R.id.reminders_list_time_text)).check(matches(withText(reminder.time.toFormattedString(testContext))))
        onView(withId(R.id.reminders_list_switch)).check(matches(if (reminder.enabled) isChecked() else not(isChecked())))
    }
}
