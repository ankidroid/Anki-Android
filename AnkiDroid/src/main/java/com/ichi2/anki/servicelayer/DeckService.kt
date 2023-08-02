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
     * @return true if the collection contains a deck with the given name
     */
    fun deckExists(col: Collection, name: String) =
        col.decks.byName(name) != null
}
