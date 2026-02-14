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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewReminderTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `getAndIncrementNextFreeReminderId should increment IDs correctly`() {
        for (i in 0..10) {
            val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(hour = i, minute = i))
            assertThat(reminder.id, equalTo(ReviewReminderId(i)))
        }
    }
}
