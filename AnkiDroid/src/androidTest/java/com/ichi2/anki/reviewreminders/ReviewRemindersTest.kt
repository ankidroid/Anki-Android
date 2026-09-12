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
import com.ichi2.anki.common.time.TimeManager
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

    @Test
    fun editReminderChangingDeckAndSettings() {
        val originalScope = ReviewReminderScope.DeckSpecific(col.decks.id("Original deck"))
        val destinationDeckName = "Destination deck"
        val destinationScope = ReviewReminderScope.DeckSpecific(col.decks.id(destinationDeckName))
        val original =
            ReviewReminder
                .createReviewReminder(
                    time = ReviewReminderTime(7, 30),
                    cardTriggerThreshold = ReviewReminderCardTriggerThreshold(2),
                    scope = originalScope,
                    enabled = false,
                ).apply { latestNotifTime = 1L }
        insertReminder(original)

        val updatedTime = ReviewReminderTime(9, 45)
        withReminders(reminderCount = 1) {
            assertReminderRow(original, "Original deck")
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            onView(withId(R.id.add_edit_reminder_deck_name)).checkWithTimeout(matches(withText("Original deck")))
            onView(withId(R.id.add_edit_reminder_time_button)).check(matches(withText(original.time.toFormattedString(testContext))))
            onView(withId(R.id.add_edit_reminder_advanced_dropdown)).perform(click())
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).check(matches(withText("2")))
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).check(matches(not(isChecked())))

            selectDeck(destinationDeckName)
            selectTime(updatedTime)
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).perform(scrollTo(), replaceText("7"), closeSoftKeyboard())
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).perform(scrollTo(), click())
            val beforeSave = TimeManager.time.intTimeMS()
            onView(withId(android.R.id.button1)).perform(click())

            val updated = awaitSingleReminder { it.scope == destinationScope }
            val afterSave = TimeManager.time.intTimeMS()
            assertEquals(updatedTime, updated.time)
            assertEquals(ReviewReminderCardTriggerThreshold(7), updated.cardTriggerThreshold)
            assertTrue(updated.onlyNotifyIfNoReviews)
            assertFalse(updated.enabled)
            assertTrue(updated.latestNotifTime in beforeSave..afterSave, "Editing must reset the last notification time to now")
            assertTrue(runBlocking { ReviewRemindersDatabase.getRemindersForScope(originalScope).isEmpty() })
            assertReminderRow(updated, destinationDeckName)
        }
        withReminders(scope = destinationScope, reminderCount = 1) {
            assertReminderRow(storedReminders().single(), destinationDeckName)
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            onView(withId(R.id.add_edit_reminder_advanced_dropdown)).perform(click())
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).check(matches(withText("7")))
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).check(matches(isChecked()))
        }
        withReminders(scope = originalScope) {
            onView(withId(R.id.no_reminders_placeholder)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun cancelEditingReminder() {
        val original = ReviewReminder.createReviewReminder(time = ReviewReminderTime(7, 30))
        insertReminder(original)
        withReminders(reminderCount = 1) {
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            selectTime(ReviewReminderTime(9, 45))
            onView(withId(android.R.id.button3)).perform(click())

            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
            assertEquals(listOf(original), storedReminders())
            assertReminderRow(original, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(original, allDecksName)
        }
    }

    @Test
    fun deleteLastReminder() {
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(9, 15))
        insertReminder(reminder)
        withReminders(reminderCount = 1) {
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            openDeleteConfirmation()
            assertEquals(listOf(reminder), storedReminders(), "Deletion must wait for confirmation")
            onView(withId(android.R.id.button1)).perform(click())

            waitUntil(message = { "The deleted reminder is still stored: ${storedReminders()}" }) { storedReminders().isEmpty() }
            assertReminderCount(0)
        }
        withReminders {
            onView(withId(R.id.no_reminders_placeholder)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun cancelDeletingReminder() {
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(9, 15))
        insertReminder(reminder)
        withReminders(reminderCount = 1) {
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            openDeleteConfirmation()
            onView(withId(android.R.id.button2)).perform(click())

            onView(withText("Delete this reminder?")).check(doesNotExist())
            assertEquals(listOf(reminder), storedReminders())
            onView(withId(R.id.add_edit_reminder_toolbar)).check(matches(isDisplayed()))
            onView(withId(android.R.id.button3)).perform(click())
            assertReminderRow(reminder, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, allDecksName)
        }
    }

    @Test
    fun deleteReminderForDeletedDeck() {
        val deckId = col.decks.id("Deck to delete")
        val orphan =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(9, 15),
                scope = ReviewReminderScope.DeckSpecific(deckId),
            )
        val remaining = ReviewReminder.createReviewReminder(time = ReviewReminderTime(10, 30))
        insertReminder(orphan)
        insertReminder(remaining)
        col.decks.remove(listOf(deckId))

        withReminders(reminderCount = 2) {
            onView(withText("Deck not found")).checkWithTimeout(matches(isDisplayed()))
            onView(withText("Deck not found")).perform(click())
            openDeleteConfirmation()
            onView(withId(android.R.id.button1)).perform(click())

            assertEquals(remaining, awaitSingleReminder { it.id == remaining.id })
            assertTrue(runBlocking { ReviewRemindersDatabase.getRemindersForScope(orphan.scope).isEmpty() })
            assertReminderRow(remaining, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(remaining, allDecksName)
        }
    }

    @Test
    fun disableAndEnableReminder() {
        val deckName = "Reminder deck"
        val reminder =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(9, 15),
                scope = ReviewReminderScope.DeckSpecific(col.decks.id(deckName)),
                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(5),
                onlyNotifyIfNoReviews = true,
            )
        insertReminder(reminder)

        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, deckName)
            onView(withId(R.id.reminders_list_switch)).perform(click())

            val disabled = awaitSingleReminder { !it.enabled }
            reminder.enabled = false
            assertEquals(reminder, disabled, "Disabling must preserve the reminder's other settings")
            assertReminderRow(disabled, deckName)
            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, deckName)
            onView(withId(R.id.reminders_list_switch)).perform(click())

            val enabled = awaitSingleReminder { it.enabled }
            reminder.enabled = true
            assertEquals(reminder, enabled, "Enabling must preserve the reminder's other settings")
            assertReminderRow(enabled, deckName)
            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, deckName)
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

    private fun openDeleteConfirmation() {
        onView(withId(android.R.id.button2)).perform(click())
        onView(withText("Delete this reminder?")).check(matches(isDisplayed()))
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

    private fun insertReminder(reminder: ReviewReminder) = runBlocking { ReviewRemindersDatabase.insertReminder(reminder) }

    private fun awaitSingleReminder(predicate: (ReviewReminder) -> Boolean = { true }): ReviewReminder {
        waitUntil(message = { "Expected one saved reminder matching the changes, found ${storedReminders()}" }) {
            storedReminders().singleOrNull()?.let(predicate) == true
        }
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
