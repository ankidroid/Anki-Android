/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Utils
import java.util.*

object DeckService {
    fun shouldShowDefaultDeck(col: Collection): Boolean =
        defaultDeckHasCards(col) || hasChildren(col, Consts.DEFAULT_DECK_ID)

    @Suppress("SameParameterValue")
    private fun hasChildren(col: Collection, did: DeckId) =
        col.decks.children(did).size > 0

    fun defaultDeckHasCards(col: Collection) =
        col.db.queryScalar("select 1 from cards where did = 1") != 0

    /**
     * Counts cards in the supplied deck and child decks.
     *
     * Includes the count of filtered decks
     * Includes filtered cards outside the tree if the home deck is included
     *
     * @param did Id of the deck to search
     * @return the number of cards in the supplied deck and child decks
     */
    fun countCardsInDeckTree(col: Collection, did: DeckId): Int {
        val children: TreeMap<String, Long> = col.decks.children(did)
        val dids = LongArray(children.size + 1)
        dids[0] = did
        var i = 1
        for (l in children.values) {
            dids[i++] = l
        }
        val ids = Utils.ids2str(dids)
        return col.db.queryScalar(
            "select count() from cards where did in $ids or odid in $ids"
        )
    }

    /**
     * @return true if the collection contains a deck with the given name
     */
    fun deckExists(col: Collection, name: String) =
        col.decks.byName(name) != null
}
