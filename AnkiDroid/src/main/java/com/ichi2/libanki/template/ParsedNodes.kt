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
import java.util.*

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

    override fun template_is_empty(nonempty_fields: Set<String>): Boolean {
        return !mChildren.any { !it!!.template_is_empty(nonempty_fields) }
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

    override fun equals(other: Any?): Boolean {
        if (other !is ParsedNodes) {
            return false
        }
        return mChildren == other.mChildren
    }

    override fun toString(): String {
        return "new ParsedNodes(listOf(${mChildren.joinToString()}))"
    }

    override fun hashCode(): Int {
        return mChildren.hashCode()
    }

    companion object {
        /**
         * @param nodes A list of nodes to put in a tree
         * @return The list of node, as compactly as possible.
         */
        fun create(nodes: List<ParsedNode>): ParsedNode {
            return when {
                nodes.isEmpty() -> EmptyNode()
                nodes.size == 1 -> nodes[0]
                else -> ParsedNodes(nodes)
            }
        }
    }
}
