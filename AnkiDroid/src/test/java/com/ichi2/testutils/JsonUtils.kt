/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONStringer

object JsonUtils {
    /**
     * Returns the string, using a well-defined order for the keys
     * COULD_BE_BETTER: This would be much better as a Matcher for JSON
     * COULD_BE_BETTER: Only handles one level of ordering
     */
    fun JSONObject.toOrderedString(): String {
        val stringer = JSONStringer()
        writeTo(stringer)
        return stringer.toString()
    }

    fun JSONArray.toOrderedString(): String {
        val stringer = JSONStringer()
        writeTo(stringer)
        return stringer.toString()
    }

    private fun JSONArray.values() = sequence {
        for (i in 0 until this@values.length()) {
            yield(this@values[i])
        }
    }

    private fun JSONArray.writeTo(stringer: JSONStringer) {
        stringer.array()
        for (value in this.values()) {
            stringer.value(toOrderedString(value))
        }
        stringer.endArray()
    }

    private fun JSONObject.iterateEntries() = sequence {
        for (k in this@iterateEntries.keys()) {
            yield(Pair(k, this@iterateEntries.get(k)))
        }
    }

    @Throws(JSONException::class)
    private fun JSONObject.writeTo(stringer: JSONStringer) {
        stringer.`object`()
        for ((key, value) in this.iterateEntries().toList().sortedBy { x -> x.first }) {
            stringer.key(key).value(toOrderedString(value))
        }
        stringer.endObject()
    }

    private fun toOrderedString(value: Any): Any {
        return when (value) {
            is JSONObject -> { JSONObject(value.toOrderedString()) }
            is JSONArray -> { JSONArray(value.toOrderedString()) }
            else -> { value }
        }
    }
}
