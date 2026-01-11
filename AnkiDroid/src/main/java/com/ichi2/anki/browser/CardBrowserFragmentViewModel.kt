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

package com.ichi2.anki.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.model.SelectableDeck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

class CardBrowserFragmentViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val flowOfSearchForDecks = MutableSharedFlow<List<SelectableDeck>>()

    val advancedSearchFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_ENABLED, false)

    private val advancedSearchTextFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_TEXT, "")
    private val basicSearchTextFlow =
        savedStateHandle.getMutableStateFlow(STATE_BASIC_SEARCH_TEXT, "")

    val searchTextFlow =
        combine(advancedSearchFlow, basicSearchTextFlow, advancedSearchTextFlow) { displayingAdvancedSearch, basicText, advancedText ->
            if (displayingAdvancedSearch) advancedText else basicText
        }

    @NeedsTest("default usage")
    fun openDeckSelectionDialog() =
        viewModelScope.launch {
            val decks = listOf(SelectableDeck.AllDecks) + SelectableDeck.fromCollection(includeFiltered = true)
            flowOfSearchForDecks.emit(decks)
        }

    /**
     * Toggles between Basic and Advanced search mode
     */
    fun toggleAdvancedSearch() {
        Timber.i("Toggling advanced search to %s", !advancedSearchFlow.value)
        advancedSearchFlow.value = !advancedSearchFlow.value
    }

    /**
     * Appends [searchText] to the current temporary advances search text
     */
    fun appendAdvancedSearch(searchText: String) {
        Timber.d("appending search text %s", searchText)
        advancedSearchTextFlow.value += "$searchText "
    }

    /**
     * Called on user modification to the temporary search text
     */
    fun onSearchTextChanged(searchText: String) {
        if (advancedSearchFlow.value) {
            advancedSearchTextFlow.value = searchText
        } else {
            basicSearchTextFlow.value = searchText
        }
    }

    /**
     * Clears state when the temporary search is no longer visible
     *
     * If a user backs out of the SearchView, it should reset
     */
    fun clearTemporarySearchState() {
        Timber.i("clearing temp search state")
        advancedSearchFlow.value = false
        basicSearchTextFlow.value = ""
        advancedSearchTextFlow.value = ""

        savedStateHandle.remove<Any>(STATE_ADVANCED_SEARCH_ENABLED)
        savedStateHandle.remove<Any>(STATE_BASIC_SEARCH_TEXT)
        savedStateHandle.remove<Any>(STATE_ADVANCED_SEARCH_TEXT)
    }

    companion object {
        private const val STATE_ADVANCED_SEARCH_ENABLED = "advancedSearch"
        private const val STATE_BASIC_SEARCH_TEXT = "basicSearchText"
        private const val STATE_ADVANCED_SEARCH_TEXT = "advancedSearchText"
    }
}
