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
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRemindersDatabaseTest : RobolectricTest() {
    private lateinit var activity: Activity
    private lateinit var reviewRemindersDatabase: ReviewRemindersDatabase

    @Before
    override fun setUp() {
        super.setUp()
        // We need a valid context for the database, any valid context will do
        activity = startRegularActivity<DeckPicker>()
        reviewRemindersDatabase = ReviewRemindersDatabase(activity)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Clean up the database after each test
        runBlocking {
            activity.dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    @Test
    fun `getRemindersForDeck should return empty list when no reminders exist`() =
        runTest {
            val did = 12345L
            val reminders = reviewRemindersDatabase.getRemindersForDeck(did)
            assert(reminders.isEmpty())
        }

    @Test
    fun `setRemindersForDeck and getRemindersForDeck should read and write reminders correctly`() =
        runTest {
            val did = 12345L
            val reminders =
                listOf(
                    ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5),
                    ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.PERSISTENT, 10, 30, 10),
                )
            reviewRemindersDatabase.setRemindersForDeck(did, reminders)

            val storedReminders = reviewRemindersDatabase.getRemindersForDeck(did)
            assert(storedReminders == reminders)
        }

    @Test
    fun `getAppWideReminders should return empty list when no reminders exist`() =
        runTest {
            val reminders = reviewRemindersDatabase.getAppWideReminders()
            assert(reminders.isEmpty())
        }

    @Test
    fun `setAppWideReminders and getAppWideReminders should read and write reminders correctly`() =
        runTest {
            val reminders =
                listOf(
                    ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5),
                    ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.PERSISTENT, 10, 30, 10),
                )
            reviewRemindersDatabase.setAppWideReminders(reminders)

            val storedReminders = reviewRemindersDatabase.getAppWideReminders()
            assert(storedReminders == reminders)
        }

    @Test
    fun `allocateReminderId should return successive IDs`() =
        runTest {
            for (i in 0..4) {
                assert(i == reviewRemindersDatabase.allocateReminderId())
            }
        }

    @Test
    fun `deallocateReminderId should allow allocateReminderId to reuse IDs`() =
        runTest {
            val id = reviewRemindersDatabase.allocateReminderId()
            reviewRemindersDatabase.deallocateReminderId(id)
            assert(id == reviewRemindersDatabase.allocateReminderId())
        }

    @Test
    fun `addReviewReminderForDeck should add a reminder to the deck`() =
        runTest {
            val did = 12345L
            val reminder = ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5)
            reviewRemindersDatabase.addReviewReminderForDeck(did, reminder)

            val reminders = reviewRemindersDatabase.getRemindersForDeck(did)
            assert(reminders.size == 1)
            assert(reminders.contains(reminder))
        }

    @Test
    fun `editReviewReminderForDeck should edit an existing reminder`() =
        runTest {
            val did = 12345L
            reviewRemindersDatabase.addReviewReminderForDeck(
                did,
                ReviewReminder.createReviewReminder(
                    activity,
                    ReviewReminderTypes.SINGLE,
                    0,
                    9,
                    0,
                ),
            )
            val updatedReminder =
                ReviewReminder.createReviewReminder(
                    activity,
                    ReviewReminderTypes.PERSISTENT,
                    10,
                    30,
                    5,
                )
            reviewRemindersDatabase.editReviewReminderForDeck(
                did,
                0,
                updatedReminder,
            )

            val reminders = reviewRemindersDatabase.getRemindersForDeck(did)
            assert(reminders.size == 1)
            assert(reminders.contains(updatedReminder))
        }

    @Test
    fun `editReviewReminderForDeck with a nonexistent id should throw an IllegalArgumentException`() =
        runTest {
            val did = 12345L
            assertThrows<IllegalArgumentException> {
                reviewRemindersDatabase.editReviewReminderForDeck(
                    did,
                    0,
                    ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5),
                )
            }
        }

    @Test
    fun `deleteReviewReminderForDeck should delete an existing reminder`() =
        runTest {
            val did = 12345L
            reviewRemindersDatabase.addReviewReminderForDeck(
                did,
                ReviewReminder.createReviewReminder(activity, ReviewReminderTypes.SINGLE, 9, 0, 5),
            )
            reviewRemindersDatabase.deleteReviewReminderForDeck(did, 0)

            val reminders = reviewRemindersDatabase.getRemindersForDeck(did)
            assert(reminders.isEmpty())
        }

    @Test
    fun `deleteReviewReminderForDeck with a nonexistent id should throw an IllegalArgumentException`() =
        runTest {
            val did = 12345L
            assertThrows<IllegalArgumentException> {
                reviewRemindersDatabase.deleteReviewReminderForDeck(did, 0)
            }
        }
}
