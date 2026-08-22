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

package com.ichi2.anki.browser

import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.search.CardState
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.PrefsStore
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

private typealias Tag = String

/**
 * The user's past searches in the Card Browser.
 *
 * Displayed in most recently used order.
 */
class SearchHistory(
    prefs: PrefsRepository = Prefs,
    maxEntries: Int = MAX_ENTRIES,
) : PrefsStore<SearchHistory.SearchHistoryEntry>(
        keyResId = R.string.pref_browser_search_history,
        entrySerializer = SearchHistoryEntry.serializer(),
        maxEntries = maxEntries,
        prefs = prefs,
    ) {
    /**
     * An entry in the history of the card browser.
     * This is user-supplied, so may contain PII.
     *
     * Contains the minimal values needed for persistent serialization:
     * Deck IDs are stored, rather than deck names. See [deckIds]
     *
     * !! When updating this, consider equality in PrefsStore.addRecent
     *
     * @see SearchHistory
     */
    @Serializable
    data class SearchHistoryEntry(
        @SerialName("q")
        val query: String,
        // Use IDs so we can handle a rename.
        // Tradeoff: a query to get the deck names is needed to produce a search string or display
        // the selected deck name in the UI
        @SerialName("did")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val deckIds: List<DeckId> = emptyList(),
        @SerialName("f")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val flags: List<Flag> = emptyList(),
        @SerialName("t")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val tags: List<Tag> = emptyList(),
        @SerialName("ntid")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val noteTypes: List<NoteTypeId> = emptyList(),
        @SerialName("s")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val cardStates: List<CardState> = emptyList(),
    ) {
        @Transient
        private val allFilters = listOf(deckIds, flags, tags, noteTypes, cardStates)

        override fun toString() = query

        /**
         * Whether there is no set search - effectively a search for the default search:
         * `deck:*`
         */
        fun isSearchEmpty() = query.isBlank() && allFilters.all { it.isEmpty() }
    }

    companion object {
        /**
         * The maximum number of search history entries to store.
         * https://github.com/ankitects/anki/blob/e9cc65569807771f548fc9c2634aabc7b2f90ed2/qt/aqt/browser/browser.py#L541
         */
        const val MAX_ENTRIES = 30
    }
}
