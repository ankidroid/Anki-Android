/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki.sched

import anki.decks.DeckTreeNode
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.utils.TimeManager

// The desktop code stores these routines in sched/base.py, and all schedulers inherit them.
// The presence of AbstractSched is going to complicate the introduction of the v3 scheduler,
// so for now these are stored in a separate file.

fun CollectionV16.deckTree(includeCounts: Boolean): DeckTreeNode {
    return backend.deckTree(now = if (includeCounts) TimeManager.time.intTime() else 0)
}

/**
 * Mutate the backend reply into a format expected by legacy code. This is less efficient,
 * and AnkiDroid may wish to use .deckTree() in the future instead.
 */
fun CollectionV16.deckTreeLegacy(includeCounts: Boolean): List<TreeNode<DeckDueTreeNode>> {
    fun toLegacyNode(node: DeckTreeNode, parentName: String): TreeNode<DeckDueTreeNode> {
        val thisName = if (parentName.isEmpty()) {
            node.name
        } else {
            "$parentName::${node.name}"
        }
        val treeNode = TreeNode(
            DeckDueTreeNode(
                thisName,
                node.deckId,
                node.reviewCount,
                node.learnCount,
                node.newCount,
                collapsed = node.collapsed,
                filtered = node.filtered
            )
        )
        treeNode.children.addAll(node.childrenList.asSequence().map { toLegacyNode(it, thisName) })
        return treeNode
    }
    return toLegacyNode(deckTree(includeCounts), "").children
}

fun CollectionV16.upgradeScheduler() {
    modSchema()
    clearUndo()
    backend.upgradeScheduler()
    _loadScheduler()
}
