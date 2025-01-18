/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki

import com.ichi2.utils.JSONObjectHolder
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONObject.NULL

interface Deck : JSONObjectHolder {
    companion object {
        fun isFiltered(jsonObject: JSONObject) = jsonObject.getInt("dyn") != 0

        fun factory(jsonObject: JSONObject): Deck = if (isFiltered(jsonObject)) FilteredDeck(jsonObject) else NormalDeck(jsonObject)

        fun factory(jsonObject: String) = factory(JSONObject(jsonObject))
    }

    /**
     * Whether this deck is a filtered deck.
     */
    val isFiltered: Boolean
        get() = jsonObject.getInt("dyn") != 0

    /**
     * Whether this deck is a normal deck. That is, not a filtered deck.
     */
    val isNormal: Boolean
        get() = !isFiltered

    /**
     * The name of the deck. Mutable. If you want a way to persistently represents this deck, use [id] instead.
     */
    var name: String
        get() = jsonObject.getString("name")
        set(value) {
            jsonObject.put("name", value)
        }

    /**
     * If this deck as subdecks, whether those subdecks should be collapsed in the desktop card browser.
     * Not used in ankidroid at the moment.
     */
    var browserCollapsed: Boolean
        get() = jsonObject.getBoolean("browserCollapsed")
        set(value) {
            jsonObject.put("browserCollapsed", value)
        }

    /**
     * If this deck as subdecks, whether those subdecks should be collapsed in the deck picker.
     */
    var collapsed: Boolean
        get() = jsonObject.getBoolean("collapsed")
        set(value) {
            jsonObject.put("collapsed", value)
        }

    /**
     * The id of the deck. Should be globally unique
     * (created as a timestamp, very small chance of collision between two different decks from different users)
     */
    var id: DeckId
        get() = jsonObject.getLong("id")
        set(value) {
            jsonObject.put("id", value)
        }

    /**
     * A string explaining what can be found in this deck.
     */
    var description: String
        get() = jsonObject.optString("desc")
        set(value) {
            jsonObject.put("desc", value)
        }

    var resched: Boolean
        get() = jsonObject.getBoolean("resched")
        set(value) {
            jsonObject.put("resched", value)
        }

    var previewAgainSecs: String
        get() = jsonObject.getString("previewAgainSecs")
        set(value) {
            jsonObject.put("previewAgainSecs", value)
        }

    var previewHardSecs: String
        get() = jsonObject.getString("previewHardSecs")
        set(value) {
            jsonObject.put("previewHardSecs", value)
        }

    var previewGoodSecs: String
        get() = jsonObject.getString("previewGoodSecs")
        set(value) {
            jsonObject.put("previewGoodSecs", value)
        }

    /**
     * An array of string. The i-th string correspond to the number of second/minute/hour or day for the i-th learning steps.
     * See https://docs.ankiweb.net/deck-options.html#learning-steps
     */
    var delays: JSONArray?
        get() = jsonObject.optJSONArray("delays")
        set(value) {
            val value =
                if (value == null) {
                    NULL
                } else {
                    value
                }
            jsonObject.put("delays", value)
        }

    fun removeEmpty() {
        jsonObject.remove("empty")
    }
}
