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

package com.ichi2.anki.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class AlarmManagerServiceTest : RobolectricTest() {
    companion object {
        private val mockTime = MockTime(2024, 0, 1, 12, 0, 0, 0, 0)
    }

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var reviewReminder: ReviewReminder

    @Before
    override fun setUp() {
        super.setUp()
        context = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        every { context.getSystemService<AlarmManager>() } returns alarmManager
        every { context.getSystemService<NotificationManager>() } returns notificationManager
        reviewReminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
        TimeManager.resetWith(mockTime)
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
        TimeManager.reset()
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `scheduleReviewReminderNotifications calls AlarmManager setWindow`() {
        val expectedSchedulingTime = mockTime.calendar().clone() as Calendar
        expectedSchedulingTime.apply {
            set(Calendar.HOUR_OF_DAY, 20)
        }
        AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                expectedSchedulingTime.timeInMillis,
                10.minutes.inWholeMilliseconds,
                any(),
            )
        }
    }

    @Test
    fun `scheduleReviewReminderNotifications for past time calls AlarmManager setWindow with future time`() {
        val pastTimeReviewReminder =
            ReviewReminder.createReviewReminder(time = ReviewReminderTime(3, 0))
        val expectedSchedulingTime = mockTime.calendar().clone() as Calendar
        expectedSchedulingTime.apply {
            set(Calendar.HOUR_OF_DAY, 3)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        AlarmManagerService.scheduleReviewReminderNotification(context, pastTimeReviewReminder)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                expectedSchedulingTime.timeInMillis,
                10.minutes.inWholeMilliseconds,
                any(),
            )
        }
    }

    @Test
    fun `unscheduleReviewReminderNotifications calls AlarmManager cancel`() {
        mockkStatic(PendingIntentCompat::class)
        val pendingIntent = mockk<PendingIntent>()
        every {
            PendingIntentCompat.getBroadcast(
                any(),
                any(),
                any(),
                PendingIntent.FLAG_UPDATE_CURRENT,
                any(),
            )
        } returns pendingIntent

        AlarmManagerService.unscheduleReviewReminderNotifications(context, reviewReminder)
        verify { alarmManager.cancel(pendingIntent) }
    }

    @Test
    fun `scheduleAllNotifications schedules reminders for all enabled reminders in database`() =
        runTest {
            val did1 = addDeck("Deck1")
            val did2 = addDeck("Deck2")
            val reminder1 = ReviewReminder.createReviewReminder(time = ReviewReminderTime(9, 0))
            val reminder2 = ReviewReminder.createReviewReminder(time = ReviewReminderTime(10, 0))
            val reminder3 = ReviewReminder.createReviewReminder(time = ReviewReminderTime(11, 0))
            val disabledReminder =
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(11, 0),
                    enabled = false,
                )
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reminder1) }
            ReviewRemindersDatabase.editRemindersForDeck(did2) { mapOf(ReviewReminderId(1) to reminder2) }
            ReviewRemindersDatabase.editAllAppWideReminders {
                mapOf(
                    ReviewReminderId(2) to reminder3,
                    ReviewReminderId(3) to disabledReminder,
                )
            }

            AlarmManagerService.scheduleAllNotifications(context)
            verify(exactly = 3) { alarmManager.setWindow(AlarmManager.RTC_WAKEUP, any(), 10.minutes.inWholeMilliseconds, any()) }
        }

    @Test
    fun `onReceive schedules snoozed notification and cancels clicked notification`() {
        val extras = mockk<Bundle>()
        every { extras.getInt(any()) } returns 5
        val intent = mockk<Intent>()
        every { intent.extras } returns extras
        mockkStatic(BundleCompat::class)
        every { BundleCompat.getParcelable(extras, any(), ReviewReminder::class.java) } returns reviewReminder

        AlarmManagerService().onReceive(context, intent)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                mockTime.intTimeMS() + 5.minutes.inWholeMilliseconds,
                10.minutes.inWholeMilliseconds,
                any(),
            )
        }
        verify { notificationManager.cancel(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value) }
    }
}
