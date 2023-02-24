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

import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Decks
import net.ankiweb.rsdroid.RustCleanup
import java.util.*
import kotlin.math.max
import kotlin.math.min

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
class DeckDueTreeNode(
    fullDeckName: String,
    did: DeckId,
    override var revCount: Int,
    override var lrnCount: Int,
    override var newCount: Int,
    // only set when defaultLegacySchema is false
    override var collapsed: Boolean = false,
    override var filtered: Boolean = false
) : AbstractDeckTreeNode(fullDeckName, did, collapsed, filtered) {
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

    private fun limitRevCount(limit: Int) {
        revCount = max(0, min(revCount, limit))
    }

    private fun limitNewCount(limit: Int) {
        newCount = max(0, min(newCount, limit))
    }

    override fun processChildren(col: Collection, children: List<AbstractDeckTreeNode>, addRev: Boolean) {
        // tally up children counts
        for (ch in children) {
            lrnCount += ch.lrnCount
            newCount += ch.newCount
            if (addRev) {
                revCount += ch.revCount
            }
        }
        // limit the counts to the deck's limits
        val conf = col.decks.confForDid(did)
        if (conf.isStd) {
            val deck = col.decks.get(did)
            limitNewCount(conf.getJSONObject("new").getInt("perDay") - deck.getJSONArray("newToday").getInt(1))
            if (addRev) {
                limitRevCount(conf.getJSONObject("rev").getInt("perDay") - deck.getJSONArray("revToday").getInt(1))
            }
        }
    }

    override fun hashCode(): Int {
        return fullDeckName.hashCode() + revCount + lrnCount + newCount
    }

    /**
     * Whether both elements have the same structure and numbers.
     * @param other
     * @return
     */
    override fun equals(other: Any?): Boolean {
        if (other !is DeckDueTreeNode) {
            return false
        }
        return Decks.equalName(fullDeckName, other.fullDeckName) &&
            revCount == other.revCount &&
            lrnCount == other.lrnCount &&
            newCount == other.newCount
    }

    /** Line representing this string without its children. Used in timbers only.  */
    override fun toStringLine(): String {
        return String.format(
            Locale.US,
            "%s, %d, %d, %d, %d\n",
            fullDeckName,
            did,
            revCount,
            lrnCount,
            newCount
        )
    }

    override fun shouldDisplayCounts(): Boolean {
        return true
    }

    override fun knownToHaveRep(): Boolean {
        return revCount > 0 || newCount > 0 || lrnCount > 0
    }
}

/** Locate node with a given deck ID in a list of nodes.
 *
 * This could be converted into a method if AnkiDroid returned a top-level
 * node instead of a list of nodes.
 */
fun findInDeckTree(nodes: List<TreeNode<DeckDueTreeNode>>, deckId: Long): DeckDueTreeNode? {
    for (node in nodes) {
        if (node.value.did == deckId) {
            return node.value
        }
        return findInDeckTree(node.children, deckId) ?: continue
    }
    return null
}
