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

import android.app.Activity
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.throwOnShowError
import kotlinx.serialization.json.Json
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.anEmptyMap
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRemindersDatabaseTest : RobolectricTest() {
    private lateinit var activity: Activity
    private lateinit var reviewRemindersDatabase: ReviewRemindersDatabase

    @Before
    override fun setUp() {
        super.setUp()
        throwOnShowError = false

        // We need a valid activity context for the database since ReviewRemindersDatabase might run showError
        activity = startRegularActivity<AnkiActivity>()
        reviewRemindersDatabase = ReviewRemindersDatabase(activity)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Reset the database after each test
        activity.sharedPrefs().edit { clear() }
    }

    @Test
    fun `getRemindersForDeck should return empty map when no reminders exist`() {
        val did = 12345L
        val reminders = reviewRemindersDatabase.getRemindersForDeck(did)
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `editRemindersForDeck and getRemindersForDeck should read and write reminders correctly`() {
        val did = 12345L
        val newReminders =
            mapOf(
                0 to ReviewReminder.createReviewReminder(activity, 9, 0, 2, 15, 5, did),
                1 to ReviewReminder.createReviewReminder(activity, 10, 30, 2, 15, 10, did),
            )

        reviewRemindersDatabase.editRemindersForDeck(did) { newReminders }
        val storedReminders = reviewRemindersDatabase.getRemindersForDeck(did)
        assertThat(storedReminders, equalTo(newReminders))
    }

    @Test
    fun `getAllDeckSpecificReminders should return empty map when no reminders exist`() {
        val reminders = reviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `getAllDeckSpecificReminders should return all reminders across decks`() {
        val did1 = 12345L
        val did2 = 67890L
        val reminders1 =
            mapOf(
                0 to ReviewReminder.createReviewReminder(activity, 9, 0, ReviewReminder.SpecialSnoozeAmounts.DISABLED, 15, 5, did1),
                1 to ReviewReminder.createReviewReminder(activity, 11, 0, ReviewReminder.SpecialSnoozeAmounts.INFINITE, 15, 5, did1),
            )
        val reminders2 =
            mapOf(
                2 to ReviewReminder.createReviewReminder(activity, 10, 30, 2, 15, 10, did2),
                3 to ReviewReminder.createReviewReminder(activity, 12, 30, 3, 10, 20, did2),
            )

        reviewRemindersDatabase.editRemindersForDeck(did1) { reminders1 }
        reviewRemindersDatabase.editRemindersForDeck(did2) { reminders2 }
        val allReminders = reviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(
            allReminders,
            equalTo(reminders1 + reminders2),
        )
    }

    @Test
    fun `getAllAppWideReminders should return empty map when no reminders exist`() {
        val reminders = reviewRemindersDatabase.getAllAppWideReminders()
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `editAllAppWideReminders and getAllAppWideReminders should read and write reminders correctly`() {
        val newReminders =
            mapOf(
                0 to ReviewReminder.createReviewReminder(activity, 9, 0, 1, 30, 5),
                1 to ReviewReminder.createReviewReminder(activity, 10, 30, 3, 60, 10),
            )

        reviewRemindersDatabase.editAllAppWideReminders { newReminders }
        val storedReminders = reviewRemindersDatabase.getAllAppWideReminders()
        assertThat(storedReminders, equalTo(newReminders))
    }

    @Test
    fun `editAllDeckSpecificReminders should update all reminders across decks`() {
        val did1 = 12345L
        val did2 = 67890L
        val did3 = 13579L
        val reminders1Old = mapOf(0 to ReviewReminder.createReviewReminder(activity, 9, 0, 1, 15, 5, did1))
        val reminders2Old = mapOf(1 to ReviewReminder.createReviewReminder(activity, 10, 30, 1, 15, 10, did2))
        val reminders2New = mapOf(2 to ReviewReminder.createReviewReminder(activity, 10, 45, 1, 15, 10, did2))
        val reminders3New = mapOf(3 to ReviewReminder.createReviewReminder(activity, 11, 0, 1, 15, 25, did3))

        reviewRemindersDatabase.editRemindersForDeck(did1) { reminders1Old }
        reviewRemindersDatabase.editRemindersForDeck(did2) { reminders2Old }

        reviewRemindersDatabase.editAllDeckSpecificReminders { reminders2New + reminders3New }

        val storedReminders1 = reviewRemindersDatabase.getRemindersForDeck(did1)
        val storedReminders2 = reviewRemindersDatabase.getRemindersForDeck(did2)
        val storedReminders3 = reviewRemindersDatabase.getRemindersForDeck(did3)
        val allStoredReminders = reviewRemindersDatabase.getAllDeckSpecificReminders()

        assertThat(storedReminders1, anEmptyMap())
        assertThat(storedReminders2, equalTo(reminders2New))
        assertThat(storedReminders3, equalTo(reminders3New))
        assertThat(
            allStoredReminders,
            equalTo(reminders2New + reminders3New),
        )
    }

    @Test
    fun `getRemindersForDeck should return empty map if JSON string is corrupted`() {
        val did1 = 12345L
        val did2 = 67890L
        val randomObject = Pair("not a map of", "review reminders")

        activity.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, "corrupted_and_invalid_json_string")
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did2, Json.encodeToString(randomObject))
        }
        val reminders1 = reviewRemindersDatabase.getRemindersForDeck(did1)
        val reminders2 = reviewRemindersDatabase.getRemindersForDeck(did2)

        assertThat(reminders1, anEmptyMap())
        assertThat(reminders2, anEmptyMap())
    }

    @Test
    fun `getAllAppWideReminders should return empty map if JSON string is corrupted`() {
        val randomObject = Pair("not a map of", "review reminders")

        activity.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, "corrupted_and_invalid_json_string")
        }
        val reminders1 = reviewRemindersDatabase.getAllAppWideReminders()
        activity.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, Json.encodeToString(randomObject))
        }
        val reminders2 = reviewRemindersDatabase.getAllAppWideReminders()

        assertThat(reminders1, anEmptyMap())
        assertThat(reminders2, anEmptyMap())
    }

    @Test
    fun `getAllDeckSpecificReminder should not return reminders with corrupted JSON strings`() {
        val did1 = 12345L
        val did2 = 67890L
        val did3 = 13579L
        val validReminders =
            mapOf(
                0 to ReviewReminder.createReviewReminder(activity, 9, 0, ReviewReminder.SpecialSnoozeAmounts.DISABLED, 15, 5, did1),
                1 to ReviewReminder.createReviewReminder(activity, 11, 0, ReviewReminder.SpecialSnoozeAmounts.INFINITE, 15, 5, did1),
            )
        val randomObject = Pair("not a map of", "review reminders")

        activity.sharedPrefs().edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(validReminders))
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did2, Json.encodeToString(randomObject))
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did3, "corrupted_and_invalid_json_string")
        }
        val reminders = reviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(reminders, equalTo(validReminders))
    }
}
