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
         * Its value is a MutableList<[ReviewReminder]> serialized as a JSON String.
         */
        private const val DECK_SPECIFIC_KEY = "review_reminders_deck_"

        /**
         * Key in SharedPreferences for retrieving app-wide reminders.
         * Its value is a MutableList<[ReviewReminder]> serialized as a JSON String.
         */
        private const val APP_WIDE_KEY = "review_reminders_app_wide"
    }

    /**
     * Get the [ReviewReminder]s for a specific key.
     */
    private fun getRemindersForKey(key: String): MutableList<ReviewReminder> {
        val jsonString = context.sharedPrefs().getString(key, null) ?: return mutableListOf()
        return Json.decodeFromString<MutableList<ReviewReminder>>(jsonString)
    }

    /**
     * Get the [ReviewReminder]s for a specific deck.
     */
    fun getRemindersForDeck(did: DeckId): MutableList<ReviewReminder> = getRemindersForKey(DECK_SPECIFIC_KEY + did)

    /**
     * Get the app-wide [ReviewReminder]s.
     */
    fun getAllAppWideReminders(): MutableList<ReviewReminder> = getRemindersForKey(APP_WIDE_KEY)

    /**
     * Get all [ReviewReminder]s that are associated with a specific deck, all in a single mutable list.
     */
    fun getAllDeckSpecificReminders(): MutableList<ReviewReminder> {
        val allReminders = mutableListOf<ReviewReminder>()
        context.sharedPrefs().all.forEach { (key, value) ->
            if (key.startsWith(DECK_SPECIFIC_KEY)) {
                val reminders = Json.decodeFromString<MutableList<ReviewReminder>>(value.toString())
                allReminders.addAll(reminders)
            }
        }
        return allReminders
    }

    /**
     * Edit the [ReviewReminder]s for a specific key.
     * @param key
     * @param reminderEditor A lambda that takes the current list and returns the updated list.
     */
    private fun editRemindersForKey(
        key: String,
        reminderEditor: (MutableList<ReviewReminder>) -> List<ReviewReminder>,
    ) {
        val reminders = getRemindersForKey(key)
        val updatedReminders = reminderEditor(reminders)
        context.sharedPrefs().edit {
            putString(key, Json.encodeToString(updatedReminders))
        }
    }

    /**
     * Edit the [ReviewReminder]s for a specific deck.
     * @param did
     * @param reminderEditor A lambda that takes the current list and returns the updated list.
     */
    fun editRemindersForDeck(
        did: DeckId,
        reminderEditor: (MutableList<ReviewReminder>) -> List<ReviewReminder>,
    ) = editRemindersForKey(DECK_SPECIFIC_KEY + did, reminderEditor)

    /**
     * Edit the app-wide [ReviewReminder]s.
     * @param reminderEditor A lambda that takes the current list and returns the updated list.
     */
    fun editAllAppWideReminders(reminderEditor: (MutableList<ReviewReminder>) -> List<ReviewReminder>) =
        editRemindersForKey(APP_WIDE_KEY, reminderEditor)

    /**
     * Edit all [ReviewReminder]s that are associated with a specific deck by operating on a single mutable list.
     */
    fun editAllDeckSpecificReminders(reminderEditor: (MutableList<ReviewReminder>) -> List<ReviewReminder>) {
        val newReminders = reminderEditor(getAllDeckSpecificReminders())
        val remindersForEachDeck: Map<DeckId, List<ReviewReminder>> = newReminders.groupBy { it.did }

        // Clear existing review reminder keys in SharedPreferences
        val existingKeys =
            context
                .sharedPrefs()
                .all.keys
                .filter { it.startsWith(DECK_SPECIFIC_KEY) }

        context.sharedPrefs().edit {
            existingKeys.forEach { remove(it) }
            // Add the updated ones back in
            remindersForEachDeck.forEach { (did, reminders) ->
                putString(DECK_SPECIFIC_KEY + did, Json.encodeToString(reminders))
            }
        }
    }
}
