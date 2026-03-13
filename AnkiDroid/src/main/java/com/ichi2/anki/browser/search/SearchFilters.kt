/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import com.ichi2.anki.Flag
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson

/**
 * All user-selectable search filters for the [com.ichi2.anki.CardBrowser]
 *
 * As a UI model: the display names of the filters are provided, as well as IDs
 */
data class SearchFilters(
    val decks: List<DeckNameId>,
    val flags: List<Flag>,
    val tags: List<String>,
    val noteTypes: List<NoteTypeNameId>,
    val cardStates: List<CardState>,
) {
    /**
     * A list of filters which are using non-default values
     *
     * For example: the list contains [decks] if a deck filter is set
     */
    val activeFilters by lazy { listOf(decks, flags, tags, noteTypes, cardStates).filter { it.isNotEmpty() } }

    data class NoteTypeNameId(
        val name: String,
        val id: NoteTypeId,
    ) {
        companion object {
            fun fromNoteTypeJson(noteTypeJson: NotetypeJson) = NoteTypeNameId(noteTypeJson.name, noteTypeJson.id)
        }
    }

    companion object {
        /** An instance of [SearchFilters] with no [active filters][SearchFilters.activeFilters] */
        val EMPTY = partial()

        /**
         * Creates a [SearchFilters] instance, providing only a subset of filters
         */
        // exists so the primary constructor calls break if a parameter is added
        fun partial(
            decks: List<DeckNameId> = emptyList(),
            flags: List<Flag> = emptyList(),
            tags: List<String> = emptyList(),
            noteTypes: List<NoteTypeNameId> = emptyList(),
            cardStates: List<CardState> = emptyList(),
        ) = SearchFilters(
            decks = decks,
            flags = flags,
            tags = tags,
            noteTypes = noteTypes,
            cardStates = cardStates,
        )
    }
}
