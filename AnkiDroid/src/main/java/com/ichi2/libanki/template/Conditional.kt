/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.libanki.template

class Conditional(private val key: String, private val child: ParsedNode) : ParsedNode() {
    override fun template_is_empty(nonempty_fields: Set<String>): Boolean {
        return !nonempty_fields.contains(key) || child.template_is_empty(nonempty_fields)
    }

    @Throws(TemplateError::class)
    override fun render_into(fields: Map<String, String>, nonempty_fields: Set<String>, builder: StringBuilder) {
        if (nonempty_fields.contains(key)) {
            child.render_into(fields, nonempty_fields, builder)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Conditional) {
            return false
        }
        val _other = other
        return _other.key == key && _other.child == child
    }

    override fun toString(): String {
        return "new Conditional(\"" + key.replace("\\", "\\\\") + "\"," + child + ")"
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + child.hashCode()
        return result
    }
}
