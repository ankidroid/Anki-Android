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

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderCardTriggerThreshold
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.settings.Prefs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationServiceTest : RobolectricTest() {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    override fun setUp() {
        super.setUp()
        context = spyk(getApplicationContext())
        notificationManager = mockk(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        mockkObject(AlarmManagerService)
        every { AlarmManagerService.scheduleReviewReminderNotification(any(), any()) } returns Unit
        Prefs.newReviewRemindersEnabled = true
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `onReceive with less cards than card threshold should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck")
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(3),
                    ReviewReminderScope.DeckSpecific(did1),
                )
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminder) }

            val intent =
                NotificationService.getIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            NotificationService().onReceive(context, intent)

            verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder) }
        }

    @Test
    fun `onReceive with happy path for single deck should fire notification and schedule next`() =
        runTest {
            val did1 = addDeck("Deck")
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(1),
                    ReviewReminderScope.DeckSpecific(did1),
                )
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminder) }

            val intent =
                NotificationService.getIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            NotificationService().onReceive(context, intent)

            verify(
                exactly = 1,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value, any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder) }
        }

    @Test
    fun `onReceive with happy path for global reminder should fire notification and schedule next`() =
        runTest {
            val did1 = addDeck("Deck1")
            val did2 = addDeck("Deck2")
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            addNotes(2).forEach {
                it.firstCard().update { did = did2 }
            }
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(4),
                    ReviewReminderScope.Global,
                )

            val intent =
                NotificationService.getIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            NotificationService().onReceive(context, intent)
            verify(
                exactly = 1,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value, any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder) }
        }

    @Test
    fun `onReceive with non-existent deck should not fire notification but schedule next`() =
        runTest {
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(1),
                    ReviewReminderScope.DeckSpecific(9999),
                )

            val intent =
                NotificationService.getIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            NotificationService().onReceive(context, intent)

            verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder) }
        }

    @Test
    fun `onReceive with blocked collection should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck")
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(1),
                    ReviewReminderScope.DeckSpecific(did1),
                )
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminder) }

            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            val intent =
                NotificationService.getIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            NotificationService().onReceive(context, intent)

            verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder) }
        }

    @Test
    fun `onReceive with snoozed notification should fire notification but not schedule next`() =
        runTest {
            val did1 = addDeck("Deck")
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(1),
                    ReviewReminderScope.DeckSpecific(did1),
                )
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminder) }

            val intent =
                NotificationService.getIntent(
                    context,
                    reviewReminder,
                    NotificationService.NotificationServiceAction.SnoozeNotification,
                )
            NotificationService().onReceive(context, intent)

            verify(
                exactly = 1,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value, any()) }
            verify(exactly = 0) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder) }
        }

    @Test
    fun `snooze actions of different notifications and different intervals should be different`() =
        runTest {
            val did1 = addDeck("Deck1")
            val did2 = addDeck("Deck2")
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            addNotes(2).forEach {
                it.firstCard().update { did = did2 }
            }
            val reviewReminderOne =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(1),
                    ReviewReminderScope.DeckSpecific(did1),
                )
            val reviewReminderTwo =
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(1),
                    ReviewReminderScope.DeckSpecific(did2),
                )
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminderOne) }
            ReviewRemindersDatabase.editRemindersForDeck(did2) { mapOf(ReviewReminderId(1) to reviewReminderTwo) }

            val slotOne = slot<Notification>()
            val slotTwo = slot<Notification>()

            val intentOne =
                NotificationService.getIntent(
                    context,
                    reviewReminderOne,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            NotificationService().onReceive(context, intentOne)

            val intentTwo =
                NotificationService.getIntent(
                    context,
                    reviewReminderTwo,
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
                )
            NotificationService().onReceive(context, intentTwo)

            verify(
                exactly = 1,
            ) {
                notificationManager.notify(
                    NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG,
                    reviewReminderOne.id.value,
                    capture(slotOne),
                )
            }
            verify(
                exactly = 1,
            ) {
                notificationManager.notify(
                    NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG,
                    reviewReminderTwo.id.value,
                    capture(slotTwo),
                )
            }

            val snoozeIntents =
                setOf(
                    slotOne.captured.actions[0].actionIntent,
                    slotOne.captured.actions[1].actionIntent,
                    slotTwo.captured.actions[0].actionIntent,
                    slotTwo.captured.actions[1].actionIntent,
                )
            assertThat(snoozeIntents.size, equalTo(4))
        }
}
