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
import anki.scheduler.CardAnswer
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.DeckId
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
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class NotificationServiceTest : RobolectricTest() {
    companion object {
        private val yesterday = MockTime(TimeManager.time.intTimeMS() - 1.days.inWholeMilliseconds)
        private val today = MockTime(TimeManager.time.intTimeMS())
    }

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    override fun setUp() {
        super.setUp()
        TimeManager.resetWith(today)
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

    private fun createAndSaveDummyDeckSpecificReminder(did: DeckId): ReviewReminder {
        val reviewReminder = createTestReminder(deckId = did, thresholdInt = 1)
        ReviewRemindersDatabase.editRemindersForDeck(did) { mapOf(ReviewReminderId(0) to reviewReminder) }
        return reviewReminder
    }

    private fun createAndSaveDummyAppWideReminder(): ReviewReminder {
        val reviewReminder = createTestReminder(thresholdInt = 1)
        ReviewRemindersDatabase.editAllAppWideReminders { mapOf(ReviewReminderId(1) to reviewReminder) }
        return reviewReminder
    }

    private fun triggerDummyReminderNotification(reviewReminder: ReviewReminder) {
        val intent =
            NotificationService.getIntent(
                context,
                reviewReminder,
                NotificationService.NotificationServiceAction.ScheduleRecurringNotifications,
            )
        NotificationService().onReceive(context, intent)
    }

    @Test
    fun `onReceive with less cards than card threshold should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 3)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 3)
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminderDeckSpecific) }
            ReviewRemindersDatabase.editAllAppWideReminders { mapOf(ReviewReminderId(1) to reviewReminderAppWide) }

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminderDeckSpecific) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminderAppWide) }
        }

    @Test
    fun `onReceive with happy path for single deck should fire notification and schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminder = createAndSaveDummyDeckSpecificReminder(did1)

            triggerDummyReminderNotification(reviewReminder)

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
            val reviewReminder = createTestReminder(thresholdInt = 4)

            triggerDummyReminderNotification(reviewReminder)

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
            val reviewReminder = createTestReminder(deckId = 9999)

            triggerDummyReminderNotification(reviewReminder)

            verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder) }
        }

    @Test
    fun `onReceive with reviews today and onlyNotifyIfNoReviews is true should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(1).forEach {
                it.firstCard().update { did = did1 }
            }
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminderDeckSpecific) }
            ReviewRemindersDatabase.editAllAppWideReminders { mapOf(ReviewReminderId(1) to reviewReminderAppWide) }

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)
            verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
        }

    @Test
    fun `onReceive with no reviews ever and onlyNotifyIfNoReviews is true should fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(1).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminderDeckSpecific) }
            ReviewRemindersDatabase.editAllAppWideReminders { mapOf(ReviewReminderId(1) to reviewReminderAppWide) }

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)
            verify(
                exactly = 1,
            ) {
                notificationManager.notify(
                    NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG,
                    reviewReminderDeckSpecific.id.value,
                    any(),
                )
            }
            verify(
                exactly = 1,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminderAppWide.id.value, any()) }
        }

    @Test
    fun `onReceive with review yesterday but none today and onlyNotifyIfNoReviews is true should fire notification`() =
        runTest {
            TimeManager.resetWith(yesterday) // Wind back time and perform the review
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(1).forEach {
                it.firstCard().update { did = did1 }
            }
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            TimeManager.resetWith(today) // Reset time to present

            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.editRemindersForDeck(did1) { mapOf(ReviewReminderId(0) to reviewReminderDeckSpecific) }
            ReviewRemindersDatabase.editAllAppWideReminders { mapOf(ReviewReminderId(1) to reviewReminderAppWide) }

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)
            verify(
                exactly = 1,
            ) {
                notificationManager.notify(
                    NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG,
                    reviewReminderDeckSpecific.id.value,
                    any(),
                )
            }
            verify(
                exactly = 1,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminderAppWide.id.value, any()) }
        }

    @Test
    fun `onReceive with onlyNotifyIfNoReviews is false should always fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(1).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminderDeckSpecific = createAndSaveDummyDeckSpecificReminder(did1)
            val reviewReminderAppWide = createAndSaveDummyAppWideReminder()

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verify(
                exactly = 2,
            ) {
                notificationManager.notify(
                    NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG,
                    reviewReminderDeckSpecific.id.value,
                    any(),
                )
            }
            verify(
                exactly = 2,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminderAppWide.id.value, any()) }
        }

    @Test
    fun `onReceive with blocked collection should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminderDeckSpecific = createAndSaveDummyDeckSpecificReminder(did1)
            val reviewReminderAppWide = createAndSaveDummyAppWideReminder()

            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminderDeckSpecific) }
            verify(
                exactly = 1,
            ) { AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminderAppWide) }
        }

    @Test
    fun `onReceive with snoozed notification should fire notification but not schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true)
            addNotes(2).forEach {
                it.firstCard().update { did = did1 }
            }
            val reviewReminderDeckSpecific = createAndSaveDummyDeckSpecificReminder(did1)
            val reviewReminderAppWide = createAndSaveDummyAppWideReminder()

            val intentDeckSpecific =
                NotificationService.getIntent(
                    context,
                    reviewReminderDeckSpecific,
                    NotificationService.NotificationServiceAction.SnoozeNotification,
                )
            val intentAppWide =
                NotificationService.getIntent(
                    context,
                    reviewReminderAppWide,
                    NotificationService.NotificationServiceAction.SnoozeNotification,
                )
            NotificationService().onReceive(context, intentDeckSpecific)
            NotificationService().onReceive(context, intentAppWide)

            verify(
                exactly = 1,
            ) {
                notificationManager.notify(
                    NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG,
                    reviewReminderDeckSpecific.id.value,
                    any(),
                )
            }
            verify(
                exactly = 1,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminderAppWide.id.value, any()) }
            verify(exactly = 0) { AlarmManagerService.scheduleReviewReminderNotification(context, any()) }
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
            val reviewReminderOne = createTestReminder(deckId = did1, thresholdInt = 1)
            val reviewReminderTwo = createTestReminder(deckId = did2, thresholdInt = 1)
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

    /**
     * Helper method for creating a review reminder to minimize verbosity in this file.
     *
     * @param deckId If specified, the reminder will be deck-specific to this deck ID. If null, it will be app-wide.
     * @param thresholdInt The card trigger threshold as an integer.
     * @param onlyNotifyIfNoReviews Whether the reminder should only notify if there are no reviews today.
     */
    private fun createTestReminder(
        deckId: DeckId? = null,
        thresholdInt: Int = 1,
        onlyNotifyIfNoReviews: Boolean = false,
    ) = ReviewReminder.createReviewReminder(
        time = ReviewReminderTime(hour = 12, minute = 0),
        cardTriggerThreshold = ReviewReminderCardTriggerThreshold(thresholdInt),
        scope = if (deckId != null) ReviewReminderScope.DeckSpecific(deckId) else ReviewReminderScope.Global,
        onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
    )
}
