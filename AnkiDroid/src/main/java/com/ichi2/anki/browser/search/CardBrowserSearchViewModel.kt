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
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A ViewModel for logic relating to creating/selecting a search in the [com.ichi2.anki.CardBrowser]
 *
 * Responsible for:
 *
 * - The search string (unsubmitted)
 * - Saved Searches
 * - History
 * - Advanced Search
 * - Previews
 */
// This is an Activity ViewModel: The SearchView can be directly on the activity
// The sub-fragments (StandardSearchFragment etc...) need to be able to modify/close the
// EditText, but should not be coupled directly to the parent SearchView.
class CardBrowserSearchViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val searchHistoryManager = SearchHistory()

    val advancedSearchFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_ENABLED, false)

    private val advancedSearchTextFlow =
        savedStateHandle.getMutableStateFlow(STATE_ADVANCED_SEARCH_TEXT, "")
    private val basicSearchTextFlow =
        savedStateHandle.getMutableStateFlow(STATE_BASIC_SEARCH_TEXT, "")

    val searchTextFlow =
        combine(advancedSearchFlow, basicSearchTextFlow, advancedSearchTextFlow) { displayingAdvancedSearch, basicText, advancedText ->
            if (displayingAdvancedSearch) advancedText else basicText
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "",
        )

    val searchHistoryFlow = MutableStateFlow(searchHistoryManager.entries)
    val searchHistoryAvailableFlow = searchHistoryFlow.map { it.isNotEmpty() }

    val closeSearchViewFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, replay = 1)

    val submittedSearchFlow = MutableStateFlow<String?>(null)

    val savedSearchesFlow = MutableStateFlow<List<SavedSearch>>(emptyList())

    val canManageSavedSearchesFlow = savedSearchesFlow.map { it.isNotEmpty() }

    val userMessageFlow = MutableSharedFlow<UserMessage?>()

    init {
        viewModelScope.launch {
            savedSearchesFlow.value = SavedSearches.loadFromConfig()
        }
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
        Timber.d("appending search text '%s'", searchText)
        val currentValue = advancedSearchTextFlow.value
        advancedSearchTextFlow.value +=
            buildString {
                if (currentValue.isNotBlank() && !currentValue.endsWith(" ")) append(' ')
                append(searchText)
                append(' ')
            }
    }

    /**
     * Called on user modification to the non-submitted search text
     */
    fun onSearchTextChanged(searchText: String) {
        Timber.v("onSearchTextChanged '%s'", searchText)
        if (advancedSearchFlow.value) {
            advancedSearchTextFlow.value = searchText
        } else {
            basicSearchTextFlow.value = searchText
        }
    }

    fun submitCurrentSearch(submittedText: String = searchTextFlow.value) =
        viewModelScope.launch {
            Timber.i("submitting search")
            // searches with trailing and leading spaces are equivalent
            val submittedText = submittedText.trim()

            val updatedEntries =
                searchHistoryManager.addRecent(
                    SearchHistoryEntry(
                        query = submittedText,
                    ),
                )
            searchHistoryFlow.value = updatedEntries
            closeSearchViewFlow.emit(Unit)
            submittedSearchFlow.emit(submittedText)
        }

    fun selectSearchHistoryEntry(entry: SearchHistoryEntry) {
        Timber.i("selected search history entry")
        onSearchTextChanged(entry.query)
        submitCurrentSearch()
    }

    fun addSavedSearch(savedSearch: SavedSearch) =
        viewModelScope.launch {
            Timber.i("Adding saved search")
            val (success, newValues) = SavedSearches.add(savedSearch)
            if (success) {
                savedSearchesFlow.emit(newValues)
                userMessageFlow.emit(UserMessage.SEARCH_SAVED)
            } else {
                userMessageFlow.emit(UserMessage.SAVED_SEARCH_DUPLICATE_ADDED)
            }
        }

    fun deleteSavedSearch(search: SavedSearch) =
        viewModelScope.launch {
            val (success, values) = SavedSearches.removeByName(search.name)

            Timber.d("deleted saved search: %b", success)

            if (success) {
                savedSearchesFlow.emit(values)
                userMessageFlow.emit(UserMessage.SAVED_SEARCH_DELETED)
            } else {
                userMessageFlow.emit(UserMessage.SAVED_SEARCH_NAME_DOES_NOT_EXIST)
            }
        }

    /**
     * Submits the query in the provided saved search
     */
    fun submitSavedSearch(search: SavedSearch) {
        Timber.i("selected saved search")
        onSearchTextChanged(search.query)
        submitCurrentSearch()
    }

    /**
     * Replaces the current search text with the query in the provided saved search.
     */
    fun applySavedSearch(search: SavedSearch) {
        Timber.i("replacing search with saved search text (not submitting)")
        onSearchTextChanged(search.query + " ")
    }

    /**
     * Clears state when the search screen is closed without saving
     */
    fun resetSearchState() {
        Timber.i("clearing temp search state")
        advancedSearchFlow.value = false
        basicSearchTextFlow.value = ""
        advancedSearchTextFlow.value = ""

        savedStateHandle.remove<Any>(STATE_ADVANCED_SEARCH_ENABLED)
        savedStateHandle.remove<Any>(STATE_BASIC_SEARCH_TEXT)
        savedStateHandle.remove<Any>(STATE_ADVANCED_SEARCH_TEXT)
    }

    enum class UserMessage {
        SEARCH_SAVED,
        SAVED_SEARCH_DUPLICATE_ADDED,
        SAVED_SEARCH_NAME_DOES_NOT_EXIST,
        SAVED_SEARCH_DELETED,
    }

    companion object {
        private const val STATE_ADVANCED_SEARCH_ENABLED = "advancedSearch"
        private const val STATE_BASIC_SEARCH_TEXT = "basicSearchText"
        private const val STATE_ADVANCED_SEARCH_TEXT = "advancedSearchText"
    }
}
