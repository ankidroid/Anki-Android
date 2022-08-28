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

import com.ichi2.utils.KotlinCleanup

@KotlinCleanup("fix hashCode related @Suppress")
@Suppress("EqualsOrHashCode")
class NegatedConditional(private val key: String, private val child: ParsedNode) : ParsedNode() {
    override fun template_is_empty(nonempty_fields: Set<String>): Boolean {
        return nonempty_fields.contains(key) || child.template_is_empty(nonempty_fields)
    }

    @Throws(TemplateError::class)
    override fun render_into(
        fields: Map<String, String>,
        nonempty_fields: Set<String>,
        builder: StringBuilder
    ) {
        if (!nonempty_fields.contains(key)) {
            child.render_into(fields, nonempty_fields, builder)
        }
    }

    @KotlinCleanup("fix parameter name issue")
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun equals(obj: Any?): Boolean {
        if (obj !is NegatedConditional) {
            return false
        }
        val other = obj
        return other.key == key && other.child == child
    }

    override fun toString(): String {
        @KotlinCleanup("this seems like java")
        return "new NegatedConditional(\"" + key.replace("\\", "\\\\") + "," + child + "\")"
    }
}
