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

import android.annotation.SuppressLint
import anki.decks.DeckTreeNode
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.utils.append
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * An object of this class represent a [com.ichi2.libanki.Deck], with all information that may be useful for its usage, such as its subdecks and the number of cards to review today.
 * This object can also represent the root of the tree, of depth -1, that is the parent of all top-level decks. All non-root decks are called read decks.
 * No change to this object are retained in the collection. Any change must be made through collections methods such as the ones of [com.ichi2.libanki.Decks].
 *
 * Objects of this type should not be retained, as this could cause them to outlive their parent. The only exception being the above-mentioned root deck that has no parent.
 *
 * The hierarchy is exactly the same as the one returned by [net.ankiweb.rsdroid.Backend.deckTree].
 */
data class DeckNode(
    val node: DeckTreeNode,
    val fullDeckName: String,
    /**
     * Non-null for real decks. The parent of a top-level deck is the root of the tree.
     */
    val parent: WeakReference<DeckNode>? = null,
) : Iterable<DeckNode> {
    /**
     * Whether or not the deck should appear as collapsed in the deck picker.
     * Change to this variable is not retained in the collection. The caller must change the [com.ichi2.libanki.Deck] object with the same id and save it.
     */
    var collapsed = node.collapsed

    /**
     * Number of review cards to review today, as displayed in the deck picker, including count of subdecks.
     */
    val revCount = node.reviewCount

    /**
     * Number of new cards to review today, as displayed in the deck picker, including count of subdecks.
     */
    val newCount = node.newCount

    /**
     * Number of cards in learning to review today, as displayed in the deck picker, including count of subdecks.
     * Note that this value will quite often be smaller than the value that would be returned by the back-end. This is because a card in learning may be due at any second.
     */
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

    /**
     * True if this node corresponds to an actual deck that the user can interact with.
     * False if this is the root of the deck tree.
     */
    private val isRealDeck = node.level > 0

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

    /**
     * Whether we expect that tapping on this deck would allow to start reviewing.
     * Note that this value may have false-negative, because [lrnCount] can increase virtually at any time.
     * False positive are virtually impossible; unless the API is used in the background.
     * Indeed, any other action that would cause a card to be reviewed or deleted should cause the owner of this tree to refresh it.
     */
    fun hasCardsReadyToStudy(): Boolean = revCount > 0 || newCount > 0 || lrnCount > 0

    /**
     * The node with [did] [deckId], if it is either this node or a descendant.
     */
    fun find(deckId: DeckId): DeckNode? {
        if (node.deckId == deckId) {
            return this
        }
        for (child in children) {
            return child.find(deckId) ?: continue
        }
        return null
    }

    /**
     * Run [fn] on this node (unless its the root) and its descendants.
     */
    fun forEach(fn: (DeckNode) -> Unit) {
        if (isRealDeck) {
            fn(this)
        }
        for (child in children) {
            child.forEach(fn)
        }
    }

    /**
     * An iterator returning this deck (unless it's the root) and all descendant.
     */
    override fun iterator(): Iterator<DeckNode> =
        sequence {
            if (isRealDeck) yield(this@DeckNode)
            for (child in children) yieldAll(child)
        }.iterator()

    /**
     * The list of all decks displayed in the deck picker containing the string [filter].
     * That is, all real decks except the descendant of collapsed decks.
     */
    fun filterAndFlatten(filter: CharSequence?): List<DeckNode> {
        val filterPattern =
            if (filter.isNullOrBlank()) {
                null
            } else {
                filter.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
            }
        val list = mutableListOf<DeckNode>()
        filterAndFlattenInner(filterPattern, list)
        return list
    }

    /**
     * Add this deck (if its not the root of the tree) and its visible descendants, to [list]
     * @param filter: if non-null, restricts the list to visible decks matching whose name contains [filter] as a case-independent substring, and the ancestors of those decks.
     */
    private fun filterAndFlattenInner(
        filter: CharSequence?,
        list: MutableList<DeckNode>,
    ) {
        if (isRealDeck && nameMatchesFilter(filter)) {
            // if this deck matched, all visible descendant are included.
            addVisibleToList(list)
            return
        }

        if (collapsed) {
            // We don't show non-visible subdecks even when its name matches the filter.
            return
        }

        if (isRealDeck) {
            list.append(this)
        }
        val startingLen = list.size
        for (child in children) {
            child.filterAndFlattenInner(filter, list)
        }
        if (isRealDeck && startingLen == list.size) {
            // we don't include ourselves if no children matched
            list.removeAt(list.lastIndex)
        }
    }

    /**
     * Whether the base name of the deck (not including the name of its parent) matches [filter].
     * if [filter] is [none], returns [true] as every deck matchs the empty filter.
     * otherwise, [filter] must be a substring (case-insensitive) of the deck name.
     */
    @SuppressLint("LocaleRootUsage")
    private fun nameMatchesFilter(filter: CharSequence?): Boolean {
        return if (filter == null) {
            true
        } else {
            return node.name.lowercase(Locale.getDefault()).contains(filter) || node.name.lowercase(Locale.ROOT).contains(filter)
        }
    }

    /**
     * Add this deck and all visible descendants to [list].
     * A descendant is visible if none of its parents are collapsed.
     */
    fun addVisibleToList(list: MutableList<DeckNode>) {
        list.append(this)
        if (!collapsed) {
            for (child in children) {
                child.addVisibleToList(list)
            }
        }
    }
}
