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
import com.ichi2.libanki.Decks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeckUtils {

    /**
     * Checks if a given deck, including its subdecks if specified, is empty.
     *
     * @param decks The [Decks] instance containing the decks to check.
     * @param deckId The ID of the deck to check.
     * @param includeSubdecks If true, includes subdecks in the check. Default is true.
     * @return `true` if the deck (and subdecks if specified) is empty, otherwise `false`.
     */
    private fun isDeckEmpty(decks: Decks, deckId: Long, includeSubdecks: Boolean = true): Boolean {
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
    suspend fun isDefaultDeckEmpty(): Boolean {
        val defaultDeckId = 1L
        return withContext(Dispatchers.IO) {
            withCol {
                isDeckEmpty(decks, defaultDeckId)
            }
        }
    }
}
