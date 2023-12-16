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
package com.ichi2.libanki.sched

import anki.decks.DeckTreeNode
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.utils.append
import java.lang.ref.WeakReference
import java.util.*

data class DeckNode(
    val node: DeckTreeNode,
    val fullDeckName: String,
    val parent: WeakReference<DeckNode>? = null,
) {
    var collapsed = node.collapsed
    val revCount = node.reviewCount
    val newCount = node.newCount
    val lrnCount = node.learnCount
    val did = node.deckId
    val filtered = node.filtered
    val children = node.childrenList.map {
        val fullChildName = if (fullDeckName.isEmpty()) {
            it.name
        } else {
            "$fullDeckName::${it.name}"
        }
        DeckNode(it, fullChildName, WeakReference(this@DeckNode))
    }

    /**
     * The part of the name displayed in deck picker, i.e. the
     * part that does not belong to its parents. E.g.  for deck
     * "A::B::C", returns "C".
     */
    val lastDeckNameComponent: String = node.name

    /**
     * @return The depth of a deck. Top level decks have depth 0,
     * their children have depth 1, etc... So "A::B::C" would have
     * depth 2.
     */
    val depth = node.level - 1

    override fun toString(): String {
        return String.format(
            Locale.US,
            "%s, %d, %d, %d, %d",
            fullDeckName,
            did,
            revCount,
            lrnCount,
            newCount
        )
    }

    fun hasCardsReadyToStudy(): Boolean {
        return revCount > 0 || newCount > 0 || lrnCount > 0
    }

    fun find(deckId: DeckId): DeckNode? {
        if (node.deckId == deckId) {
            return this
        }
        for (child in children) {
            return child.find(deckId) ?: continue
        }
        return null
    }

    fun forEach(fn: (DeckNode) -> Unit) {
        if (node.level > 0) {
            fn(this)
        }
        for (child in children) {
            child.forEach(fn)
        }
    }

    /** Convert the tree into a flat list, where matching decks and the children/parents
     * are included. Decks inside collapsed decks are not considered. */
    fun filterAndFlatten(filter: CharSequence?): List<DeckNode> {
        val filterPattern = if (filter.isNullOrBlank()) { null } else {
            filter.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
        }
        val list = mutableListOf<DeckNode>()
        filterAndFlattenInner(filterPattern, list)
        return list
    }

    private fun filterAndFlattenInner(filter: CharSequence?, list: MutableList<DeckNode>) {
        if (node.level > 0 && nameMatchesFilter(filter)) {
            // if this deck matched, all children are included
            addVisibleToList(list)
            return
        }

        if (collapsed) {
            return
        }

        if (node.level > 0) {
            list.append(this)
        }
        val startingLen = list.size
        for (child in children) {
            child.filterAndFlattenInner(filter, list)
        }
        if (node.level > 0 && startingLen == list.size) {
            // we don't include ourselves if no children matched
            list.removeLast()
        }
    }

    private fun nameMatchesFilter(filter: CharSequence?): Boolean {
        return if (filter == null) {
            true
        } else {
            return node.name.lowercase(Locale.getDefault()).contains(filter) || node.name.lowercase(Locale.ROOT).contains(filter)
        }
    }

    fun addVisibleToList(list: MutableList<DeckNode>) {
        list.append(this)
        if (!collapsed) {
            for (child in children) {
                child.addVisibleToList(list)
            }
        }
    }
}
