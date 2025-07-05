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

import android.os.Parcelable
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.settings.Prefs
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@JvmInline
@Serializable
value class ReviewReminderId(
    val id: Int,
) {
    companion object {
        /**
         * Get and return the next free reminder ID which can be associated with a new review reminder.
         * Also increment the next free reminder ID stored in SharedPreferences.
         * @return The next free reminder ID.
         */
        fun getAndIncrementNextFreeReminderId(): ReviewReminderId {
            val nextFreeId = Prefs.reviewReminderNextFreeId
            Prefs.reviewReminderNextFreeId = nextFreeId + 1
            Timber.d("Generated next free review reminder ID: $nextFreeId")
            return ReviewReminderId(nextFreeId)
        }
    }
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

    fun toSecondsFromMidnight(): Long = (hour.hours + minute.minutes).inWholeSeconds
}

/**
 * Types of snooze behaviour that can be present on notifications sent by review reminders.
 * If a reminder is snoozed repeatedly until it overlaps with another notification from the reminder firing the next day,
 * the snoozing instance of the reminder from the previous day will be cancelled.
 */
@Serializable
sealed class ReviewReminderSnoozeAmount {
    /**
     * The snooze button will never appear on notifications set by this review reminder.
     */
    @Serializable
    data object Disabled : ReviewReminderSnoozeAmount()

    /**
     * The snooze button will always be available on notifications sent by this review reminder.
     */
    @Serializable
    data class Infinite(
        val interval: Duration,
    ) : ReviewReminderSnoozeAmount() {
        init {
            require(interval >= 1.minutes) { "Snooze time interval must be >= 1 minute" }
            require(interval < 24.hours) { "Snooze time interval must be < 24 hours" }
        }
    }

    /**
     * The snooze button can be pressed a maximum amount of times on notifications sent by this review reminder.
     * After it has been pressed that many times, the button will no longer appear.
     */
    @Serializable
    data class SetAmount(
        val interval: Duration,
        val maxSnoozes: Int,
    ) : ReviewReminderSnoozeAmount() {
        init {
            require(interval >= 1.minutes) { "Snooze time interval must be >= 1 minute" }
            require(interval < 24.hours) { "Snooze time interval must be < 24 hours" }
            require(maxSnoozes >= 1) { "Max snoozes must be >= 1" }
        }
    }
}

/**
 * If, at the time of the reminder, less than this many cards are due, the notification is not triggered.
 */
@JvmInline
@Serializable
value class ReviewReminderCardTriggerThreshold(
    val threshold: Int,
) {
    init {
        require(threshold >= 0) { "Card trigger threshold must be >= 0" }
    }
}

/**
 * An indicator of whether a review reminders feature is associated with every deck in the user's
 * collection or if it is associated with a single deck. For example, the [ScheduleReminders] fragment
 * can be triggered in either global or deck-specific editing mode. A [ReviewReminder] can be associated
 * with either all decks or a specific deck.
 *
 * This class is marked with @Parcelize so that it can be passed into [ScheduleReminders.getIntent].
 * This class is marked with @Serializable so that it can be a field of [ReviewReminder]s, which are stored as JSON strings.
 */
@Serializable
@Parcelize
sealed class ReviewReminderScope : Parcelable {
    /**
     * This represents all decks in the user's collection.
     */
    @Serializable
    data object Global : ReviewReminderScope()

    /**
     * This represents a specific deck in the user's collection.
     */
    @Serializable
    data class DeckSpecific(
        val did: DeckId,
    ) : ReviewReminderScope() {
        @IgnoredOnParcel
        private var cachedDeckName: String? = null

        /**
         * Gets the deck name associated with this [DeckSpecific] review reminder's [did] from the collection.
         * Caches the resultant deck name to minimize calls to the collection.
         * Should not be called if [did] is no longer a valid deck ID. If [did] is invalid, this method will return "[no deck]".
         */
        suspend fun getDeckName(): String = cachedDeckName ?: withCol { decks.name(did) }.also { cachedDeckName = it }
    }
}

/**
 * A "review reminder" is a recurring scheduled notification that reminds the user
 * to review their Anki cards. Individual instances of a review reminder firing and showing up
 * on the user's phone are called "notifications".
 *
 * Below, a public way of creating review reminders is exposed via a companion object so that
 * reminders with invalid IDs are never created. This class is annotated
 * with @ConsistentCopyVisibility to ensure copy() is private too and does not leak the constructor.
 *
 * About the old schema migration process:
 *
 * To any developer who changes this class in the future, note that these review reminders are stored
 * by [ReviewRemindersDatabase] inside SharedPreferences. Modifying this schema means that existing
 * stored review reminders on user devices will no longer be able to be read, as decoding them to the new
 * [ReviewReminder] schema will cause a serialization exception.
 * You must specify a schema migration mapping for users who already have review reminders set on their devices
 * so that [ReviewRemindersDatabase.attemptSchemaMigration] can migrate their reminders to the new schema.
 * Use an [OldReviewReminderSchema] to store the old schema and to define a method for migrating to the new schema.
 * Your method will be called from [ScheduleReminders.catchDatabaseExceptions]. To inform [ScheduleReminders.catchDatabaseExceptions]
 * that some users may have review reminders in the form of your old schema, add your [OldReviewReminderSchema]
 * to [ScheduleReminders.oldReviewReminderSchemasForMigration]. We store a list of old schemas since there may be
 * multiple old schemas, and users do not always update their app from
 * version A -> B -> C but may sometimes jump from A -> C.
 * [ScheduleReminders.catchDatabaseExceptions] will attempt to migrate from all old schemas present in the list.
 *
 * TODO: add remaining fields planned for GSoC 2025.
 *
 * @param id Unique, auto-incremented ID of the review reminder.
 * @param time See [ReviewReminderTime].
 * @param snoozeAmount See [ReviewReminderSnoozeAmount].
 * @param cardTriggerThreshold See [ReviewReminderCardTriggerThreshold].
 * @param scope See [ReviewReminderScope].
 * @param enabled Whether the review reminder's notifications are active or disabled.
 */
@Serializable
@ConsistentCopyVisibility
data class ReviewReminder private constructor(
    val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val snoozeAmount: ReviewReminderSnoozeAmount,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderScope,
    var enabled: Boolean,
) {
    companion object {
        /**
         * Create a new review reminder. This will allocate a new ID for the reminder.
         * @return A new [ReviewReminder] object.
         * @see [ReviewReminder]
         */
        fun createReviewReminder(
            time: ReviewReminderTime,
            snoozeAmount: ReviewReminderSnoozeAmount,
            cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
            scope: ReviewReminderScope = ReviewReminderScope.Global,
            enabled: Boolean = true,
        ) = ReviewReminder(
            id = ReviewReminderId.getAndIncrementNextFreeReminderId(),
            time,
            snoozeAmount,
            cardTriggerThreshold,
            scope,
            enabled,
        )
    }
}
