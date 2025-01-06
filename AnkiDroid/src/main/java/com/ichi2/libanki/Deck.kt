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

import com.ichi2.libanki.utils.getLongOrNull
import com.ichi2.utils.JSONObjectHolder
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONObject.NULL

@JvmInline
value class Deck(
    override val jsonObject: JSONObject,
) : JSONObjectHolder {
    /**
     * Creates a deck object form a json string
     */
    constructor(json: String) : this(JSONObject(json))

    val isFiltered: Boolean
        get() = jsonObject.getInt("dyn") != 0

    val isNormal: Boolean
        get() = !isFiltered

    var name: String
        get() = jsonObject.getString("name")
        set(value) {
            jsonObject.put("name", value)
        }

    var browserCollapsed: Boolean
        get() = jsonObject.getBoolean("browserCollapsed")
        set(value) {
            jsonObject.put("browserCollapsed", value)
        }

    var collapsed: Boolean
        get() = jsonObject.getBoolean("collapsed")
        set(value) {
            jsonObject.put("collapsed", value)
        }

    var id: DeckId
        get() = jsonObject.getLong("id")
        set(value) {
            jsonObject.put("id", value)
        }

    var conf: Long
        get() {
            val value = jsonObject.optLong("conf")
            return if (value > 0) value else 1
        }
        set(value) {
            jsonObject.put("conf", value)
        }

    var desc: String
        get() = jsonObject.optString("desc")
        set(value) {
            jsonObject.put("desc", value)
        }

    var mid: NoteTypeId?
        get() = jsonObject.getLongOrNull("mid")
        set(value) {
            jsonObject.put("mid", value)
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

    @JvmInline
    value class Term(
        val array: JSONArray,
    ) {
        var search: String
            get() = array.getString(0)
            set(value) {
                array.put(0, value)
            }
        var limit: String
            get() = array.getString(1)
            set(value) {
                array.put(1, value)
            }
        var order: Int
            get() = array.getInt(2)
            set(value) {
                array.put(2, value)
            }
    }

    val firstFilter: Term
        get() = Term(terms.getJSONArray(0))

    var secondFilter: Term?
        get() = terms.optJSONArray(1)?.let { Term(it) }
        set(value) {
            terms.put(2, value?.array)
        }

    fun removeSecondFilter() = terms.remove(1)

    val terms: JSONArray
        get() = jsonObject.getJSONArray("terms")
}
