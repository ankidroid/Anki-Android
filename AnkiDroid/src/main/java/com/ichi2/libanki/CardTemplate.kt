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

import com.ichi2.utils.JSONObjectHolder
import com.ichi2.utils.deepClone
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONObject

/**
 * A card template is a HTML template, combined with [fields][Field] to produce [cards][Card].
 * Since templates exist on the [note][Note]-level, each template can be used to bulk-change the
 * styling/displayed fields of cards it  generates from the associated [note type][NotetypeJson].
 *
 * In a standard note, 1 template maps to at most 1 card if [requirements][NotetypeJson.req] are met
 * In a cloze note, 1 template maps to 1 to many cards
 *
 * An example of the template HTML: [afmt] of a 'Basic' Note
 * ```
 * {{FrontSide}}
 *
 * <hr id=answer>
 *
 * {{Back}}
 * ```
 *
 * The template also defines 'browser' fields: [bqfmt], [bafmt], `bfont` & `bsize` which affect how
 * [Browse][com.ichi2.anki.CardBrowser] displays the field. Typically used to remove extraneous
 * information, making it easier to find cards. See: https://www.youtube.com/shorts/JbxNP4pSIBA
 *
 * ## Additional Properties
 * TODO: `did` (Deck Override) is used in AnkiDroid and should be documented.
 *  This property should always be set
 *
 * TODO: Anki Desktop (2024) defines the following properties which this class should document for
 *  completeness
 * * bfont: '' - the font used in Browse
 * * bsize: 0 - the font size used in Browse
 * * id: 1556256845231882422
 *
 * ## Links
 * * [Anki Manual](https://docs.ankiweb.net/templates/intro.html)
 */
@JvmInline
value class CardTemplate(
    override val jsonObject: JSONObject,
) : JSONObjectHolder {
    /**
     * The user-facing name of the template
     *
     * By default, Anki uses `Card 1`
     */
    var name: String
        get() = jsonObject.getString("name")
        set(value) {
            jsonObject.put("name", value)
        }

    /** The 0-based ordinal of the template */
    val ord: Int
        get() = jsonObject.getInt("ord")

    /**
     * Format string for the question when reviewing
     *
     * Example:
     *
     * ```
     * {{Front}}
     * ```
     */
    var qfmt: String
        get() = jsonObject.getString("qfmt")
        set(value) {
            jsonObject.put("qfmt", value)
        }

    /**
     * Format string for the answer when reviewing
     *
     * Example
     * ```
     * {{FrontSide}}
     *
     * <hr id=answer>
     *
     * {{Back}}
     * ```
     */
    var afmt: String
        get() = jsonObject.getString("afmt")
        set(value) {
            jsonObject.put("afmt", value)
        }

    /**
     * Format string for the question of the card **when displayed in Browse**
     */
    var bqfmt: String
        get() = jsonObject.getString("bqfmt")
        set(value) {
            jsonObject.put("bqfmt", value)
        }

    /**
     * Format string for the answer of the card **when displayed in Browse**
     */
    var bafmt: String
        get() = jsonObject.getString("bafmt")
        set(value) {
            jsonObject.put("bafmt", value)
        }

    /** @see ord */
    @RustCleanup("Check JSONObject.NULL")
    fun setOrd(value: Int?) = jsonObject.put("ord", if (value == null) JSONObject.NULL else value)

    fun deepClone() = CardTemplate(jsonObject.deepClone())

    override fun toString() = jsonObject.toString()
}
