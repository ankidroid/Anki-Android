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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import timber.log.Timber

/**
 * A ViewModel for logic relating to creating/selecting a search in the [com.ichi2.anki.CardBrowser]
 *
 * Responsible for:
 *
 * - Temporary search strings
 * - Saved Searches
 * - History
 * - Advanced Search
 * - Previews
 */
// This is an Activity ViewModel: The SearchView can be directly on the activity
// The sub-fragments (StandardSearchFragment etc...) need to be able to modify/close the
// EditText, but should not be coupled directly to the parent SearchView.
class CardBrowserSearchViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val advancedSearchFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_ENABLED, false)

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
