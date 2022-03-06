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
import com.ichi2.libanki.Decks
import com.ichi2.utils.KotlinCleanup
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
@KotlinCleanup("maybe possible to remove gettres for revCount/lrnCount")
class DeckDueTreeNode(col: Collection?, name: String?, did: Long, private var revCount: Int, private var lrnCount: Int, private var newCount: Int) : AbstractDeckTreeNode<DeckDueTreeNode?>(col, name, did) {
    override fun toString(): String {
        return String.format(
            Locale.US, "%s, %d, %d, %d, %d, %s",
            fullDeckName, did, revCount, lrnCount, newCount, children
        )
    }

    override fun getRevCount(): Int {
        return revCount
    }

    private fun limitRevCount(limit: Int) {
        revCount = max(0, min(revCount, limit))
    }

    override fun getNewCount(): Int {
        return newCount
    }

    private fun limitNewCount(limit: Int) {
        newCount = max(0, min(newCount, limit))
    }

    override fun getLrnCount(): Int {
        return lrnCount
    }

    @KotlinCleanup("non-null")
    override fun setChildren(children: MutableList<DeckDueTreeNode?>, addRev: Boolean) {
        super.setChildren(children, addRev)
        // tally up children counts
        for (ch in children) {
            lrnCount += ch!!.lrnCount
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
        val childrenHash = if (children == null) 0 else children.hashCode()
        return fullDeckName.hashCode() + revCount + lrnCount + newCount + childrenHash
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
            newCount == other.newCount &&
            (
                children == other.children || // Would be the case if both are null, or the same pointer
                    children.equals(other.children)
                )
    }

    /** Line representing this string without its children. Used in timbers only.  */
    override fun toStringLine(): String {
        return String.format(
            Locale.US, "%s, %d, %d, %d, %d\n",
            fullDeckName, did, revCount, lrnCount, newCount
        )
    }

    override fun shouldDisplayCounts(): Boolean {
        return true
    }

    override fun knownToHaveRep(): Boolean {
        return revCount > 0 || newCount > 0 || lrnCount > 0
    }

    private fun setChildrenSuper(children: MutableList<DeckDueTreeNode?>) {
        super.setChildren(children, false)
    }

    override fun withChildren(children: MutableList<DeckDueTreeNode?>): DeckDueTreeNode {
        val name = fullDeckName
        val did = did
        val node = DeckDueTreeNode(col, name, did, revCount, lrnCount, newCount)
        // We've already calculated the counts, don't bother doing it again, just set the variable.
        node.setChildrenSuper(children)
        return node
    }
}
