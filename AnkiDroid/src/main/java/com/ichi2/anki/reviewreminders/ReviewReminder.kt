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

import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.libanki.DeckId
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal typealias ReviewReminderId = Int

/**
 * A "review reminder" is a recurring scheduled notification that reminds the user
 * to review their Anki cards. Individual instances of a review reminder firing and showing up
 * on the user's phone are called "notifications".
 *
 * Below, a public way of creating review reminders is exposed via a companion object so that
 * reminders with invalid IDs are never created. This class is annotated
 * with @ConsistentCopyVisibility to ensure copy() is private too and does not leak the constructor.
 *
 * TODO: add remaining fields planned for GSoC 2025.
 *
 * @param id Unique, auto-incremented ID of the review reminder.
 * @param time See [ReviewReminderTime].
 * @param snoozeAmount See [SnoozeAmount].
 * @param cardTriggerThreshold If, at the time of the reminder, less than this many cards are due, the notification is not triggered.
 * @param did The deck this reminder is associated with, or [APP_WIDE_REMINDER_DECK_ID] if it is an app-wide reminder.
 */
@Serializable
@ConsistentCopyVisibility
data class ReviewReminder private constructor(
    val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val snoozeAmount: SnoozeAmount,
    val cardTriggerThreshold: Int,
    val did: DeckId,
    var enabled: Boolean,
) {
    init {
        require(cardTriggerThreshold >= 0) { "Card trigger threshold must be >= 0" }
    }

    /**
     * The time of day at which reminders will send a notification.
     */
    @Serializable
    data class ReviewReminderTime(
        val hour: Int,
        val minute: Int,
    ) {
        init {
            require(hour in 0..23) { "Hour must be between 0 and 23" }
            require(minute in 0..59) { "Minute must be between 0 and 59" }
        }

        override fun toString(): String =
            LocalTime
                .of(hour, minute)
                .format(
                    DateTimeFormatter
                        .ofLocalizedTime(FormatStyle.SHORT)
                        .withLocale(Locale.getDefault()),
                )

        fun toMinutesFromMidnight(): Int = hour * 60 + minute
    }

    /**
     * Types of snooze behaviour that can be present on notifications sent by review reminders.
     */
    @Serializable
    sealed class SnoozeAmount {
        /**
         * The snooze button will never appear on notifications set by this review reminder.
         */
        @Serializable
        data object Disabled : SnoozeAmount()

        /**
         * The snooze button will always be available on notifications sent by this review reminder.
         */
        @Serializable
        data class Infinite(
            val timeInterval: Duration,
        ) : SnoozeAmount() {
            init {
                require(timeInterval >= 0.minutes) { "Snooze time interval must be >= 0 minutes" }
            }
        }

        /**
         * The snooze button can be pressed a maximum amount of times on notifications sent by this review reminder.
         * After it has been pressed that many times, the button will no longer appear.
         */
        @Serializable
        data class SetAmount(
            val timeInterval: Duration,
            val maxSnoozes: Int,
        ) : SnoozeAmount() {
            init {
                require(timeInterval >= 0.minutes) { "Snooze time interval must be >= 0 minutes" }
                require(maxSnoozes >= 0) { "Max snoozes must be >= 0" }
            }
        }
    }

    companion object {
        /**
         * The "deck ID" field for review reminders that are app-wide rather than deck-specific.
         */
        const val APP_WIDE_REMINDER_DECK_ID = -1L

        /**
         * IDs start at this value and climb upwards by one each time.
         */
        private const val FIRST_REMINDER_ID = 0

        /**
         * Create a new review reminder. This will allocate a new ID for the reminder.
         * @return A new [ReviewReminder] object.
         * @see [ReviewReminder]
         */
        fun createReviewReminder(
            time: ReviewReminderTime,
            snoozeAmount: SnoozeAmount,
            cardTriggerThreshold: Int,
            did: DeckId = APP_WIDE_REMINDER_DECK_ID,
            enabled: Boolean = true,
        ) = ReviewReminder(
            id = getAndIncrementNextFreeReminderId(),
            time,
            snoozeAmount,
            cardTriggerThreshold,
            did,
            enabled,
        )

        /**
         * Get and return the next free reminder ID which can be associated with a new review reminder.
         * Also increment the next free reminder ID stored in SharedPreferences.
         * Since there are 4 billion IDs available, this should not overflow in practice.
         * @return The next free reminder ID.
         */
        private fun getAndIncrementNextFreeReminderId(): ReviewReminderId {
            val nextFreeId = Prefs.getInt(R.string.review_reminders_next_free_id, FIRST_REMINDER_ID)
            Prefs.putInt(R.string.review_reminders_next_free_id, nextFreeId + 1)
            Timber.d("Generated next free review reminder ID: $nextFreeId")
            return nextFreeId
        }
    }
}
