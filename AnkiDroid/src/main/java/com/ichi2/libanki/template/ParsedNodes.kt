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
import com.ichi2.utils.KotlinCleanup
import java.util.*

@KotlinCleanup("fix hashCode issue suppression")
@Suppress("EqualsOrHashCode")
class ParsedNodes : ParsedNode {
    private val mChildren: List<ParsedNode?>

    @VisibleForTesting
    constructor(nodes: List<ParsedNode?>) {
        mChildren = nodes
    }

    // Only used for testing
    @VisibleForTesting
    constructor(vararg nodes: ParsedNode?) {
        mChildren = ArrayList(listOf(*nodes))
    }

    @KotlinCleanup("simplify fun with any {}")
    override fun template_is_empty(nonempty_fields: Set<String>): Boolean {
        for (child in mChildren) {
            if (!child!!.template_is_empty(nonempty_fields)) {
                return false
            }
        }
        return true
    }

    @Throws(TemplateError::class)
    override fun render_into(
        fields: Map<String, String>,
        nonempty_fields: Set<String>,
        builder: StringBuilder
    ) {
        for (child in mChildren) {
            child!!.render_into(fields, nonempty_fields, builder)
        }
    }

    @KotlinCleanup("fix parameter name issue")
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun equals(obj: Any?): Boolean {
        if (obj !is ParsedNodes) {
            return false
        }
        return mChildren == obj.mChildren
    }

    @KotlinCleanup("see if it can be removed if not simplify string and function")
    override fun toString(): String {
        var t: String? = "new ParsedNodes(listOf("
        for (child in mChildren) {
            t += child
        }
        return "$t))"
    }

    companion object {
        /**
         * @param nodes A list of nodes to put in a tree
         * @return The list of node, as compactly as possible.
         */
        fun create(nodes: List<ParsedNode>): ParsedNode {
            @KotlinCleanup("replace with when")
            return if (nodes.isEmpty()) {
                EmptyNode()
            } else if (nodes.size == 1) {
                nodes[0]
            } else {
                ParsedNodes(nodes)
            }
        }
    }
}
