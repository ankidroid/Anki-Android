/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki.sched

import com.ichi2.utils.KotlinCleanup

/**
 * Defines a node in a tree with a known, non-null value: [value].
 * [children] contain the children (may be empty, but non-null)
 */
data class TreeNode<T : Any>(val value: T) {
    fun hasChildren(): Boolean = children.any()
    val children: MutableList<TreeNode<T>> = mutableListOf()

    /** UNSAFE. Casts the tree to [U] */
    @KotlinCleanup("should be removable after DeckPicker is converted")
    @Suppress("UNCHECKED_CAST")
    fun <U : Any> unsafeCastToType(): TreeNode<U> {
        return TreeNode(this.value as U).also {
            it.children.addAll(this.children.map { child -> child.unsafeCastToType() })
        }
    }

    /** UNSAFE. Casts the tree to [U] */
    @KotlinCleanup("should be removable after DeckPicker is converted")
    @Suppress("UNUSED_PARAMETER")
    fun <U : Any> unsafeCastToType(unused: Class<U>): TreeNode<U> {
        return unsafeCastToType()
    }

    override fun toString(): String = "$value, $children"
}
