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
import java.lang.ref.WeakReference
import java.util.Locale

data class DeckNode(
    val node: DeckTreeNode,
    val fullDeckName: String,
    val parent: WeakReference<DeckNode>? = null,
) : Iterable<DeckNode> {
    // TODO: make this immutable and simplify 'DisplayDeckNode' to contain a 'DeckNode'
    var collapsed = node.collapsed
    val revCount = node.reviewCount
    val newCount = node.newCount
    val lrnCount = node.learnCount
    val did = node.deckId
    val filtered = node.filtered
    val children =
        node.childrenList.map {
            val fullChildName =
                if (fullDeckName.isEmpty()) {
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

    override fun toString(): String =
        String.format(
            Locale.US,
            "%s, %d, %d, %d, %d",
            fullDeckName,
            did,
            revCount,
            lrnCount,
            newCount,
        )

    fun hasCardsReadyToStudy(): Boolean = revCount > 0 || newCount > 0 || lrnCount > 0

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

    override fun iterator(): Iterator<DeckNode> =
        sequence {
            if (node.level > 0) yield(this@DeckNode)
            for (child in children) yieldAll(child)
        }.iterator()
}
