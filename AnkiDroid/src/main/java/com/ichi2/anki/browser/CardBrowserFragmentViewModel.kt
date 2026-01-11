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
import kotlinx.coroutines.launch
import timber.log.Timber

class CardBrowserFragmentViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val flowOfSearchForDecks = MutableSharedFlow<List<SelectableDeck>>()

    val advancedSearchFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_ENABLED, false)

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

    companion object {
        private const val STATE_ADVANCED_SEARCH_ENABLED = "advancedSearch"
    }
}
