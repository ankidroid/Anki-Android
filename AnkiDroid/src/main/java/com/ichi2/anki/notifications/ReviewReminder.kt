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

package com.ichi2.anki.notifications

import android.content.Context
import kotlinx.serialization.Serializable

/**
 * Review reminder data class. Handles the fields of a review reminders and the logic behind creating one.
 * Below, a public way of creating [ReviewReminder] is exposed via a companion object so that IDs are not abused.
 * Annotated with @ConsistentCopyVisibility to ensure copy() is private too and does not leak the constructor.
 * TODO: add remaining fields planned for GSoC 2025.
 */
@Serializable
@ConsistentCopyVisibility
data class ReviewReminder private constructor(
    val id: Int,
    val type: Int, // ReviewReminderTypes ordinal
    val hour: Int,
    val minute: Int,
    val cardTriggerThreshold: Int,
) {
    init {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        require(cardTriggerThreshold >= 0) { "Card trigger threshold must be >= 0" }
    }

    companion object {
        /**
         * Create a new review reminder. This will allocate a new ID for the reminder.
         * @param context The context to use for Datastore database access.
         * @param type The type of the reminder.
         * @param hour The hour of the reminder (0-23).
         * @param minute The minute of the reminder (0-59).
         * @param cardTriggerThreshold The card trigger threshold.
         * @return A new ReviewReminder object.
         */
        suspend fun createReviewReminder(
            context: Context,
            type: ReviewReminderTypes,
            hour: Int,
            minute: Int,
            cardTriggerThreshold: Int,
        ) = ReviewReminder(
            id = ReviewRemindersDatabase(context).allocateReminderId(),
            type = type.ordinal,
            hour = hour,
            minute = minute,
            cardTriggerThreshold = cardTriggerThreshold,
        )
    }
}

/**
 * Types of review reminders.
 * GSoC 2025: Two kinds planned: single and persistent.
 * Possibly extensible in the future.
 */
enum class ReviewReminderTypes {
    SINGLE,
    PERSISTENT,
}
