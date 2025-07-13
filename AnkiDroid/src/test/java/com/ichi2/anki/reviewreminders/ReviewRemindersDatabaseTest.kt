/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.sharedPrefs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.anEmptyMap
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRemindersDatabaseTest : RobolectricTest() {
    private lateinit var reviewRemindersDatabase: ReviewRemindersDatabase

    private val did1 = 12345L
    private val did2 = 67890L

    private val dummyDeckSpecificRemindersForDeckOne =
        mapOf(
            ReviewReminderId(0) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(5),
                    ReviewReminderScope.DeckSpecific(did1),
                    false,
                ),
            ReviewReminderId(1) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(10, 30),
                    ReviewReminderCardTriggerThreshold(10),
                    ReviewReminderScope.DeckSpecific(did1),
                ),
        )
    private val dummyDeckSpecificRemindersForDeckTwo =
        mapOf(
            ReviewReminderId(2) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(10, 30),
                    ReviewReminderCardTriggerThreshold(10),
                    ReviewReminderScope.DeckSpecific(did2),
                    true,
                ),
            ReviewReminderId(3) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(12, 30),
                    ReviewReminderCardTriggerThreshold(20),
                    ReviewReminderScope.DeckSpecific(did2),
                ),
        )
    private val dummyAppWideReminders =
        mapOf(
            ReviewReminderId(4) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(5),
                ),
            ReviewReminderId(5) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(10, 30),
                    ReviewReminderCardTriggerThreshold(10),
                ),
        )

    @Before
    override fun setUp() {
        super.setUp()
        reviewRemindersDatabase = ReviewRemindersDatabase()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Reset the database after each test
        targetContext.sharedPrefs().edit { clear() }
    }

    @Test
    fun `getRemindersForDeck should return empty map when no reminders exist`() {
        val reminders = reviewRemindersDatabase.getRemindersForDeck(did1)
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `editRemindersForDeck and getRemindersForDeck should read and write reminders correctly`() {
        reviewRemindersDatabase.editRemindersForDeck(did1) { dummyDeckSpecificRemindersForDeckOne }
        val storedReminders = reviewRemindersDatabase.getRemindersForDeck(did1)
        assertThat(storedReminders, equalTo(dummyDeckSpecificRemindersForDeckOne))
    }

    @Test
    fun `getAllDeckSpecificReminders should return empty map when no reminders exist`() {
        val reminders = reviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `getAllDeckSpecificReminders should return all reminders across decks`() {
        reviewRemindersDatabase.editRemindersForDeck(did1) { dummyDeckSpecificRemindersForDeckOne }
        reviewRemindersDatabase.editRemindersForDeck(did2) { dummyDeckSpecificRemindersForDeckTwo }
        val allReminders = reviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(
            allReminders,
            equalTo(dummyDeckSpecificRemindersForDeckOne + dummyDeckSpecificRemindersForDeckTwo),
        )
    }

    @Test
    fun `getAllAppWideReminders should return empty map when no reminders exist`() {
        val reminders = reviewRemindersDatabase.getAllAppWideReminders()
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `editAllAppWideReminders and getAllAppWideReminders should read and write reminders correctly`() {
        reviewRemindersDatabase.editAllAppWideReminders { dummyAppWideReminders }
        val storedReminders = reviewRemindersDatabase.getAllAppWideReminders()
        assertThat(storedReminders, equalTo(dummyAppWideReminders))
    }

    @Test(expected = SerializationException::class)
    fun `getRemindersForDeck should throw SerializationException if JSON string is corrupted`() {
        targetContext.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, "corrupted_and_invalid_json_string")
        }
        reviewRemindersDatabase.getRemindersForDeck(did1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getRemindersForDeck should throw IllegalArgumentException if JSON string is not a ReviewReminder`() {
        val randomObject = Pair("not a map of", "review reminders")
        targetContext.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(randomObject))
        }
        reviewRemindersDatabase.getRemindersForDeck(did1)
    }

    @Test(expected = SerializationException::class)
    fun `getAllAppWideReminders should throw SerializationException if JSON string is corrupted`() {
        targetContext.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, "corrupted_and_invalid_json_string")
        }
        reviewRemindersDatabase.getAllAppWideReminders()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAllAppWideReminders should throw IllegalArgumentException if JSON string is not a ReviewReminder`() {
        val randomObject = Pair("not a map of", "review reminders")
        targetContext.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, Json.encodeToString(randomObject))
        }
        reviewRemindersDatabase.getAllAppWideReminders()
    }

    @Test(expected = SerializationException::class)
    fun `getAllDeckSpecificReminders should throw SerializationException if JSON string is corrupted`() {
        targetContext.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, "corrupted_and_invalid_json_string")
        }
        reviewRemindersDatabase.getAllDeckSpecificReminders()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAllDeckSpecificReminders should throw IllegalArgumentException if JSON string is not a ReviewReminder`() {
        val randomObject = Pair("not a map of", "review reminders")
        targetContext.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(randomObject))
        }
        reviewRemindersDatabase.getAllDeckSpecificReminders()
    }

    @Test
    fun `getAllReviewReminderSharedPrefsAsMap should return empty map if no reminders exist`() {
        val sharedPrefs = reviewRemindersDatabase.getAllReviewReminderSharedPrefsAsMap()
        assertThat(sharedPrefs, anEmptyMap())
    }

    @Test
    fun `getAllReviewReminderSharedPrefsAsMap should return only review reminder shared preferences`() {
        reviewRemindersDatabase.editRemindersForDeck(did1) { dummyDeckSpecificRemindersForDeckOne }
        reviewRemindersDatabase.editAllAppWideReminders { dummyAppWideReminders }

        targetContext.sharedPrefs().edit {
            putString("unrelated shared preference", "that should not be returned")
        }

        val reviewReminderSharedPrefs =
            reviewRemindersDatabase
                .getAllReviewReminderSharedPrefsAsMap()
                .values
                .toList()
                .map { Json.decodeFromString<Map<ReviewReminderId, ReviewReminder>>(it as String) }

        assertThat(reviewReminderSharedPrefs, containsInAnyOrder(dummyDeckSpecificRemindersForDeckOne, dummyAppWideReminders))
    }

    @Test
    fun `deleteAllReviewReminderSharedPrefs should do nothing if there are no review reminder shared preferences`() {
        targetContext.sharedPrefs().edit {
            putString("unrelated shared preference", "that should not be deleted")
        }
        val sharedPrefsBefore = targetContext.sharedPrefs().all
        reviewRemindersDatabase.deleteAllReviewReminderSharedPrefs()
        val sharedPrefsAfter = targetContext.sharedPrefs().all
        assertThat(sharedPrefsBefore, equalTo(sharedPrefsAfter))
    }

    @Test
    fun `deleteAllReviewReminderSharedPrefs should delete all review reminder shared preferences`() {
        targetContext.sharedPrefs().edit {
            putString("unrelated shared preference", "that should not be deleted")
        }
        val sharedPrefsBefore = targetContext.sharedPrefs().all

        reviewRemindersDatabase.editRemindersForDeck(did1) { dummyDeckSpecificRemindersForDeckOne }
        reviewRemindersDatabase.editAllAppWideReminders { dummyAppWideReminders }
        reviewRemindersDatabase.deleteAllReviewReminderSharedPrefs()

        val sharedPrefsAfter = targetContext.sharedPrefs().all
        assertThat(sharedPrefsBefore, equalTo(sharedPrefsAfter))
    }
}
