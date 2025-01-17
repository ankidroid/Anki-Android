/*
 *  Copyright (c) 2024 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.utils

import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.utils.NotInLibAnki
import com.ichi2.libanki.utils.append
import com.ichi2.libanki.utils.index
import com.ichi2.libanki.utils.insert
import com.ichi2.libanki.utils.remove
import org.json.JSONArray
import org.json.JSONObject

@NotInLibAnki
interface JSONObjectHolder {
    @VisibleForTesting val jsonObject: JSONObject
}

@NotInLibAnki
interface JSONContainer<T : JSONObjectHolder> : Iterable<T> {
    val jsonArray: JSONArray

    fun constructor(obj: JSONObject): T

    override fun iterator() =
        jsonArray
            .jsonObjectIterator()
            .asSequence()
            .map(::constructor)
            .iterator()

    operator fun get(index: Int) = constructor(jsonArray.getJSONObject(index))

    /**
     * Sets/replaces the value at [index].
     *
     * This `null` pads this array to the required length if necessary.
     *
     * @see JSONArray.put
     */
    operator fun set(
        index: Int,
        field: T,
    ) {
        jsonArray.put(index, field.jsonObject)
    }

    fun length() = jsonArray.length()

    fun append(template: T) = jsonArray.append(template.jsonObject)

    fun remove(template: T) = jsonArray.remove(template.jsonObject)

    fun index(template: T) = jsonArray.index(template.jsonObject)

    fun insert(
        idx: Int,
        template: T,
    ) = jsonArray.insert(idx, template.jsonObject)
}

fun <T : JSONObjectHolder> len(templates: JSONContainer<T>) = templates.jsonArray.length()
