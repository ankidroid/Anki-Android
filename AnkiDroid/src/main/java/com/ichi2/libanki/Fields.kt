/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki

import com.ichi2.libanki.utils.append
import com.ichi2.libanki.utils.index
import com.ichi2.libanki.utils.insert
import com.ichi2.libanki.utils.remove
import com.ichi2.utils.jsonObjectIterator
import org.json.JSONArray

/** A collection of [Field] */
@JvmInline
value class Fields(
    val jsonArray: JSONArray,
) : Iterable<Field> {
    override fun iterator() =
        jsonArray
            .jsonObjectIterator()
            .asSequence()
            .map(::Field)
            .iterator()

    operator fun get(index: Int) = Field(jsonArray.getJSONObject(index))

    /**
     * Sets/replaces the value at [index].
     *
     * This `null` pads this array to the required length if necessary.
     *
     * @see JSONArray.put
     */
    operator fun set(
        index: Int,
        field: Field,
    ) {
        jsonArray.put(index, field.jsonObject)
    }

    /**
     * @see NotetypeJson.fieldsNames
     */
    @Suppress("unused")
    val fieldsNames: List<String>
        get() = this.map { it.name }

    fun length() = jsonArray.length()

    fun append(field: Field) = jsonArray.append(field.jsonObject)

    fun remove(field: Field) = jsonArray.remove(field.jsonObject)

    fun index(field: Field) = jsonArray.index(field.jsonObject)

    fun insert(
        idx: Int,
        field: Field,
    ) = jsonArray.insert(idx, field.jsonObject)
}

fun len(fields: Fields): Long = fields.jsonArray.length().toLong()
