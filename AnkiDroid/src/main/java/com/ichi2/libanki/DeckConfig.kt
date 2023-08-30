/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 Copyright (c) 2020 Arthur Milchior <Arthur@Milchior.fr>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import com.ichi2.utils.deepClonedInto
import org.json.JSONObject
import timber.log.Timber

class DeckConfig
/**
 * Creates a copy from [JSONObject] and use it as a string
 *
 * This function will perform deepCopy on the passed object
 *
 */(json: JSONObject) : JSONObject() {

    var conf: Long
        get() = getLong("conf")
        set(value) {
            put("conf", value)
        }

    var id: DeckConfigId
        get() = getLong("id")
        set(value) {
            put("id", value)
        }

    var name: String
        get() = getString("name")
        set(value) {
            put("name", value)
        }

    companion object {
        fun parseTimer(config: JSONObject): Boolean? {
            // Note: Card.py used != 0, DeckOptions used == 1
            return try {
                // #6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
                config.getInt("timer") != 0
            } catch (e: Exception) {
                Timber.w(e)
                try {
                    config.getBoolean("timer")
                } catch (ex: Exception) {
                    Timber.w(ex)
                    null
                }
            }
        }

        /**
         * @return The 'timer' property on [config], or [defaultValue] if it's not set.
         */
        fun parseTimerOpt(config: JSONObject, defaultValue: Boolean): Boolean =
            parseTimer(config) ?: defaultValue
    }

    init {
        json.deepClonedInto(this)
    }
}
