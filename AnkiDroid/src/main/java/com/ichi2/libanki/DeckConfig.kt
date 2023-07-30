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

class DeckConfig : JSONObject {
    val source: Source

    /**
     * Creates a new empty deck config object
     */
    private constructor(source: Source) : super() {
        this.source = source
    }

    /**
     * Creates a copy from [JSONObject] and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     */
    constructor(json: JSONObject, source: Source) {
        this.source = source
        json.deepClonedInto(this)
    }

    val isDyn: Boolean
        get() = getInt("dyn") == Consts.DECK_DYN
    val isStd: Boolean
        get() = getInt("dyn") == Consts.DECK_STD

    fun deepClone(): DeckConfig {
        val dc = DeckConfig(source)
        return deepClonedInto(dc)
    }

    /** Specifies how to save the config  */
    enum class Source {
        /** From an entry in dconf  */
        DECK_CONFIG,

        /** filtered decks have their config embedded in the deck  */
        DECK_EMBEDDED
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
}
