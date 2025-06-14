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

import android.content.Context
import androidx.core.content.edit
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.libanki.DeckId
import kotlinx.serialization.Serializable

internal typealias ReviewReminderId = Int
internal typealias SnoozeAmount = Int

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
 * @param hour
 * @param minute
 * @param snoozeAmountAllowed Amount of times the user can hit snooze on the notification. See [SpecialSnoozeAmounts] for distinguished values.
 * @param snoozeTimeInterval Time interval between snoozes, in minutes. Unused if snoozesAllowed is a [SpecialSnoozeAmounts] value.
 * @param cardTriggerThreshold If, at the time of the reminder, less than this many cards are due, the notification is not triggered.
 * @param did The deck this reminder is associated with, or -1 if it is an app-wide reminder.
 */
@Serializable
@ConsistentCopyVisibility
data class ReviewReminder private constructor(
    val id: ReviewReminderId,
    val hour: Int,
    val minute: Int,
    val snoozeAmountAllowed: SnoozeAmount,
    val snoozeTimeInterval: Int,
    val cardTriggerThreshold: Int,
    val did: DeckId,
) {
    init {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(snoozeTimeInterval >= 0) { "Snooze time interval must be >= 0" }
        require(cardTriggerThreshold >= 0) { "Card trigger threshold must be >= 0" }
    }

    /**
     * Distinguished snooze amount constants.
     * - [INFINITE]: The snooze button will always be available on notifications sent by this review reminder.
     * - [DISABLED]: The snooze button will never appear on notifications set by this review reminder.
     * @see SnoozeAmount
     */
    object SpecialSnoozeAmounts {
        const val INFINITE: SnoozeAmount = -1
        const val DISABLED: SnoozeAmount = 0
    }

    companion object {
        /**
         * IDs start at this value and climb upwards by one each time.
         */
        private const val FIRST_REMINDER_ID = 0

        /**
         * Key in SharedPreferences for the next free reminder ID, with its value as an Int.
         */
        private const val NEXT_FREE_ID_KEY = "review_reminders_next_free_id"

        /**
         * Create a new review reminder. This will allocate a new ID for the reminder.
         * @return A new [ReviewReminder] object.
         * @see [ReviewReminder]
         */
        fun createReviewReminder(
            context: Context,
            hour: Int,
            minute: Int,
            snoozeAmountAllowed: SnoozeAmount,
            snoozeTimeInterval: Int,
            cardTriggerThreshold: Int,
            did: DeckId = -1L,
        ) = ReviewReminder(
            id = getAndIncrementNextFreeReminderId(context),
            hour = hour,
            minute = minute,
            snoozeAmountAllowed = snoozeAmountAllowed,
            snoozeTimeInterval = snoozeTimeInterval,
            cardTriggerThreshold = cardTriggerThreshold,
            did = did,
        )

        /**
         * Get and return the next free reminder ID which can be associated with a new review reminder.
         * Also increment the next free reminder ID stored in SharedPreferences.
         * Since there are 4 billion IDs available, this should not overflow in practice.
         */
        private fun getAndIncrementNextFreeReminderId(context: Context): ReviewReminderId {
            val nextFreeId =
                context.sharedPrefs().getInt(
                    NEXT_FREE_ID_KEY,
                    FIRST_REMINDER_ID,
                )
            context.sharedPrefs().edit {
                putInt(NEXT_FREE_ID_KEY, nextFreeId + 1)
            }
            return nextFreeId
        }
    }
}
