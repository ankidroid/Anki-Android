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

import androidx.annotation.VisibleForTesting
import com.ichi2.utils.JSONObjectHolder
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONObject.NULL

/**
 * Represents a deck. Use [factory] in order to create either a [FilteredDeck] or a [RegularDeck].
 */
interface Deck : JSONObjectHolder {
    companion object {
        const val DYN = "dyn"
        const val NAME = "name"
        const val BROWSER_COLLAPSED = "browserCollapsed"
        const val COLLAPSED = "collapsed"
        const val ID = "id"
        const val DESCRIPTION = "desc"
        const val RESCHED = "resched"
        const val PREVIEW_AGAIN_SECS = "previewAgainSecs"
        const val PREVIEW_HARD_SECS = "previewHardSecs"
        const val PREVIEW_GOOD_SECS = "previewGoodSecs"
        const val DELAYS = "delays"
        const val EMPTY = "empty"

        fun isFiltered(jsonObject: JSONObject) = jsonObject.getInt(DYN) != 0

        /**
         * Those two factories returns either a [FilteredDeck] or a [RegularDeck] encapsulating the input, depending on the value of [isFiltered].
         */
        fun factory(jsonObject: JSONObject): Deck = if (isFiltered(jsonObject)) FilteredDeck(jsonObject) else RegularDeck(jsonObject)

        fun factory(jsonObject: String) = factory(JSONObject(jsonObject))
    }

    /**
     * Whether this deck is a filtered deck.
     */
    var isFiltered: Boolean
        get() = jsonObject.getInt(DYN) != 0

        @VisibleForTesting
        set(value) {
            jsonObject.put(DYN, if (value) 1 else 0)
        }

    /**
     * Whether this deck is a normal deck. That is, not a filtered deck.
     */
    var isRegular: Boolean
        get() = !isFiltered

        @VisibleForTesting
        set(value) {
            isFiltered = !value
        }

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
        get() = jsonObject.getBoolean(BROWSER_COLLAPSED)
        set(value) {
            jsonObject.put(BROWSER_COLLAPSED, value)
        }

    /**
     * If this deck as subdecks, whether those subdecks should be collapsed in the deck picker.
     */
    var collapsed: Boolean
        get() = jsonObject.getBoolean(COLLAPSED)
        set(value) {
            jsonObject.put(COLLAPSED, value)
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
        get() = jsonObject.optString(DESCRIPTION)
        set(value) {
            jsonObject.put(DESCRIPTION, value)
        }

    var resched: Boolean
        get() = jsonObject.getBoolean(RESCHED)
        set(value) {
            jsonObject.put(RESCHED, value)
        }

    var previewAgainSecs: Int
        get() = jsonObject.getInt(PREVIEW_AGAIN_SECS)
        set(value) {
            jsonObject.put(PREVIEW_AGAIN_SECS, value)
        }
    var previewHardSecs: Int
        get() = jsonObject.getInt(PREVIEW_HARD_SECS)
        set(value) {
            jsonObject.put(PREVIEW_HARD_SECS, value)
        }

    var previewGoodSecs: Int
        get() = jsonObject.getInt(PREVIEW_GOOD_SECS)
        set(value) {
            jsonObject.put(PREVIEW_GOOD_SECS, value)
        }

    /**
     * An array of string. The i-th string correspond to the number of second/minute/hour or day for the i-th learning steps.
     * See https://docs.ankiweb.net/deck-options.html#learning-steps
     */
    var delays: JSONArray?
        get() = jsonObject.optJSONArray(DELAYS)
        set(value) {
            val value =
                if (value == null) {
                    NULL
                } else {
                    value
                }
            jsonObject.put(DELAYS, value)
        }

    fun removeEmpty() {
        jsonObject.remove(EMPTY)
    }
}
