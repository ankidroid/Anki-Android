/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.template

import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.template.TemplateError.FieldNotFound
import com.ichi2.libanki.template.TemplateFilters.apply_filter
import com.ichi2.utils.KotlinCleanup

@KotlinCleanup("IDE Lint")
class Replacement(
    /**
     * The name of the field to show
     */
    private val key: String,
    /**
     * List of filter to apply (from right to left)
     */
    private val filters: List<String>,
    /**
     * The entire content between {{ and }}
     */
    private val tag: String
) : ParsedNode() {
    // Only used for test
    @VisibleForTesting
    constructor(key: String, vararg filters: String?) : this(
        key,
        filters.map { it!! },
        ""
    ) {
    }

    override fun template_is_empty(nonempty_fields: Set<String>): Boolean {
        return !nonempty_fields.contains(key)
    }

    @Throws(FieldNotFound::class)
    override fun render_into(
        fields: Map<String, String>,
        nonempty_fields: Set<String>,
        builder: StringBuilder
    ) {
        var txt = fields[key]
        if (txt == null) {
            txt = if (key.trim { it <= ' ' }.isEmpty() && !filters.isEmpty()) {
                ""
            } else {
                throw FieldNotFound(filters, key)
            }
        }
        for (filter in filters) {
            txt = apply_filter(txt!!, filter, key, tag)
        }
        builder.append(txt)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Replacement) {
            return false
        }
        return other.key == key && other.filters == filters
    }

    override fun toString(): String {
        return "new Replacement(\"" + key.replace("\\", "\\\\") + ", " + filters + "\")"
    }
}
