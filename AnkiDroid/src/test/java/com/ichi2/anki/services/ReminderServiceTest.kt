/*
 *  Copyright (c) 2021 Mike Hardy <github@mikehardy.net>
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
package com.ichi2.anki.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(AndroidJUnit4::class)
class ReminderServiceTest : RobolectricTest() {
    @Test
    fun testReminderServiceNothingDue() {
        buildDefaultDeckReminders()
        assertThat("No notifications exist", notificationManagerShadow.size(), equalTo(0))
    }

    @Test
    fun testReminderServiceReviewsDue() {
        addNoteUsingBasicModel("test front", "test back")
        assertThat("No notifications exist", notificationManagerShadow.size(), equalTo(0))
        buildDefaultDeckReminders()
        assertThat("No notifications exist", notificationManagerShadow.size(), equalTo(1))
    }

    @Test
    fun testReminderServiceNullCollection() {
        addNoteUsingBasicModel("test front", "test back")
        enableNullCollection()
        assertThat("No notifications exist", notificationManagerShadow.size(), equalTo(0))
        buildDefaultDeckReminders()
        // The collection was null so no reminders, but we should get here without exception
        assertThat("No notifications exist", notificationManagerShadow.size(), equalTo(0))
    }

    /**
     * #8264: Crash on sync - getSched().getDueTree() failed
     */
    @Test
    fun testDatabaseFailureWhileSyncingDoesNotCrash() {
        // If getCol() fails, it triggers different exception handling in the service.
        // The cause was getSched().deckDueTree(col, )
        val baseCol = col
        val mockCol = spy(baseCol)

        whenever(mockCol.sched).thenThrow(IllegalStateException("Unit test: simulating database exception"))

        CollectionHelper.instance.setColForTests(mockCol)

        buildDefaultDeckReminders()

        // We retry after a database timeout so getSched is called twice
        verify(mockCol, times(2)).sched
    }

    private val notificationManagerShadow: ShadowNotificationManager
        get() = Shadows.shadowOf(targetContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    private fun buildDefaultDeckReminders() {
        val defaultDeckIntent = Intent()
        defaultDeckIntent.putExtra(ReminderService.EXTRA_DECK_ID, 1L)
        defaultDeckIntent.putExtra(ReminderService.EXTRA_DECK_OPTION_ID, 1L)
        val reminders = ReminderService()
        reminders.onReceive(targetContext, defaultDeckIntent)
    }
}
