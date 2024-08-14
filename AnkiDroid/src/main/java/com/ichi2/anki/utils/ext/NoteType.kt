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

package com.ichi2.anki.utils.ext

import anki.notetypes.StockNotetype.OriginalStockKind.ORIGINAL_STOCK_KIND_IMAGE_OCCLUSION_VALUE
import com.ichi2.anki.utils.CardTemplateJson
import com.ichi2.libanki.NotetypeJson
import com.ichi2.utils.jsonObjectIterable
import org.json.JSONException

/**
 * @throws JSONException if the mapping doesn't exist or cannot be coerced to an int.
 */
val NotetypeJson.isImageOcclusion: Boolean
    get() = try {
        getInt("originalStockKind") == ORIGINAL_STOCK_KIND_IMAGE_OCCLUSION_VALUE
    } catch (e: JSONException) {
        false
    }

/**
 * Regular expression pattern for extracting cloze text fields.
 */
private val clozeRegex = "\\{\\{(?:.*?:)?cloze:([^}]*)\\}\\}".toRegex()

val NotetypeJson.templates: List<CardTemplateJson>
    get() = getJSONArray("tmpls").jsonObjectIterable().map { CardTemplateJson(it) }

fun NotetypeJson.getAllClozeTextFields(): List<String> {
    if (!this.isCloze) {
        throw IllegalStateException("getAllClozeTextFields called on non-cloze template")
    }

    val questionFormat = templates.single().qfmt
    return clozeRegex.findAll(questionFormat).map { it.groups[1]!!.value }.toList()
}
