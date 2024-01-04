/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.ALL_DECKS_ID
import com.ichi2.libanki.DeckId

/**
 * A restriction on a Card Browser query caused by a single selected deck
 */
sealed interface DeckSearchFilter {

    data object AllDecks : DeckSearchFilter
    data class FilterDeck(val deckName: String) : DeckSearchFilter {
        companion object {
            suspend fun fromDeckId(deckId: DeckId): FilterDeck {
                val deckName = withCol { decks.name(deckId) }
                return FilterDeck(deckName)
            }
        }
    }

    fun filterSearch(searchTerms: String): String {
        return when (this) {
            is AllDecks -> searchTerms
            is FilterDeck ->
                when {
                    // A user-specified condition takes priority over our condition
                    searchTerms.contains("deck:") -> searchTerms
                    // If there is no user input apply or filter
                    searchTerms == "" -> "deck:\"$deckName\" "
                    // if there's user input which is NOT a deck specification, the selected deck
                    // takes priority
                    else -> "deck:\"$deckName\" ($searchTerms)"
                }
        }
    }

    companion object {
        suspend fun fromDeckId(deckId: DeckId): DeckSearchFilter = when (deckId) {
            ALL_DECKS_ID -> AllDecks
            else -> FilterDeck.fromDeckId(deckId)
        }
    }
}
