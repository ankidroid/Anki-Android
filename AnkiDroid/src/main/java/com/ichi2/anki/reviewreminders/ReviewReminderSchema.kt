// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase.StoredReviewReminderGroup
import kotlinx.serialization.Serializable

/**
 * Inline value class for review reminder schema versions.
 * @see [StoredReviewReminderGroup]
 * @see [ReviewReminder]
 */
@JvmInline
@Serializable
value class ReviewReminderSchemaVersion(
    val value: Int,
) {
    init {
        require(value >= 1) { "Review reminder schema version must be >= 1" }
        // We do not check that it is <= SCHEMA_VERSION here because then declaring SCHEMA_VERSION would be circular
    }
}

/**
 * When [ReviewReminder] is updated by a developer, implement this interface in a new data class which
 * has the same fields as the old version of [ReviewReminder], then implement the [migrate] method which
 * transforms old [ReviewReminder]s to new [ReviewReminder]s. Also ensure that the previous [ReviewReminderSchema]
 * in the migration version chain ([ReviewRemindersDatabase.oldReviewReminderSchemasForMigration]) has its [migrate] method
 * edited to return instances of the newly-created schema. Then, increment [ReviewRemindersDatabase.schemaVersion].
 *
 * Data classes implementing this interface should be marked as @Serializable. Any new types defined for ReviewReminderSchemas
 * should also be marked as @Serializable.
 *
 * Note that if a migration involves renaming a data class field, you need to maintain a namespaced version of the old data class
 * so that the serializer can distinguish between the old and new data classes. For instance, if you are renaming
 * `ReviewReminderTime.timeHour` to `ReviewReminderTime.hour`, create the following:
 *
 * ```kotlin
 * object OldDataClasses {
 *     @Serializable
 *     data class ReviewReminderTime(
 *         val timeHour: Int,
 *         val timeMinute: Int,
 *     )
 * }
 * ```
 *
 * This class will be serialized into "ReviewReminderTime(timeHour=#, timeMinute=#)", and is nested because it will otherwise conflict
 * with the updated definition of [ReviewReminderTime], which is serialized as "ReviewReminderTime(hour=#, minute=#)".
 * When we read the outdated schema from the disk, we need to tell the deserializer that it is reading a
 * OldDataClasses.ReviewReminderTime rather than a [ReviewReminderTime], even though the class names are the same.
 * With the above in place, you can then use this namespaced data class to construct the new schema in [migrate]:
 *
 * ```kotlin
 * time = ReviewReminderTime(this.time.timeHour, this.time.timeMinute)
 * ```
 *
 * @see [ReviewRemindersDatabase.performSchemaMigration].
 * @see [ReviewReminder]
 */
@Serializable
sealed interface ReviewReminderSchema {
    /**
     * All review reminders must have an identifying ID.
     * This is necessary to facilitate migrations. See the implementation of [ReviewRemindersDatabase.performSchemaMigration] for details.
     */
    val id: ReviewReminderId

    /**
     * Transforms this [ReviewReminderSchema] to the next version of the [ReviewReminderSchema]. For example, if an old schema used `did`
     * to define its scope, but the new schema uses a scope data class, the field might have an override like the following:
     *
     * ```kotlin
     * scope = if (this.did == -1L) ReviewReminderScope.Global else ReviewReminderScope.DeckSpecific(this.did)
     * ```
     */
    fun migrate(): ReviewReminderSchema
}

/**
 * Version 1 of [ReviewReminderSchema]. Updated to Version 2 by adding [ReviewReminder.onlyNotifyIfNoReviews].
 */
@Serializable
data class ReviewReminderSchemaV1(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderScope,
    var enabled: Boolean,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean = false,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminderSchemaV2 =
        ReviewReminderSchemaV2(
            id = id,
            time = time,
            cardTriggerThreshold = cardTriggerThreshold,
            scope = scope,
            enabled = enabled,
            profileID = profileID,
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
        )
}

/**
 * Version 2 of [ReviewReminderSchema]. Updated to Version 3 by adding [ReviewReminder.latestNotifTime].
 */
@Serializable
data class ReviewReminderSchemaV2(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderScope,
    var enabled: Boolean,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminder = ReviewReminder.createViaMigration(this)
}
