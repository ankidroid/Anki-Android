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
import com.ichi2.utils.toStringList
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
class NotetypeJson : JSONObject {
    /**
     * Creates a new empty model object
     */
    constructor() : super()

    /**
     * Creates a deep copy from [JSONObject].
     */
    constructor(json: JSONObject) : super() {
        json.deepClonedInto(this)
    }

    /**
     * Creates a model object from json string
     */
    constructor(
        @Language("json") json: String,
    ) : super(json)

    @CheckResult
    fun deepClone(): NotetypeJson {
        val clone = NotetypeJson()
        return deepClonedInto(clone)
    }

    val fieldsNames: List<String>
        get() = flds.map { it.name }

    fun getField(pos: Int): Field = flds[pos]

    /**
     * @return model did or default deck id (1) if null
     */
    val did: DeckId
        get() = if (isNull("did")) 1L else getLong("did")
    val templatesNames: List<String>
        get() = getJSONArray("tmpls").toStringList("name")
    val isStd: Boolean
        get() = type == NoteTypeKind.Std
    val isCloze: Boolean
        get() = type == NoteTypeKind.Cloze

    /**
     * @param sfld Fields of a note of this note type
     * @return The names of non-empty fields
     */
    fun nonEmptyFields(sfld: Array<String>): Set<String> =
        sfld
            .zip(fieldsNames)
            // filter to the fields which are non-empty
            .filter { (sfld, _) -> sfld.trim { it <= ' ' }.isNotEmpty() }
            .mapTo(HashSet()) { (_, fieldName) -> fieldName }

    /**
     * Python method
     * [dict.update](https://docs.python.org/3/library/stdtypes.html?highlight=dict#dict.update)
     *
     * Update the dictionary with the provided key/value pairs, overwriting existing keys
     */
    fun update(updateFrom: NotetypeJson) {
        for (k in updateFrom.keys()) {
            put(k, updateFrom[k])
        }
    }

    var flds: Fields
        get() = Fields(getJSONArray("flds"))
        set(value) {
            put("flds", value.jsonArray)
        }

    var tmpls: CardTemplates
        get() = CardTemplates(getJSONArray("tmpls"))
        set(value) {
            put("tmpls", value.jsonArray)
        }

    var id: NoteTypeId
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

    var type: NoteTypeKind
        get() = NoteTypeKind.fromCode(getInt("type"))
        set(value) {
            put("type", value.code)
        }

    /**
     * Defines the requirements for generating cards (for [standard note types][Consts.MODEL_STD])
     *
     * A requirement states that either one of, or all of a set of fields must be non-empty to
     * generate a card using a template. Meaning for a standard note, each template has a
     * requirement, which generates 0 or 1 cards
     *
     * **Example - Basic (optional reversed card):**
     *
     * * Fields: `["Front", "Back", "Add Reverse"]`
     * * `req: [[0, 'any', [0]], [1, 'all', [1, 2]]]`
     *
     * meaning:
     *
     * * Card 1 needs "Front" to be non-empty
     * * Card 2 needs both "Back" and "Add Reverse" to be non-empty
     *
     * The array is of the form `[T, string, list]`, where:
     * - `T` is the ordinal of the template.
     * - `string` is 'none', 'all' or 'any'.
     * - `list` contains ordinals of fields, in increasing order.
     *
     * The output is defined based on the `string`:
     * - if `"none"'`, no cards are generated for this template. `list` should be empty.
     * - if `"all"'`, the card is generated if all fields in `list` are non-empty
     * - if `"any"'`, the card is generated if any field in `list` is non-empty.
     *
     * See [The algorithm to decide how to compute req from the template]
     * (https://github.com/Arthur-Milchior/anki/blob/commented/documentation//templates_generation_rules.md) is explained on:
     */
    @Deprecated(
        "req is no longer used. Exists for backwards compatibility:" +
            "https://forums.ankiweb.net/t/is-req-still-used-or-present/9977",
    )
    var req: JSONArray
        get() = getJSONArray("req")
        set(value) {
            put("req", value)
        }
}
