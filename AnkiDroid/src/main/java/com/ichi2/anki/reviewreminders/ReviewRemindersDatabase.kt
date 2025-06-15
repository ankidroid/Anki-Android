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
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.showError
import com.ichi2.libanki.DeckId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Manages the storage and retrieval of [ReviewReminder]s in SharedPreferences.
 *
 * [ReviewReminder]s can either be tied to a specific deck and trigger based on the number of cards
 * due in that deck, or they can be app-wide reminders that trigger based on the total number
 * of cards due across all decks.
 */
class ReviewRemindersDatabase(
    val context: Context,
) {
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
     * It is possible for Json.decodeFromString to throw [SerializationException]s if a serialization
     * error is encountered, or [IllegalArgumentException]s if the decoded object is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     * @see Json.decodeFromString
     */
    private fun decodeJson(jsonString: String): HashMap<ReviewReminderId, ReviewReminder> {
        try {
            return Json.decodeFromString<HashMap<ReviewReminderId, ReviewReminder>>(jsonString)
        } catch (e: SerializationException) {
            showError(
                context,
                "Something went wrong. A serialization error was encountered while retrieving review reminders.",
            )
            return hashMapOf()
        } catch (e: IllegalArgumentException) {
            showError(
                context,
                "Something went wrong. An unexpected data type was read while retrieving review reminders.",
            )
            return hashMapOf()
        }
    }

    /**
     * Get the [ReviewReminder]s for a specific key.
     */
    private fun getRemindersForKey(key: String): HashMap<ReviewReminderId, ReviewReminder> {
        val jsonString = context.sharedPrefs().getString(key, null) ?: return hashMapOf()
        return decodeJson(jsonString)
    }

    /**
     * Get the [ReviewReminder]s for a specific deck.
     */
    fun getRemindersForDeck(did: DeckId): HashMap<ReviewReminderId, ReviewReminder> = getRemindersForKey(DECK_SPECIFIC_KEY + did)

    /**
     * Get the app-wide [ReviewReminder]s.
     */
    fun getAllAppWideReminders(): HashMap<ReviewReminderId, ReviewReminder> = getRemindersForKey(APP_WIDE_KEY)

    /**
     * Get all [ReviewReminder]s that are associated with a specific deck, grouped by deck ID.
     */
    private fun getAllDeckSpecificRemindersGrouped(): Map<DeckId, HashMap<ReviewReminderId, ReviewReminder>> {
        return context
            .sharedPrefs()
            .all
            .filterKeys { it.startsWith(DECK_SPECIFIC_KEY) }
            .mapNotNull { (key, value) ->
                val did = key.removePrefix(DECK_SPECIFIC_KEY).toLongOrNull() ?: return@mapNotNull null
                val reminders = decodeJson(value.toString())
                did to reminders
            }.toMap()
    }

    /**
     * Get all [ReviewReminder]s that are associated with a specific deck, all in a single flattened map.
     */
    fun getAllDeckSpecificReminders(): HashMap<ReviewReminderId, ReviewReminder> = getAllDeckSpecificRemindersGrouped().flatten()

    /**
     * Edit the [ReviewReminder]s for a specific key.
     * @param key
     * @param reminderEditor A lambda that takes the current map and returns the updated map.
     */
    private fun editRemindersForKey(
        key: String,
        reminderEditor: (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder>,
    ) {
        val existingReminders = getRemindersForKey(key)
        val updatedReminders = reminderEditor(existingReminders)
        context.sharedPrefs().edit {
            putString(key, Json.encodeToString(updatedReminders))
        }
    }

    /**
     * Edit the [ReviewReminder]s for a specific deck.
     * @param did
     * @param reminderEditor A lambda that takes the current map and returns the updated map.
     */
    fun editRemindersForDeck(
        did: DeckId,
        reminderEditor: (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder>,
    ) = editRemindersForKey(DECK_SPECIFIC_KEY + did, reminderEditor)

    /**
     * Edit the app-wide [ReviewReminder]s.
     * @param reminderEditor A lambda that takes the current map and returns the updated map.
     */
    fun editAllAppWideReminders(reminderEditor: (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder>) =
        editRemindersForKey(APP_WIDE_KEY, reminderEditor)

    /**
     * Edit all [ReviewReminder]s that are associated with a specific deck by operating on a single mutable map.
     */
    fun editAllDeckSpecificReminders(reminderEditor: (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder>) {
        val existingRemindersGrouped = getAllDeckSpecificRemindersGrouped()
        val existingRemindersFlattened = existingRemindersGrouped.flatten()

        val updatedRemindersFlattened = reminderEditor(existingRemindersFlattened)
        val updatedRemindersGrouped = updatedRemindersFlattened.groupByDeckId()

        val existingKeys = existingRemindersGrouped.keys.map { DECK_SPECIFIC_KEY + it }

        context.sharedPrefs().edit {
            // Clear existing review reminder keys in SharedPreferences
            existingKeys.forEach { remove(it) }
            // Add the updated ones back in
            updatedRemindersGrouped.forEach { (did, reminders) ->
                putString(DECK_SPECIFIC_KEY + did, Json.encodeToString(reminders))
            }
        }
    }

    /**
     * Utility function for flattening maps of [ReviewReminder]s grouped by deck ID into a single map.
     */
    private fun Map<DeckId, HashMap<ReviewReminderId, ReviewReminder>>.flatten(): HashMap<ReviewReminderId, ReviewReminder> =
        hashMapOf<ReviewReminderId, ReviewReminder>().apply {
            this@flatten.forEach { (_, reminders) ->
                putAll(reminders)
            }
        }

    /**
     * Utility function for grouping maps of [ReviewReminder]s by deck ID.
     */
    private fun Map<ReviewReminderId, ReviewReminder>.groupByDeckId(): Map<DeckId, HashMap<ReviewReminderId, ReviewReminder>> =
        hashMapOf<DeckId, HashMap<ReviewReminderId, ReviewReminder>>().apply {
            this@groupByDeckId.forEach { (id, reminder) ->
                getOrPut(reminder.did) { hashMapOf() }[id] = reminder
            }
        }
}
