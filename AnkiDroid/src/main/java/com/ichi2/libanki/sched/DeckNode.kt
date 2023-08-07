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

import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Decks
import net.ankiweb.rsdroid.RustCleanup
import java.util.*

/**
 * Holds the data for a single node (row) in the deck due tree (the user-visible list
 * of decks and their counts). A node also contains a list of nodes that refer to the
 * next level of sub-decks for that particular deck (which can be an empty list).
 *
 * The names field is an array of names that build a deck name from a hierarchy (i.e., a nested
 * deck will have an entry for every level of nesting). While the python version interchanges
 * between a string and a list of strings throughout processing, we always use an array for
 * this field and use getNamePart(0) for those cases.
 */
@RustCleanup("after migration, consider dropping this and using backend tree structure directly")
data class DeckNode(
    val fullDeckName: String,
    val did: DeckId,
    var revCount: Int = 0,
    var lrnCount: Int = 0,
    var newCount: Int = 0,
    var collapsed: Boolean = false,
    var filtered: Boolean = false
) {
    private val mNameComponents: Array<String>

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

    fun knownToHaveRep(): Boolean {
        return revCount > 0 || newCount > 0 || lrnCount > 0
    }

    /**
     * For deck "A::B::C", `getDeckNameComponent(0)` returns "A",
     * `getDeckNameComponent(1)` returns "B", etc...
     */
    fun getDeckNameComponent(part: Int): String {
        return mNameComponents[part]
    }

    /**
     * The part of the name displayed in deck picker, i.e. the
     * part that does not belong to its parents. E.g.  for deck
     * "A::B::C", returns "C".
     */
    val lastDeckNameComponent: String
        get() = getDeckNameComponent(depth)

    /**
     * @return The depth of a deck. Top level decks have depth 0,
     * their children have depth 1, etc... So "A::B::C" would have
     * depth 2.
     */
    val depth: Int
        get() = mNameComponents.size - 1

    init {
        mNameComponents = Decks.legacyPath(fullDeckName)
    }
}

/** Locate node with a given deck ID in a list of nodes.
 *
 * This could be converted into a method if AnkiDroid returned a top-level
 * node instead of a list of nodes.
 */
fun findInDeckTree(nodes: List<TreeNode<DeckNode>>, deckId: Long): DeckNode? {
    for (node in nodes) {
        if (node.value.did == deckId) {
            return node.value
        }
        return findInDeckTree(node.children, deckId) ?: continue
    }
    return null
}
