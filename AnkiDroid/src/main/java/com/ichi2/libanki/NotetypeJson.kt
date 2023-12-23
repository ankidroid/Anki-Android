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
import com.ichi2.utils.*
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONObject
import java.util.HashSet

/**
 * Represents a note type, a.k.a. Model.
 * The content of an object is described in https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
 * Each time the object is modified, `Models.save(this)` should be called, otherwise the change will not be synchronized
 * If a change affect card generation, (i.e. any change on the list of field, or the question side of a card type),
 * `Models.save(this, true)` should be called. However, you should do the change in batch and change only when all are d
 * one, because recomputing the list of card is an expensive operation.
 */
@KotlinCleanup("fix kotlin docs")
@KotlinCleanup("IDE Lint")
class NotetypeJson : JSONObject {
    /**
     * Creates a new empty model object
     */
    constructor() : super()

    /**
     * Creates a copy from [JSONObject] and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     * @see NotetypeJson.from
     */
    constructor(json: JSONObject) : super() {
        json.deepClonedInto(this)
    }

    /**
     * Creates a model object form json string
     */
    constructor(@Language("json") json: String) : super(json)

    @CheckResult
    fun deepClone(): NotetypeJson {
        val clone = NotetypeJson()
        return deepClonedInto(clone)
    }

    val fieldsNames: List<String>
        get() = getJSONArray("flds").toStringList("name")

    fun getField(pos: Int): JSONObject {
        return getJSONArray("flds").getJSONObject(pos)
    }

    /**
     * @return model did or default deck id (1) if null
     */
    val did: Long
        get() = if (isNull("did")) 1L else getLong("did")
    val templatesNames: List<String>
        get() = getJSONArray("tmpls").toStringList("name")
    val isStd: Boolean
        get() = getInt("type") == Consts.MODEL_STD
    val isCloze: Boolean
        get() = getInt("type") == Consts.MODEL_CLOZE

    /**
     * @param sfld Fields of a note of this note type
     * @return The names of non-empty fields
     */
    fun nonEmptyFields(sfld: Array<String>): Set<String> =
        sfld.zip(fieldsNames)
            // filter to the fields which are non-empty
            .filter { (sfld, _) -> sfld.trim { it <= ' ' }.isNotEmpty() }
            .mapTo(HashSet()) { (_, fieldName) -> fieldName }

    /** Python method
     * https://docs.python.org/3/library/stdtypes.html?highlight=dict#dict.update
     *
     * Update the dictionary with the provided key/value pairs, overwriting existing keys
     */
    fun update(updateFrom: NotetypeJson) {
        for (k in updateFrom.keys()) {
            put(k, updateFrom[k])
        }
    }

    fun deepcopy(): NotetypeJson = NotetypeJson(this.deepClone())

    var flds: JSONArray
        get() = getJSONArray("flds")
        set(value) {
            put("flds", value)
        }

    var tmpls: JSONArray
        get() = getJSONArray("tmpls")
        set(value) {
            put("tmpls", value)
        }

    var id: Long
        get() = getLong("id")
        set(value) {
            put("id", value)
        }

    var name: String
        get() = getString("name")
        set(value) {
            put("name", value)
        }

    /** Integer specifying which field is used for sorting in the browser */
    var sortf: Int
        get() = getInt("sortf")
        set(value) {
            put("sortf", value)
        }

    // TODO: Not constrained
    @Consts.MODEL_TYPE
    var type: Int
        get() = getInt("type")
        set(value) {
            put("type", value)
        }
}
