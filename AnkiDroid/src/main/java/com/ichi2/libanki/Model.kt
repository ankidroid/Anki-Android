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
import org.json.JSONObject

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
class Model : JSONObject {
    /**
     * Creates a new empty model object
     */
    constructor() : super()

    /**
     * Creates a copy from [JSONObject] and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     * @see Model.from
     */
    @KotlinCleanup("non-null")
    constructor(json: JSONObject) : super() {
        json.deepClonedInto(this)
    }

    /**
     * Creates a model object form json string
     */
    @KotlinCleanup("non-null")
    constructor(json: String?) : super(json!!) {}

    @CheckResult
    fun deepClone(): Model {
        val clone = Model()
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
     * @return The set of name of non-empty fields.
     */
    @KotlinCleanup("filter")
    fun nonEmptyFields(sfld: Array<String>): Set<String> {
        val fieldNames = fieldsNames
        val nonemptyFields: MutableSet<String> = HashUtil.HashSetInit(sfld.size)
        for (i in sfld.indices) {
            if (sfld[i].trim { it <= ' ' }.isNotEmpty()) {
                nonemptyFields.add(fieldNames[i])
            }
        }
        return nonemptyFields
    }
}
