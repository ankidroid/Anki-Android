/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts

/**
 * Checks if a given deck, including its subdecks if specified, is empty.
 *
 * @param deckId The ID of the deck to check.
 * @param includeSubdecks If true, includes subdecks in the check. Default is true.
 * @return `true` if the deck (and subdecks if specified) is empty, otherwise `false`.
 */
private fun Collection.isDeckEmpty(deckId: Long, includeSubdecks: Boolean = true): Boolean {
    val deckIds = decks.deckAndChildIds(deckId)
    val totalCardCount = decks.cardCount(*deckIds.toLongArray(), includeSubdecks = includeSubdecks)
    return totalCardCount == 0
}

/**
 * Checks if the default deck is empty.
 *
 * This method runs on an IO thread and accesses the collection to determine if the default deck (with ID 1) is empty.
 *
 * @return `true` if the default deck is empty, otherwise `false`.
 */
suspend fun isDefaultDeckEmpty(): Boolean = withCol { isDeckEmpty(Consts.DEFAULT_DECK_ID) }

/**
 * Returns whether the deck picker displays any deck.
 * Technically, it means that there is a non-default deck, or that the default deck is non-empty.
 *
 * This function is specifically implemented to address an issue where the default deck
 * isn't handled correctly when a second deck is added to the
 * collection. In this case, the deck tree may incorrectly appear as non-empty when it contains
 * only the default deck and no other cards.
 *
 */
suspend fun isCollectionEmpty(): Boolean {
    val tree = withCol { sched.deckDueTree() }
    val onlyDefaultDeckAvailable = tree.children.singleOrNull()?.did == Consts.DEFAULT_DECK_ID
    return onlyDefaultDeckAvailable && isDefaultDeckEmpty()
}
