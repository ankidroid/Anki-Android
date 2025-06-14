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
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewReminderTest : RobolectricTest() {
    private lateinit var activity: Activity

    @Before
    override fun setUp() {
        super.setUp()
        // We need a valid context to allocate reminder IDs, any valid context will do
        activity = startRegularActivity<AnkiActivity>()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Reset the database after each test
        activity.sharedPrefs().edit { clear() }
    }

    @Test
    fun `getAndIncrementNextFreeReminderId should increment IDs correctly`() {
        for (i in 0..10) {
            val reminder = ReviewReminder.createReviewReminder(activity, 12, 30, ReviewReminder.SpecialSnoozeAmounts.DISABLED, 0, 5)
            assertThat(reminder.id, equalTo(i))
        }
    }
}
