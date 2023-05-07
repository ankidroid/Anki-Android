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

/**
 * Defines a node in a tree with a known, non-null value: [value].
 * [children] contain the children (may be empty, but non-null)
 */
data class TreeNode<T : Any>(val value: T) {
    fun hasChildren(): Boolean = children.any()
    val children: MutableList<TreeNode<T>> = mutableListOf()
    override fun toString(): String = "$value, $children"

    /**
     * Flattens the tree to a list and provides a child -> parent association.
     * @return A sequence of pairs. `first` is a node in the tree. `second` is the node's parent
     * in the tree, or `null` if the node is a root.
     */
    private fun flattenWithParent(parent: T? = null): Sequence<Pair<T, T?>> = sequence {
        val currentNode = this@TreeNode
        yield(Pair(currentNode.value, parent))
        for (child in currentNode.children) {
            yieldAll(child.flattenWithParent(parent = currentNode.value))
        }
    }

    fun associateNodeWithParent(): Map<T, T?> = flattenWithParent().toMap()
}

// https://stackoverflow.com/a/6831626
fun <T : Any> List<TreeNode<T>>.associateNodeWithParent(): Map<T, T?> {
    return this.map { it.associateNodeWithParent() }
        .flatMap { map -> map.entries }
        .associate(Map.Entry<T, T?>::toPair)
}
