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

import androidx.annotation.CheckResult
import com.ichi2.utils.deepClonedInto
import org.json.JSONObject

class Deck : JSONObject {
    /**
     * Creates a copy from [JSONObject] and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     * If you want to create a Deck without deepCopy
     * @see Deck.from
     */
    constructor(json: JSONObject) : super() {
        json.deepClonedInto(this)
    }

    /**
     * Creates a deck object form a json string
     */
    constructor(json: String) : super(json)

    /**
     * Creates a new empty deck object
     */
    private constructor() : super()

    @CheckResult
    fun deepClone(): Deck {
        val clone = Deck()
        return deepClonedInto(clone)
    }

    val isDyn: Boolean
        get() = getInt("dyn") == Consts.DECK_DYN
    val isStd: Boolean
        get() = getInt("dyn") == Consts.DECK_STD
    val id: DeckId
        get() = getLong("id")
}
