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

import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.libanki.DeckId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Manages the storage and retrieval of [ReviewReminder]s in SharedPreferences.
 *
 * [ReviewReminder]s can either be tied to a specific deck and trigger based on the number of cards
 * due in that deck, or they can be app-wide reminders that trigger based on the total number
 * of cards due across all decks. See [ReviewReminderScope].
 *
 * Calls to methods in this class should be wrapped by [ScheduleReminders.catchDatabaseExceptions].
 */
class ReviewRemindersDatabase {
    companion object {
        /**
         * Key in SharedPreferences for retrieving deck-specific reminders.
         * Should have deck ID appended to its end, ex. "review_reminders_deck_12345".
         * Its value is a HashMap<[ReviewReminderId], [ReviewReminder]> serialized as a JSON String.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DECK_SPECIFIC_KEY = "review_reminders_deck_"

        /**
         * Key in SharedPreferences for retrieving app-wide reminders.
         * Its value is a HashMap<[ReviewReminderId], [ReviewReminder]> serialized as a JSON String.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val APP_WIDE_KEY = "review_reminders_app_wide"
    }

    /**
     * Decode an encoded HashMap<[ReviewReminderId], [ReviewReminder]> JSON string.
     * @see Json.decodeFromString
     * @throws SerializationException If the stored string is not a valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    private fun decodeJson(jsonString: String): HashMap<ReviewReminderId, ReviewReminder> =
        Json.decodeFromString<HashMap<ReviewReminderId, ReviewReminder>>(jsonString)

    /**
     * Encode a Map<[ReviewReminderId], [ReviewReminder]> as a JSON string.
     * @see Json.encodeToString
     * @throws SerializationException If the stored string is somehow not a valid JSON string, even though the input parameter is type-checked.
     */
    private fun encodeJson(reminders: Map<ReviewReminderId, ReviewReminder>): String = Json.encodeToString(reminders)

    /**
     * Get the [ReviewReminder]s for a specific key.
     * @throws SerializationException If the value associated with this key is not valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    private fun getRemindersForKey(key: String): HashMap<ReviewReminderId, ReviewReminder> {
        val jsonString = AnkiDroidApp.sharedPrefs().getString(key, null) ?: return hashMapOf()
        return decodeJson(jsonString)
    }

    /**
     * Get the [ReviewReminder]s for a specific deck.
     * @throws SerializationException If the reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    fun getRemindersForDeck(did: DeckId): HashMap<ReviewReminderId, ReviewReminder> = getRemindersForKey(DECK_SPECIFIC_KEY + did)

    /**
     * Get the app-wide [ReviewReminder]s.
     * @throws SerializationException If the reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    fun getAllAppWideReminders(): HashMap<ReviewReminderId, ReviewReminder> = getRemindersForKey(APP_WIDE_KEY)

    /**
     * Get all [ReviewReminder]s that are associated with a specific deck, all in a single flattened map.
     * @throws SerializationException If the reminders maps have not been stored in SharedPreferences as valid JSON strings.
     * @throws IllegalArgumentException If the decoded reminders maps are not instances of HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    fun getAllDeckSpecificReminders(): HashMap<ReviewReminderId, ReviewReminder> =
        AnkiDroidApp
            .sharedPrefs()
            .all
            .filterKeys { it.startsWith(DECK_SPECIFIC_KEY) }
            .flatMap { (_, value) -> decodeJson(value.toString()).entries }
            .associateTo(hashMapOf()) { it.toPair() }

    /**
     * Edit the [ReviewReminder]s for a specific key.
     * @param key
     * @param reminderEditor A lambda that takes the current map and returns the updated map.
     * @throws SerializationException If the current reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded current reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    private fun editRemindersForKey(
        key: String,
        reminderEditor: (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder>,
    ) {
        val existingReminders = getRemindersForKey(key)
        val updatedReminders = reminderEditor(existingReminders)
        AnkiDroidApp.sharedPrefs().edit {
            putString(key, encodeJson(updatedReminders))
        }
    }

    /**
     * Edit the [ReviewReminder]s for a specific deck.
     * This assumes the resulting map contains only reminders of scope [ReviewReminderScope.DeckSpecific].
     * @param did
     * @param reminderEditor A lambda that takes the current map and returns the updated map.
     * @throws SerializationException If the current reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded current reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    fun editRemindersForDeck(
        did: DeckId,
        reminderEditor: (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder>,
    ) = editRemindersForKey(DECK_SPECIFIC_KEY + did, reminderEditor)

    /**
     * Edit the app-wide [ReviewReminder]s.
     * This assumes the resulting map contains only reminders of scope [ReviewReminderScope.Global].
     * @param reminderEditor A lambda that takes the current map and returns the updated map.
     * @throws SerializationException If the current reminders map has not been stored in SharedPreferences as a valid JSON string.
     * @throws IllegalArgumentException If the decoded current reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    fun editAllAppWideReminders(reminderEditor: (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder>) =
        editRemindersForKey(APP_WIDE_KEY, reminderEditor)

    /**
     * Helper method for getting all SharedPreferences that represent app-wide or deck-specific reminder HashMaps.
     * For example, may be used for constructing a backup of all review reminders pending a potentially-destructive migration operation.
     * Does not return the next-free-ID preference for review reminders used by [ReviewReminderId.getAndIncrementNextFreeReminderId].
     */
    fun getAllReviewReminderSharedPrefsAsMap(): Map<String, Any?> =
        AnkiDroidApp.sharedPrefs().all.filter {
            it.key.startsWith(DECK_SPECIFIC_KEY) ||
                it.key.startsWith(APP_WIDE_KEY)
        }

    /**
     * Helper method for deleting all SharedPreferences that represent app-wide or deck-specific reminder HashMaps.
     * Does not delete the next-free-ID preference for review reminders used by [ReviewReminderId.getAndIncrementNextFreeReminderId].
     *
     * For example, may be used when a potentially-destructive operation, like a failed migration, has been applied to all review reminders.
     * This method can be used to delete all potentially-corrupted review reminder shared preferences so that backed-up
     * review reminders can be restored.
     *
     * For developers debugging review reminder issues during development or writing tests:
     * call this when you need to hard-reset the review reminders database.
     */
    fun deleteAllReviewReminderSharedPrefs() {
        AnkiDroidApp.sharedPrefs().edit {
            AnkiDroidApp
                .sharedPrefs()
                .all
                .keys
                .filter { it.startsWith(DECK_SPECIFIC_KEY) || it.startsWith(APP_WIDE_KEY) }
                .forEach { remove(it) }
        }
    }

    /**
     * Schema update method for migrating old review reminders to new ones.
     * Use when [ReviewReminder] is updated and existing users who already have review reminders set up on their devices
     * need to have their data ported to the new schema.
     * @param serializer The serializer for the old schema of type [T] implementing [OldReviewReminderSchema]
     * @see [OldReviewReminderSchema]
     * @throws SerializationException If the current reminders maps have not been stored in SharedPreferences as valid JSON strings.
     * @throws IllegalArgumentException If the decoded current reminders maps are not instances of HashMap<[ReviewReminderId], [T]>.
     */
    fun <T : OldReviewReminderSchema> attemptSchemaMigration(serializer: KSerializer<T>) {
        val mapSerializer = MapSerializer(ReviewReminderId.serializer(), serializer)
        AnkiDroidApp.sharedPrefs().edit {
            getAllReviewReminderSharedPrefsAsMap().forEach { (key, value) ->
                val old: Map<ReviewReminderId, T> = Json.decodeFromString(mapSerializer, value.toString())
                val new =
                    old
                        .map { (_, value) ->
                            val updatedReminder = value.migrate()
                            updatedReminder.id to updatedReminder
                        }.toMap()
                putString(key, Json.encodeToString(new))
                Timber.d("Migrated review reminders from $key")
            }
        }
    }
}

/**
 * When [ReviewReminder] is updated by a developer, implement this interface in a new data class which
 * has the same fields as the old version of [ReviewReminder], then implement the [migrate] method which
 * transforms old [ReviewReminder]s to new [ReviewReminder]s. Data classes implementing this interface
 * should be marked as @Serializable.
 * @see [ReviewRemindersDatabase.attemptSchemaMigration].
 */
interface OldReviewReminderSchema {
    fun migrate(): ReviewReminder
}
