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
        activity.sharedPrefs().edit {
            clear()
        }
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
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5, did),
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.PERSISTENT, 10, 30, 10, did),
            )
        reviewRemindersDatabase.editRemindersForDeck(did) { reminders ->
            reminders.addAll(newReminders)
            reminders
        }
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
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5, did1),
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.PERSISTENT, 11, 0, 15, did1),
            )
        val reminders2 =
            listOf(
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.PERSISTENT, 10, 30, 10, did2),
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 12, 30, 20, did2),
            )
        reviewRemindersDatabase.editRemindersForDeck(did1) { reminders ->
            reminders.addAll(reminders1)
            reminders
        }
        reviewRemindersDatabase.editRemindersForDeck(did2) { reminders ->
            reminders.addAll(reminders2)
            reminders
        }
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
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5),
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.PERSISTENT, 10, 30, 10),
            )
        reviewRemindersDatabase.editAllAppWideReminders { reminders ->
            reminders.addAll(newReminders)
            reminders
        }
        val storedReminders = reviewRemindersDatabase.getAllAppWideReminders()
        assertThat(storedReminders, equalTo(newReminders))
    }
}
