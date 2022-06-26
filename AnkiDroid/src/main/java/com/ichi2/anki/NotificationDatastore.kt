/*
 * Copyright (c) 2022 Prateek Singh <prateeksingh3212@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.NotificationDatastore.Companion.getInstance
import com.ichi2.anki.model.DeckNotification
import com.ichi2.libanki.DeckId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.*

/**
 * Time at which deck notification will trigger. Stores time in millisecond in EPOCH format.
 */
/*
* We use TimeOfNotification as key of json object. So it must be string.
* NOTE: It's in decimal. Hexadecimal would make the conversion faster and takes less space, but really not worth the code complexity added.
*/
typealias TimeOfNotification = String

/**
 * DeckIds which is going to trigger at particular time.
 */
typealias SimultaneouslyTriggeredDeckIds = HashSet<DeckId>

/**
 * Indicates all notifications that we must eventually trigger. If a notification time is in the past, we must do it as soon as possible, otherwise we must trigger the notification later.
 * Implemented as map from time to set of deck ids triggered at this time
 */
/*
* NOTE: In Time Of Notification String will break when timestamp gets an extra digit because we'll get new timestamps
* starting with a 1 that will be a number greater than previous numbers but alphabetically shorter
* This will occur on 20 NOVEMBER 2286
*/
typealias NotificationTodo = TreeMap<TimeOfNotification, SimultaneouslyTriggeredDeckIds>

/**
 * Notification which is going to trigger next.
 * */
fun NotificationTodo.earliestNotifications(): MutableMap.MutableEntry<TimeOfNotification, SimultaneouslyTriggeredDeckIds>? =
    firstEntry()

/**
 * Stores the scheduled notification details
 * This is a singleton class, use [getInstance]
 * */
class NotificationDatastore private constructor(val context: Context) {

    private val objectMapper = ObjectMapper()

    /**
     * Stores the String in Notification Datastore
     * It stores the data asynchronously.
     * Calling this function guarantees to store value in database.
     * @param key The Key of value. Used in fetching the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE STRING)</b>.
     * */
    suspend fun putStringAsync(key: String, value: String) {
        val dataStoreKey = stringPreferencesKey(key)
        context.notificationDatastore.edit { metaData ->
            metaData[dataStoreKey] = value
        }
    }

    /**
     * Stores the String in Notification Datastore
     * It stores the data synchronously. It will create Coroutine [Dispatchers.IO] Scope Internally.
     * @param key The Key of value. Used in fetching the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE STRING)</b>.
     * */
    fun putStringSync(key: String, value: String) {
        CoroutineScope(Dispatchers.IO).launch {
            putStringAsync(key, value)
        }
    }

    /**
     * Fetches the String value from Datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value associated to `key` by the last call to [putStringSync], [putStringAsync], or [default] if none
     * */
    suspend fun getString(key: String, default: String): String {
        val dataStoreKey = stringPreferencesKey(key)
        return context.notificationDatastore.data.firstOrNull()?.let {
            it[dataStoreKey]
        } ?: default
    }

    /**
     * Stores the Integer in Notification Datastore
     * It stores the data asynchronously.
     * Calling this function guarantees to store value in database.
     * @param key The Key of value. Created while storing the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE INTEGER)</b>.
     * */
    suspend fun putIntAsync(key: String, value: Int) {
        val dataStoreKey = intPreferencesKey(key)
        context.notificationDatastore.edit { metaDataEditor ->
            metaDataEditor[dataStoreKey] = value
        }
    }

    /**
     * Stores the Integer in Notification Datastore
     * It stores the data synchronously. It will create Coroutine [Dispatchers.IO] Scope Internally.
     * @param key The Key of value. Created while storing the data.
     * @param value Value that needs to be stored <b>(VALUE MUST BE INTEGER)</b>.
     * */
    fun putIntSync(key: String, value: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            putIntAsync(key, value)
        }
    }

    /**
     * Fetches the Integer value from Datastore.
     * @prams The Key of deck whose data you want to fetch.
     * @return Value associated to `key` by the last call to [putIntSync], [putIntAsync], or [default] if none
     * */
    suspend fun getInt(key: String, default: Int): Int {
        val dataStoreKey = intPreferencesKey(key)
        return context.notificationDatastore.data.firstOrNull()?.let {
            it[dataStoreKey]
        } ?: default
    }

    /**
     * Stores the Map of time and list of deck ids to Datastore
     * It stores the data asynchronously.
     * */
    suspend fun setTimeDeckData(data: Map<String, HashSet<Long>>) {
        val dataStoreKey = stringPreferencesKey("TIME_DECK_DATA")
        val jsonObj = JSONObject(data)
        context.notificationDatastore.edit { metaData ->
            metaData[dataStoreKey] = jsonObj.toString()
        }
    }

    /**
     * Fetches the Map of time and list of deck ids from Datastore.
     * @return The current AllTimeAndDecksMap
     * */
    /*
    * We actually are not blocking the thread. This method throws an exception. It will not create problem for us.
    * */
    @Suppress("UNCHECKED_CAST", "BlockingMethodInNonBlockingContext")
    suspend fun getTimeDeckData(): NotificationTodo? {
        val datastoreKey = stringPreferencesKey("TIME_DECK_DATA")
        return context.notificationDatastore.data.firstOrNull()?.let {
            try {
                objectMapper.readValue(
                    it[datastoreKey],
                    TreeMap::class.java
                ) as NotificationTodo
            } catch (ex: JacksonException) {
                Timber.d(ex.cause)
                null
            }
        }
    }

    /**
     * Stores the details of the [notification] scheduling of deck [did]
     * @return whether operation is successful.
     * */
    suspend fun setDeckSchedData(did: DeckId, notification: DeckNotification): Boolean {
        val dataStoreKey = stringPreferencesKey(did.toString())
        return runCatching {
            val json = objectMapper.writeValueAsString(notification)
            context.notificationDatastore.edit { metaData ->
                metaData[dataStoreKey] = json
            }
        }.isSuccess
    }

    /**
     * Fetches the details of particular deck scheduling.
     * @return Deck Notification model for particular deck.
     * */
    /*
    * We actually are not blocking the thread. This method throws an exception. It will not create problem for us.
    * TODO: unit test that :
    *  * if there is no preference at all, we return null
    *  * if there is a preference without entry for this key we return null
    *  * if there is a preference whose entry for this key can't be cast to DeckNotification, throw
    *  * if there is a preference with entry for this key that can be cast, we get expected notification
    */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getDeckSchedData(did: DeckId): DeckNotification? {
        val datastoreKey = stringPreferencesKey(did.toString())
        return context.notificationDatastore.data.firstOrNull()?.let {
            val schedDataJSON = it[datastoreKey] ?: return null
            try {
                objectMapper.readValue(
                    schedDataJSON,
                    DeckNotification::class.java
                )
            } catch (ex: Exception) {
                // Let the exception throw
                CrashReportService.sendExceptionReport(
                    ex,
                    "Notification Datastore-getDeckSchedData",
                    "Exception Occurred during fetching of data."
                )
                throw Exception("Unable to find schedule data of given deck id: $did", ex)
            }
        }
    }

    companion object {
        private lateinit var INSTANCE: NotificationDatastore
        private val Context.notificationDatastore: DataStore<Preferences> by preferencesDataStore("NotificationDatastore")

        /**
         * @param block A function that create the unique notification data store.
         * Only called if it does not already exists. It is then in charge of assigning INSTANCE.
         * @return the unique instance of NotificationDataStore. Creates it through [block] if necessary.
         */
        private fun instanceInitializedOr(
            context: Context,
            block: (context: Context) -> NotificationDatastore
        ) = if (this::INSTANCE.isInitialized) INSTANCE else block(context)

        /**
         * Thread safe.
         * @return The singleton NotificationDatastore
         */
        fun getInstance(context: Context) = instanceInitializedOr(context) {
            synchronized(this) {
                // Check again whether [INSTANCE] is initialized because it could have been initialized while waiting for synchronization.
                instanceInitializedOr(context) {
                    NotificationDatastore(context).also {
                        INSTANCE = it
                    }
                }
            }
        }
    }
}
