/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 Gautam Bhetanabhotla <gautamarcturus@gmail.com>
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

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.utils.append
import java.util.Locale

/**
 * An immutable variant of a [DeckNode]. Instantiated right before
 * we want to display it. The list being submitted to the [ListViewAdapter]
 * is a list of [DisplayDeckNode]s. This class only contains the information
 * needed to display it on the screen, hence no data of a node's children and parent.
 */
data class DisplayDeckNode(
    val did: DeckId,
    val fullDeckName: String,
    val lastDeckNameComponent: String,
    val collapsed: Boolean,
    val canCollapse: Boolean,
    val depth: Int,
    val filtered: Boolean,
    val newCount: Int,
    val lrnCount: Int,
    val revCount: Int,
) {
    // DeckNode is mutable, so use a lateinit var so '==' doesn't include it in the comparison
    lateinit var deckNode: DeckNode

    companion object {
        fun from(
            node: DeckNode,
            matchesSearchOrChild: Boolean,
        ): DisplayDeckNode =
            DisplayDeckNode(
                did = node.did,
                fullDeckName = node.fullDeckName,
                lastDeckNameComponent = node.lastDeckNameComponent,
                collapsed = node.collapsed,
                canCollapse = node.children.any() && matchesSearchOrChild,
                depth = node.depth,
                filtered = node.filtered,
                newCount = node.newCount,
                lrnCount = node.lrnCount,
                revCount = node.revCount,
            ).apply {
                this.deckNode = node
            }
    }
}

/** Convert the tree into a flat list of [DisplayDeckNode]s, where matching decks and the children/parents
 * are included. Decks inside collapsed decks are not considered. */
fun DeckNode.filterAndFlattenDisplay(filter: CharSequence?): List<DisplayDeckNode> {
    val filterPattern =
        if (filter.isNullOrBlank()) {
            null
        } else {
            filter.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
        }
    val list = mutableListOf<DisplayDeckNode>()
    filterAndFlattenDisplayInner(filterPattern, list, false)
    return list
}

private fun DeckNode.filterAndFlattenDisplayInner(
    filter: CharSequence?,
    list: MutableList<DisplayDeckNode>,
    parentMatched: Boolean,
) {
    if (!isSyntheticDeck && (nameMatchesFilter((filter)) || parentMatched)) {
        addVisibleToList(list, true)
        return
    }

    // When searching, ignore collapsed state and always search children
    val searching = filter != null
    if (collapsed && !searching) {
        return
    }

    if (!isSyntheticDeck) {
        list.append(DisplayDeckNode.from(this, false))
    }
    val startingLen = list.size
    for (child in children) {
        child.filterAndFlattenDisplayInner(filter, list, false)
    }
    if (!isSyntheticDeck && startingLen == list.size) {
        // we don't include ourselves if no children matched
        list.removeAt(list.lastIndex)
    }
}

private fun DeckNode.addVisibleToList(
    list: MutableList<DisplayDeckNode>,
    matchesSearchOrChild: Boolean,
) {
    list.append(DisplayDeckNode.from(this, matchesSearchOrChild))
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list, matchesSearchOrChild)
        }
    }
}

@VisibleForTesting
fun DeckNode.addVisibleToList(list: MutableList<DeckNode>) {
    list.append(this)
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list)
        }
    }
}

@SuppressLint("LocaleRootUsage")
private fun DeckNode.nameMatchesFilter(filter: CharSequence?): Boolean {
    return if (filter == null) {
        true
    } else {
        return node.name.lowercase(Locale.getDefault()).contains(filter) || node.name.lowercase(Locale.ROOT).contains(filter)
    }
}
