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

import com.ichi2.anki.utils.ext.getStringOrNull
import com.ichi2.utils.JSONObjectHolder
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONObject

/**
 * A [note type][NotetypeJson] contains 1 to many named fields which define what a user can enter.
 * Fields are combined with [card templates][NotetypeJson.tmpls], to allow the note to generate
 * multiple cards
 *
 * Basic field substitution syntax looks like: `{{Front}}`, where `Front` is defined by [name].
 * This syntax is Anki-specific (similar to [mustache](https://mustache.github.io/mustache.5.html]))
 * and is [documented in the Manual](https://docs.ankiweb.net/templates/fields.html)
 *
 * This class documents field properties, most of which are relevant to inserting/editing notes,
 * with a few related to searching/card rendering
 *
 * In AnkiDroid, this also affects the font and language used in 'type the answer'
 *
 * ### Desktop-only properties
 * TODO: The following properties are used in Anki Desktop (as-of 2024), but not documented
 *  in this class. Accessors should be created.
 *
 * * collapsed
 * * description - text to show inside the field when it's empty
 * * excludeFromSearch
 * * id
 * * plainText
 * * preventDeletion
 * * rtl
 *
 * ## Further Reading
 * [Anki manual - fields](https://docs.ankiweb.net/templates/fields.html)
 * [Anki manual - customizing fields](https://docs.ankiweb.net/editing.html#customizing-fields)
 */
@JvmInline
value class Field(
    override val jsonObject: JSONObject,
) : JSONObjectHolder {
    /**
     * The user-facing name of the field.
     *
     * Card template substitutions use the name (`{{Front}}`, where [name] = `Front`)
     */
    var name: String
        get() = jsonObject.getString("name")
        set(value) {
            jsonObject.put("name", value)
        }

    /** The 0-based ordinal of the field */
    val ord: Int
        get() = jsonObject.getInt("ord")

    /**
     * If `false`, the note editor erases the content of this field after a note is created
     *
     * Usage: 'pin/freeze' a field, so a user does not need to type in duplicate values
     */
    var sticky: Boolean
        get() = jsonObject.getBoolean("sticky")
        set(value) {
            jsonObject.put("sticky", value)
        }

    /**
     * The font used in the note editor when editing the note
     *
     * ⚠️: AnkiDroid also uses this for 'type the answer'
     */
    var font: String
        get() = jsonObject.getString("font")
        set(value) {
            jsonObject.put("font", value)
        }

    /**
     * The font size used in the note editor when editing the note
     *
     * ⚠️: AnkiDroid also uses this for 'type the answer'
     */
    var fontSize: Int
        get() = jsonObject.getInt("size")
        set(value) {
            jsonObject.put("size", value)
        }

    /**
     * @see [anki.notetypes.ImageOcclusionField]
     */
    val imageOcclusionTag: String?
        get() = jsonObject.getStringOrNull("tag")

    /** @see ord */
    @RustCleanup("Check JSONObject.NULL")
    fun setOrd(value: Int?) = jsonObject.put("ord", if (value == null) JSONObject.NULL else value)

    override fun toString() = jsonObject.toString()
}
