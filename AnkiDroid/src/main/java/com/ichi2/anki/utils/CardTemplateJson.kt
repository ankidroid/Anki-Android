/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

import com.ichi2.libanki.DeckId
import com.ichi2.utils.JSONObjectHolder
import org.json.JSONObject

@JvmInline
value class CardTemplateJson(
    override val jsonObject: JSONObject,
) : JSONObjectHolder {
    val name: String
        get() = jsonObject.getString("name")

    val ord: Int
        get() = jsonObject.getInt("ord")

    val qfmt: String
        get() = jsonObject.getString("qfmt")

    val afmt: String
        get() = jsonObject.getString("afmt")

    val bqfmt: String
        get() = jsonObject.getString("bqfmt")

    val bafmt: String
        get() = jsonObject.getString("bafmt")

    val did: DeckId?
        get() = if (jsonObject.isNull("did")) null else jsonObject.getLong("did")

    val bfont: String
        get() = jsonObject.getString("bfont")

    val bsize: Int
        get() = jsonObject.getInt("bsize")

    val id: Long
        get() = jsonObject.getLong("id")

    override fun toString(): String = jsonObject.toString()
}
