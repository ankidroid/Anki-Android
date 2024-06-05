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

import com.ichi2.utils.deepClonedInto
import org.intellij.lang.annotations.Language
import org.json.JSONObject

class CardTemplateJson : JSONObject {
    /**
     * Creates a new empty model object
     */
    constructor() : super()

    /**
     * Creates a copy from [JSONObject] and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     * @see CardTemplateJson.from
     */
    constructor(json: JSONObject) : super() {
        json.deepClonedInto(this)
    }

    /**
     * Creates a model object form json string
     */
    constructor(@Language("json") json: String) : super(json)

    val name: String
        get() = getString("name")

    val ord: Int
        get() = getInt("ord")

    val qfmt: String
        get() = getString("qfmt")

    val afmt: String
        get() = getString("afmt")

    val bqfmt: String
        get() = getString("bqfmt")

    val bafmt: String
        get() = getString("bafmt")

    val did: Long?
        get() = if (isNull("did")) null else getLong("did")

    val bfont: String
        get() = getString("bfont")

    val bsize: Int
        get() = getInt("bsize")

    val id: Long
        get() = getLong("id")
}
