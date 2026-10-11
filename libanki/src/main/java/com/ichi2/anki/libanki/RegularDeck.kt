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
import com.ichi2.anki.common.json.JSONObjectHolder
import org.json.JSONObject

/**
 * Represents a regular (non-filtered) Deck.
 */
// The name "regular" can be found on https://docs.ankiweb.net/filtered-decks.html
@JvmInline
value class RegularDeck(
    @VisibleForTesting override val jsonObject: JSONObject,
) : JSONObjectHolder,
    Deck {
    /**
     * Creates a deck object form a json string
     */
    constructor(json: String) : this(JSONObject(json))

    /**
     * The id of the deck option.
     */
    var conf: DeckConfigId
        get() {
            val value = jsonObject.optLong("conf")
            return if (value > 0) value else 1
        }
        set(value) {
            jsonObject.put("conf", value)
        }

    var noteTypeId: NoteTypeId?
        get() = jsonObject.getLongOrNull("mid")
        set(value) {
            jsonObject.put("mid", value)
        }
}
