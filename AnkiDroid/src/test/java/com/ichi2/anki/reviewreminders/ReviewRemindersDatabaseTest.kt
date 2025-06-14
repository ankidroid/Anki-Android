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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
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
        // We need a valid context for the database, any valid context will do
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
    fun `getRemindersForDeck should return empty list when no reminders exist`() {
        val did = 12345L
        val reminders = reviewRemindersDatabase.getRemindersForDeck(did)
        assertThat(reminders, empty())
    }

    @Test
    fun `editRemindersForDeck and getRemindersForDeck should read and write reminders correctly`() {
        val did = 12345L
        val newReminders =
            listOf(
                ReviewReminder.createReviewReminder(activity, 9, 0, 2, 15, 5, did),
                ReviewReminder.createReviewReminder(activity, 10, 30, 2, 15, 10, did),
            )

        reviewRemindersDatabase.editRemindersForDeck(did) { newReminders }
        val storedReminders = reviewRemindersDatabase.getRemindersForDeck(did)
        assertThat(storedReminders, equalTo(newReminders))
    }

    @Test
    fun `getAllDeckSpecificReminders should return empty list when no reminders exist`() {
        val reminders = reviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(reminders, empty())
    }

    @Test
    fun `getAllDeckSpecificReminders should return all reminders across decks`() {
        val did1 = 12345L
        val did2 = 67890L
        val reminders1 =
            listOf(
                ReviewReminder.createReviewReminder(activity, 9, 0, ReviewReminder.SpecialSnoozeAmounts.DISABLED, 15, 5, did1),
                ReviewReminder.createReviewReminder(activity, 11, 0, ReviewReminder.SpecialSnoozeAmounts.INFINITE, 15, 5, did1),
            )
        val reminders2 =
            listOf(
                ReviewReminder.createReviewReminder(activity, 10, 30, 2, 15, 10, did2),
                ReviewReminder.createReviewReminder(activity, 12, 30, 3, 10, 20, did2),
            )

        reviewRemindersDatabase.editRemindersForDeck(did1) { reminders1 }
        reviewRemindersDatabase.editRemindersForDeck(did2) { reminders2 }
        val allReminders = reviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(
            allReminders.sortedBy { it.id },
            equalTo((reminders1 + reminders2).sortedBy { it.id }),
        )
    }

    @Test
    fun `getAllAppWideReminders should return empty list when no reminders exist`() {
        val reminders = reviewRemindersDatabase.getAllAppWideReminders()
        assertThat(reminders, empty())
    }

    @Test
    fun `editAllAppWideReminders and getAllAppWideReminders should read and write reminders correctly`() {
        val newReminders =
            listOf(
                ReviewReminder.createReviewReminder(activity, 9, 0, 1, 30, 5),
                ReviewReminder.createReviewReminder(activity, 10, 30, 3, 60, 10),
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
        val reminders1Old = listOf(ReviewReminder.createReviewReminder(activity, 9, 0, 1, 15, 5, did1))
        val reminders2Old = listOf(ReviewReminder.createReviewReminder(activity, 10, 30, 1, 15, 10, did2))
        val reminders2New = listOf(ReviewReminder.createReviewReminder(activity, 10, 45, 1, 15, 10, did2))
        val reminders3New = listOf(ReviewReminder.createReviewReminder(activity, 11, 0, 1, 15, 25, did3))

        reviewRemindersDatabase.editRemindersForDeck(did1) { reminders1Old }
        reviewRemindersDatabase.editRemindersForDeck(did2) { reminders2Old }

        reviewRemindersDatabase.editAllDeckSpecificReminders { reminders2New + reminders3New }

        val storedReminders1 = reviewRemindersDatabase.getRemindersForDeck(did1)
        val storedReminders2 = reviewRemindersDatabase.getRemindersForDeck(did2)
        val storedReminders3 = reviewRemindersDatabase.getRemindersForDeck(did3)
        val allStoredReminders = reviewRemindersDatabase.getAllDeckSpecificReminders()

        assertThat(storedReminders1, empty())
        assertThat(storedReminders2, equalTo(reminders2New))
        assertThat(storedReminders3, equalTo(reminders3New))
        assertThat(
            allStoredReminders.sortedBy { it.id },
            equalTo((reminders2New + reminders3New).sortedBy { it.id }),
        )
    }
}
