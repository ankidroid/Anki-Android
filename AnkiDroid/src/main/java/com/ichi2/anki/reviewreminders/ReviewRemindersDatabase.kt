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
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.libanki.DeckId
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import timber.log.Timber
import kotlin.reflect.KClass

/**
 * Manages the storage and retrieval of [ReviewReminder]s in SharedPreferences.
 *
 * [ReviewReminder]s can either be tied to a specific deck and trigger based on the number of cards
 * due in that deck, or they can be app-wide reminders that trigger based on the total number
 * of cards due across all decks. See [ReviewReminderScope].
 */
object ReviewRemindersDatabase {
    /**
     * Profile ID for the review reminder SharedPreferences file key. Each profile for using AnkiDroid will have its own review reminders stored
     * in its own SharedPreferences file. This ID is appended onto the end of [SHARED_PREFS_FILE_KEY] to create a unique file name for each profile.
     *
     * Currently, this is hard-coded as 0. When multi-profile functionality is added to AnkiDroid, make sure this value is dynamically set
     * to the current profile ID. Also ensure that the entire review reminders system is updated to work with the multi-profile system.
     * For example, scheduled notifications may need to be cancelled and rescheduled when the user toggles between profiles.
     */
    private const val PROFILE_ID: Int = 0

    /**
     * SharedPreferences file name key for review reminders. We store the review reminders separately from the default SharedPreferences.
     */
    private const val SHARED_PREFS_FILE_KEY = "com.ichi2.anki.REVIEW_REMINDERS_SHARED_PREFS_$PROFILE_ID"

    /**
     * SharedPreferences file for review reminders. We store the review reminders separately from the default SharedPreferences.
     */
    @VisibleForTesting
    val remindersSharedPrefs: SharedPreferences =
        AnkiDroidApp.instance.getSharedPreferences(
            SHARED_PREFS_FILE_KEY,
            Context.MODE_PRIVATE,
        )

    /**
     * Key in SharedPreferences for retrieving deck-specific reminders.
     * Should have deck ID appended to its end, ex. "deck_12345".
     * Its value is a [ReviewReminderGroup] serialized as a JSON String.
     */
    @VisibleForTesting
    const val DECK_SPECIFIC_KEY = "deck_"

    /**
     * Key in SharedPreferences for retrieving app-wide reminders.
     * Its value is a [ReviewReminderGroup] serialized as a JSON String.
     */
    @VisibleForTesting
    const val APP_WIDE_KEY = "app_wide"

    /**
     * The form in which [ReviewReminderGroup] are actually written to SharedPreferences.
     * This allows us to check the version of [ReviewReminder] stored before trying to deserialize the JSON string,
     * allowing us to carefully handle schema migration. Otherwise, if an older version of [ReviewReminder] is encoded
     * and we try to decode it into a newer form of [ReviewReminder], an error will be thrown.
     *
     * We assume that the version is accurate; e.x. if the version is 3, then the [ReviewReminder] stored is indeed
     * of schema version 3. This should be safe to assume since writing this data class to SharedPreferences is an
     * atomic operation.
     *
     * @param version The version of the [remindersMapJson] which is stored.
     * @param remindersMapJson The stored [ReviewReminderGroup]'s underlying hashmap as a serialized JSON string.
     */
    @Serializable
    @VisibleForTesting
    data class StoredReviewReminderGroup(
        val version: ReviewReminderSchemaVersion,
        val remindersMapJson: String,
    )

    /**
     * Current [ReviewReminder] schema version. Whenever [ReviewReminder] is modified, this integer MUST be incremented.
     *
     * Version 1: 3 August 2025 - Initial version
     * Version 2: 25 January 2026 - Added [ReviewReminder.onlyNotifyIfNoReviews]
     *
     * @see [oldReviewReminderSchemasForMigration]
     * @see [ReviewReminder]
     */
    @VisibleForTesting
    var schemaVersion = ReviewReminderSchemaVersion(2)

    /**
     * A map of all old [ReviewReminderSchema]s that [ReviewRemindersDatabase.performSchemaMigration] will attempt to migrate old
     * review reminders in SharedPreferences from. Migration occurs from version 1 to version 2, from version 2 to version 3, etc.
     *
     * When [ReviewReminder] is updated, you MUST add a new migration version and keep the old schema
     * as a class that implements [ReviewReminderSchema]. Ensure the latest schema version in this map
     * always maps to [ReviewReminder].
     *
     * @see [schemaVersion]
     * @see [ReviewReminderSchema]
     * @see [ReviewReminder]
     */
    @VisibleForTesting
    var oldReviewReminderSchemasForMigration: Map<ReviewReminderSchemaVersion, KClass<out ReviewReminderSchema>> =
        mapOf(
            ReviewReminderSchemaVersion(1) to ReviewReminderSchemaV1::class,
            ReviewReminderSchemaVersion(2) to ReviewReminder::class, // Most up to date version
        )

    /**
     * Schema update method for migrating old review reminders to new ones.
     * This is run when [ReviewReminder] is updated and existing users who already have review reminders set up on their devices
     * need to have their data ported to the new schema.
     * Versions are declared in [oldReviewReminderSchemasForMigration].
     *
     * We need to opt into an experimental serialization API feature because we are determining classes to deserialize
     * dynamically via [oldReviewReminderSchemasForMigration] rather than at compile-time.
     * The possible schemas to deserialize from are inputted dynamically so that unit tests are possible.
     *
     * @param encodedReviewReminderKey The key with which the [encodedReviewReminderGroup] is stored in SharedPreferences,
     * used for writing the migrated map back into SharedPreferences.
     * @param encodedReviewReminderGroup The encoded review reminders map to migrate.
     * @param fromVersion The schema version of [encodedReviewReminderGroup].
     * @param toVersion The schema version of the new review reminders map.
     *
     * @throws SerializationException If the [fromVersion] is less than 1 or greater than [schemaVersion], or if the
     * [encodedReviewReminderGroup] is not a valid JSON string, or if the final result of migration is somehow not a [ReviewReminder].
     * @throws IllegalArgumentException If the [encodedReviewReminderGroup] is not actually of version [fromVersion],
     * or if the [fromVersion] is not in [oldReviewReminderSchemasForMigration].
     *
     * @see [ReviewReminder]
     */
    @OptIn(InternalSerializationApi::class)
    private fun performSchemaMigration(
        encodedReviewReminderKey: String,
        encodedReviewReminderGroup: String,
        fromVersion: ReviewReminderSchemaVersion,
        toVersion: ReviewReminderSchemaVersion = schemaVersion,
    ): ReviewReminderGroup {
        Timber.i("Beginning migration from $fromVersion to $toVersion")
        if (fromVersion.value < 1 ||
            fromVersion.value > toVersion.value
        ) {
            throw SerializationException("Invalid review reminder schema version: $fromVersion")
        }

        // Deserialize from old schema
        val oldSchema =
            oldReviewReminderSchemasForMigration[fromVersion]
                ?: throw IllegalArgumentException("Review reminder schema version not found: $fromVersion")
        val mapDeserializer = MapSerializer(ReviewReminderId.serializer(), oldSchema.serializer())
        val mapDecoded = Json.decodeFromString(mapDeserializer, encodedReviewReminderGroup)

        // Migrate step by step
        var currentMap = mapDecoded
        var currentVersion = fromVersion.value
        while (currentVersion < toVersion.value) {
            Timber.i("Migrating from schema version $currentVersion to ${currentVersion + 1}")
            currentMap =
                currentMap
                    .map { (_, value) ->
                        val newValue: ReviewReminderSchema = value.migrate()
                        newValue.id to newValue
                    }.toMap()
            currentVersion++
        }

        // Write to SharedPreferences, then return deserialized map
        val finalGroup =
            ReviewReminderGroup(
                currentMap.mapValues { (_, value) ->
                    value as? ReviewReminder ?: throw SerializationException("Expected ReviewReminder, got ${value::class.qualifiedName}")
                },
            )
        remindersSharedPrefs.edit {
            putString(encodedReviewReminderKey, encodeJson(finalGroup))
        }
        return finalGroup
    }

    /**
     * Decode an encoded [ReviewReminderGroup] JSON string which has been stored as a [StoredReviewReminderGroup].
     * @see Json.decodeFromString
     * @throws SerializationException If the stored string is not a valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a [ReviewReminderGroup],
     * and no valid schema migrations exist.
     */
    private fun decodeJson(
        jsonString: String,
        deckKeyForMigrationPurposes: String,
    ): ReviewReminderGroup {
        Timber.v("Decoding review reminders JSON string: $jsonString")
        val storedReviewReminderGroup = Json.decodeFromString<StoredReviewReminderGroup>(jsonString)
        return if (storedReviewReminderGroup.version.value != schemaVersion.value) {
            performSchemaMigration(
                deckKeyForMigrationPurposes,
                storedReviewReminderGroup.remindersMapJson,
                storedReviewReminderGroup.version,
                schemaVersion,
            )
        } else {
            ReviewReminderGroup(storedReviewReminderGroup.remindersMapJson)
        }
    }

    /**
     * Encode a [ReviewReminderGroup] as a [StoredReviewReminderGroup] JSON string.
     * @see Json.encodeToString
     */
    private fun encodeJson(reminders: ReviewReminderGroup): String =
        Json.encodeToString(StoredReviewReminderGroup.serializer(), StoredReviewReminderGroup(schemaVersion, reminders.serializeToString()))

    /**
     * Get the [ReviewReminder]s for a specific key.
     * @throws SerializationException If the value associated with this key is not valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a [ReviewReminderGroup].
     */
    private fun getRemindersForKey(key: String): ReviewReminderGroup {
        val jsonString = remindersSharedPrefs.getString(key, null) ?: return ReviewReminderGroup()
        return decodeJson(jsonString, deckKeyForMigrationPurposes = key)
    }

    /**
     * Get the [ReviewReminder]s for a specific deck.
     * @throws SerializationException If the reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a [ReviewReminderGroup].
     */
    fun getRemindersForDeck(did: DeckId): ReviewReminderGroup = getRemindersForKey(DECK_SPECIFIC_KEY + did)

    /**
     * Get the app-wide [ReviewReminder]s.
     * @throws SerializationException If the reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a [ReviewReminderGroup].
     */
    fun getAllAppWideReminders(): ReviewReminderGroup = getRemindersForKey(APP_WIDE_KEY)

    /**
     * Get all [ReviewReminder]s that are associated with a specific deck, all in a single flattened map.
     * @throws SerializationException If the reminders maps have not been stored in SharedPreferences as valid JSON strings.
     * @throws IllegalArgumentException If the decoded reminders maps are not instances of [ReviewReminderGroup].
     */
    fun getAllDeckSpecificReminders(): ReviewReminderGroup =
        remindersSharedPrefs
            .all
            .filterKeys { it.startsWith(DECK_SPECIFIC_KEY) }
            .map { (key, value) -> decodeJson(value.toString(), deckKeyForMigrationPurposes = key) }
            .mergeAll()

    /**
     * Edit the [ReviewReminder]s for a specific key. Deletes the review reminders map for this key if, after the editing operation,
     * no review reminders remain.
     * @param key
     * @param editor A lambda that takes the current map and returns the updated map.
     * @param expectedScope The expected scope of all review reminders in the resulting map.
     * @throws SerializationException If the current reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded current reminders map is not a [ReviewReminderGroup],
     * or if the result of applying the [editor] contains review reminders of a scope other than [expectedScope].
     */
    private fun editRemindersForKey(
        key: String,
        editor: ReviewReminderGroupEditor,
        expectedScope: ReviewReminderScope,
    ) {
        val existingReminders = getRemindersForKey(key)
        val updatedReminders = editor(existingReminders)

        require(updatedReminders.getRemindersList().all { it.scope == expectedScope }) {
            "Tried to write review reminders of an unexpected, incompatible scope to scope: $expectedScope"
        }

        remindersSharedPrefs.edit {
            if (updatedReminders.isEmpty()) {
                remove(key)
            } else {
                putString(key, encodeJson(updatedReminders))
            }
        }
    }

    /**
     * Edit the [ReviewReminder]s for a specific deck.
     * This assumes the resulting map contains only reminders of scope [ReviewReminderScope.DeckSpecific].
     * @param did
     * @param editor A lambda that takes the current map and returns the updated map.
     * @throws SerializationException If the current reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded current reminders map is not a [ReviewReminderGroup],
     * or if the result of applying the [editor] contains review reminders of a scope other than [ReviewReminderScope.DeckSpecific].
     */
    fun editRemindersForDeck(
        did: DeckId,
        editor: ReviewReminderGroupEditor,
    ) = editRemindersForKey(DECK_SPECIFIC_KEY + did, editor, ReviewReminderScope.DeckSpecific(did))

    /**
     * Edit the app-wide [ReviewReminder]s.
     * This assumes the resulting map contains only reminders of scope [ReviewReminderScope.Global].
     * @param editor A lambda that takes the current map and returns the updated map.
     * @throws SerializationException If the current reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded current reminders map is not a [ReviewReminderGroup],
     * or if the result of applying the [editor] contains review reminders of a scope other than [ReviewReminderScope.Global].
     */
    fun editAllAppWideReminders(editor: ReviewReminderGroupEditor) = editRemindersForKey(APP_WIDE_KEY, editor, ReviewReminderScope.Global)
}
