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
import java.lang.UnsupportedOperationException
import java.util.*

/**
 * Holds the data for a single node (row) in the deck due tree (the user-visible list
 * of decks and their counts).
 *
 * The names field is an array of names that build a deck name from a hierarchy (i.e., a nested
 * deck will have an entry for every level of nesting). While the python version interchanges
 * between a string and a list of strings throughout processing, we always use an array for
 * this field and use getNamePart(0) for those cases.
 *
 * [processChildren] should be called if the children of this node are modified.
 */
abstract class AbstractDeckTreeNode(
    val col: Collection,
    /**
     * @return The full deck name, e.g. "A::B::C"
     */
    val fullDeckName: String,
    val did: Long
) : Comparable<AbstractDeckTreeNode> {
    private val mNameComponents: Array<String>

    /**
     * Sort on the head of the node.
     */
    override fun compareTo(other: AbstractDeckTreeNode): Int {
        val minDepth = Math.min(depth, other.depth) + 1
        // Consider each subdeck name in the ordering
        for (i in 0 until minDepth) {
            val cmp = mNameComponents[i].compareTo(other.mNameComponents[i])
            if (cmp == 0) {
                continue
            }
            return cmp
        }
        // If we made it this far then the arrays are of different length. The longer one should
        // always come after since it contains all of the sections of the shorter one inside it
        // (i.e., the short one is an ancestor of the longer one).
        return Integer.compare(depth, other.depth)
    }

    /** Line representing this string without its children. Used in timbers only.  */
    protected open fun toStringLine(): String? {
        return String.format(
            Locale.US, "%s, %d",
            fullDeckName, did
        )
    }

    abstract fun processChildren(children: List<AbstractDeckTreeNode>, addRev: Boolean)

    override fun toString(): String {
        val buf = StringBuffer()
        toString(buf)
        return buf.toString()
    }

    protected fun toString(buf: StringBuffer) {
        for (i in 0 until depth) {
            buf.append("  ")
        }
        buf.append(toStringLine())
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

    override fun hashCode(): Int {
        return fullDeckName.hashCode()
    }

    /**
     * Whether both elements have the same structure and numbers.
     * @param other
     * @return
     */
    override fun equals(other: Any?): Boolean {
        if (other !is AbstractDeckTreeNode) {
            return false
        }
        return Decks.equalName(fullDeckName, other.fullDeckName)
    }

    open fun shouldDisplayCounts(): Boolean {
        return false
    }

    /* Number of new cards to see today known to be in this deck and its descendants. The number to show to user*/
    open var newCount: Int = 0
        get() {
            throw UnsupportedOperationException()
        }

    /* Number of lrn cards (or repetition) to see today known to be in this deck and its descendants. The number to show to user*/
    open var lrnCount: Int = 0
        get() {
            throw UnsupportedOperationException()
        }

    /* Number of rev cards to see today known to be in this deck and its descendants. The number to show to user*/
    open var revCount: Int = 0
        get() {
            throw UnsupportedOperationException()
        }

    open fun knownToHaveRep(): Boolean {
        return false
    }

    init {
        mNameComponents = Decks.path(fullDeckName)
    }
}
