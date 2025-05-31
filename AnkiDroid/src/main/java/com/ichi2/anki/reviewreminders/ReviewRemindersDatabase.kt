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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

/**
 * Preferences Datastore for storing review reminders.
 */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "review_reminders")

/**
 * Manages the storage and retrieval of review reminders in the local Preferences Datastore database.
 */
class ReviewRemindersDatabase(
    val context: Context,
) {
    companion object {
        /**
         * IDs start at this value and climb upwards by one each time.
         */
        private const val FIRST_REMINDER_ID = 0

        /**
         * Keys of the Preferences Datastore used to store reminders.
         * - deck_<deck_id>: String (JSON of List<ReviewReminder>): JSON string of the list of reminders for a deck
         * - app_wide: String (JSON of List<ReviewReminder>): JSON string of the list of app-wide reminders
         * - next_free_id: Int: The next available free ID for a reminder
         * - previously_used_ids: String (JSON of List<Int>): JSON string of the list of previously used but now available IDs
         */
        private const val DATASTORE_DECK_SPECIFIC_KEY = "deck_%s"
        private const val DATASTORE_APP_WIDE_KEY = "app_wide"
        private const val DATASTORE_NEXT_FREE_ID_KEY = "next_free_id"
        private const val DATASTORE_PREVIOUSLY_USED_IDS_KEY = "previously_used_ids"
    }

    /**
     * Retrieve the list of reminders for a specific deck.
     * @param did The deck ID.
     * @return The list of reminders.
     */
    suspend fun getRemindersForDeck(did: Long): List<ReviewReminder> {
        val key = stringPreferencesKey(String.format(DATASTORE_DECK_SPECIFIC_KEY, did))
        return getRemindersForKey(key)
    }

    /**
     * Retrieve the list of app-wide reminders.
     * @return The list of app-wide reminders.
     */
    suspend fun getAppWideReminders(): List<ReviewReminder> {
        val key = stringPreferencesKey(DATASTORE_APP_WIDE_KEY)
        return getRemindersForKey(key)
    }

    /**
     * Retrieve the list of reminders given a key to use for the Preferences Datastore.
     * @param key The Preferences Datastore key.
     * @return The list of reminders associated with this key.
     */
    private suspend fun getRemindersForKey(key: Preferences.Key<String>): List<ReviewReminder> {
        val jsonString =
            context.dataStore.data.firstOrNull()?.let {
                it[key]
            } ?: ""
        if (jsonString.isEmpty()) return emptyList()
        val reminders = Json.decodeFromString<List<ReviewReminder>>(jsonString)
        return reminders
    }

    /**
     * Set the list of reminders for a specific deck.
     * @param did The deck ID.
     * @param reminders The reminders to associate with this deck.
     */
    suspend fun setRemindersForDeck(
        did: Long,
        reminders: List<ReviewReminder>,
    ) {
        val key = stringPreferencesKey(String.format(DATASTORE_DECK_SPECIFIC_KEY, did))
        return setRemindersForKey(key, reminders)
    }

    /**
     * Set the list of app-wide reminders.
     * @param reminders The reminders to associate with the app.
     */
    suspend fun setAppWideReminders(reminders: List<ReviewReminder>) {
        val key = stringPreferencesKey(DATASTORE_APP_WIDE_KEY)
        return setRemindersForKey(key, reminders)
    }

    /**
     * Set the list of reminders given a key to use for the Preferences Datastore.
     * @param key The Preferences Datastore key.
     * @param reminders The reminders to associate with this key.
     */
    private suspend fun setRemindersForKey(
        key: Preferences.Key<String>,
        reminders: List<ReviewReminder>,
    ) {
        val jsonString = Json.encodeToString(reminders)
        context.dataStore.edit { preferences ->
            preferences[key] = jsonString
        }
    }

    /**
     * Allocate and return the next free reminder ID which can be associated with a new review reminder.
     * @return The next free reminder ID.
     */
    internal suspend fun allocateReminderId(): Int {
        // Get next free ID and previously used IDs
        val nextFreeIdKey = intPreferencesKey(DATASTORE_NEXT_FREE_ID_KEY)
        val previouslyUsedIdsKey = stringPreferencesKey(DATASTORE_PREVIOUSLY_USED_IDS_KEY)
        val (nextFreeId, previouslyUsedIds) =
            context.dataStore.data.firstOrNull()?.let { preferences ->
                val nextFreeId = preferences[nextFreeIdKey] ?: FIRST_REMINDER_ID
                val previouslyUsedIds = preferences[previouslyUsedIdsKey] ?: "[]"
                Pair(nextFreeId, previouslyUsedIds)
            } ?: Pair(FIRST_REMINDER_ID, "[]")
        val previouslyUsedIdsList = Json.decodeFromString<MutableList<Int>>(previouslyUsedIds)

        // Use previously used IDs if available, else increment next free ID
        if (previouslyUsedIdsList.isNotEmpty()) {
            val id = previouslyUsedIdsList.removeAt(previouslyUsedIdsList.lastIndex)
            context.dataStore.edit { preferences ->
                preferences[previouslyUsedIdsKey] = Json.encodeToString(previouslyUsedIdsList)
            }
            return id
        } else {
            context.dataStore.edit { preferences ->
                preferences[nextFreeIdKey] = nextFreeId + 1
            }
            return nextFreeId
        }
    }

    /**
     * Deallocate a reminder ID, marking it as available for future use.
     * @param id The ID to deallocate.
     */
    internal suspend fun deallocateReminderId(id: Int) {
        // Get previously used IDs
        val previouslyUsedIdsKey = stringPreferencesKey(DATASTORE_PREVIOUSLY_USED_IDS_KEY)
        val previouslyUsedIds =
            context.dataStore.data.firstOrNull()?.let { preferences ->
                preferences[previouslyUsedIdsKey] ?: "[]"
            } ?: "[]"
        val previouslyUsedIdsList = Json.decodeFromString<MutableList<Int>>(previouslyUsedIds)

        // Place the ID into the previously used IDs list so it can be reused in the future
        previouslyUsedIdsList.add(id)
        context.dataStore.edit { preferences ->
            preferences[previouslyUsedIdsKey] = Json.encodeToString(previouslyUsedIdsList)
        }
    }

    /**
     * Add a review reminder for a specific deck.
     * @param did The deck ID.
     * @param newReminder The new review reminder to add.
     */
    suspend fun addReviewReminderForDeck(
        did: Long,
        newReminder: ReviewReminder,
    ) {
        val reminders = getRemindersForDeck(did).toMutableList()
        reminders.add(newReminder)
        setRemindersForDeck(did, reminders)
    }

    /**
     * Edit a review reminder for a specific deck. Must provide the deck ID and the reminder ID.
     * @param did The deck ID.
     * @param reminderId The ID of the reminder to edit.
     * @param updatedReminder The updated review reminder.
     */
    suspend fun editReviewReminderForDeck(
        did: Long,
        reminderId: Int,
        updatedReminder: ReviewReminder,
    ) {
        val reminders = getRemindersForDeck(did).toMutableList()
        val indexToEdit = reminders.indexOfFirst { it.id == reminderId }
        if (indexToEdit == -1) {
            throw IllegalArgumentException("Edit Reminder: Reminder with ID $reminderId not found in deck $did")
        }
        reminders[indexToEdit] = updatedReminder
        setRemindersForDeck(did, reminders)
    }

    /**
     * Delete a review reminder for a specific deck.
     * This will deallocate the ID of the reminder and delete it from the list of reminders for the deck.
     * @param did The deck ID.
     * @param reminderId The ID of the reminder to delete.
     */
    suspend fun deleteReviewReminderForDeck(
        did: Long,
        reminderId: Int,
    ) {
        val reminders = getRemindersForDeck(did).toMutableList()
        val reminderToRemove = reminders.find { it.id == reminderId }
        if (reminderToRemove == null) {
            throw IllegalArgumentException("Delete Reminder: Reminder with ID $reminderId not found in deck $did")
        }
        reminders.remove(reminderToRemove)
        setRemindersForDeck(did, reminders)
        deallocateReminderId(reminderId)
    }
}
