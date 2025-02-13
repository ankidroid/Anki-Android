/****************************************************************************************
 * Copyright (c) 2025 Arthur Milchior <arthur@milchior.fr>                             *
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
package com.ichi2.anki.libanki

import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a Filtered (a.k.a. Dynamic, Cram) deck.
 */
@JvmInline
value class FilteredDeck(
    override val jsonObject: JSONObject,
) : Deck {
    constructor(jsonObject: String) : this(JSONObject(jsonObject))

    /**
     * The options configuring which cards are shown in a filtered deck.
     * See https://docs.ankiweb.net/filtered-decks.html
     */
    @JvmInline
    value class Term(
        val array: JSONArray,
    ) {
        constructor(search: String, limit: Int, order: Int) : this(JSONArray(listOf(search, limit, order)))

        /**
         Only cards satisfying this search query are shown.
         */
        var search: String
            get() = array.getString(0)
            set(value) {
                array.put(0, value)
            }

        /**
         * At most this number of cards are shown.
         */
        var limit: Int
            get() = array.getInt(1)
            set(value) {
                array.put(1, value)
            }

        /**
         * The order in which cards are shown. See https://docs.ankiweb.net/filtered-decks.html#order.
         */
        var order: Int
            get() = array.getInt(2)
            set(value) {
                array.put(2, value)
            }

        override fun toString(): String = array.toString()
    }

    /**
     * The options deciding which cards are shown in a filtered deck.
     */
    val firstFilter: Term
        get() = Term(terms.getJSONArray(0))

    /**
     * A second option to add more cards to the filtered deck.
     */
    var secondFilter: Term?
        get() = terms.optJSONArray(1)?.let { Term(it) }
        set(value) {
            if (value == null) {
                terms.remove(1)
            } else {
                terms.put(1, value.array)
            }
        }

    /**
     * The array of filters. Only for filtered decks.
     */
    @VisibleForTesting
    var terms: JSONArray
        get() = jsonObject.getJSONArray("terms")
        set(value) {
            jsonObject.put("terms", value)
        }

    override fun toString(): String = jsonObject.toString()
}
