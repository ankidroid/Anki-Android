/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.filtered

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChangesWithId
import anki.decks.Deck
import anki.decks.FilteredDeckForUpdate
import anki.decks.filteredDeckForUpdate
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment.Companion.ARG_DECK_ID
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment.Companion.ARG_SEARCH
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment.Companion.ARG_SEARCH_2
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * @see FilteredDeckOptionsState
 * @see FilteredDeckOptionsFragment
 */
class FilteredDeckOptionsViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow<FilteredDeckOptionsState>(Initializing())
    val state: StateFlow<FilteredDeckOptionsState> = _state.asStateFlow()
    private val did: DeckId
        get() = savedStateHandle.get<Long>(ARG_DECK_ID) ?: 0L
    private val search: String?
        get() = savedStateHandle.get<String>(ARG_SEARCH)
    private val search2: String?
        get() = savedStateHandle.get<String>(ARG_SEARCH_2)

    init {
        viewModelScope.launch {
            Timber.i("Starting filtered deck options setup, deckId=$did")
            val previousState = savedStateHandle.get<FilteredDeckOptions>(ARG_DATA)
            if (previousState != null) {
                _state.update { previousState }
                return@launch
            }
            Timber.i("No previous stored state, querying the collection")
            val backendQueryResult = withCol { safeBackendDataQuery(did) }
            if (backendQueryResult.isFailure) {
                _state.update { Initializing(throwable = backendQueryResult.exceptionOrNull()) }
                return@launch
            }
            val (filteredDeckData, cardsOptions) = backendQueryResult.getOrThrow()
            savedStateHandle[ARG_DATA] =
                filteredDeckData.asInitialState(
                    cardsOptions = cardsOptions,
                    defaultSearch1 = search,
                    defaultSearch2 = search2,
                )
            _state.update { currentState() }
        }
    }

    fun onDeckNameChange(name: String) {
        Timber.i("Filtered deck name is changing")
        if (currentState().name == name) return
        updateCurrentState { copy(name = name) }
    }

    fun onSearchChange(
        index: FilterIndex,
        searchQuery: String,
    ) {
        Timber.i("Filtered deck filter($index) search string is changing")
        val targetFilterState = currentFilterState(index)
        if (targetFilterState?.search == searchQuery) return
        updateCurrentFilterState(index) { copy(search = searchQuery) }
    }

    fun onLimitChange(
        index: FilterIndex,
        limit: String,
    ) {
        Timber.i("Filtered deck filter($index) limit changing to $limit")
        if (limit == currentFilterState(index)?.limit) return
        updateCurrentFilterState(index) { copy(limit = limit) }
    }

    fun onCardsOptionsChange(
        filterIndex: FilterIndex,
        cardOptionIndex: Int,
    ) {
        Timber.i("Filtered deck filter($filterIndex) cards options index changing to $cardOptionIndex")
        if (currentFilterState(filterIndex)?.index == cardOptionIndex) return
        updateCurrentFilterState(filterIndex) { copy(index = cardOptionIndex) }
    }

    fun onSecondFilterStatusChange(isEnabled: Boolean) {
        Timber.i("Second filter status is changing to $isEnabled")
        if (currentState().isSecondFilterEnabled == isEnabled) return
        updateCurrentState { copy(isSecondFilterEnabled = isEnabled) }
    }

    fun onRescheduleChange(isEnabled: Boolean) {
        Timber.i("Rescheduling status is changing to $isEnabled")
        if (currentState().shouldReschedule == isEnabled) return
        updateCurrentState { copy(shouldReschedule = isEnabled) }
    }

    fun onRescheduleDelayChange(
        target: RescheduleDelay,
        amount: String,
    ) {
        Timber.i("Rescheduling delay: $target is changing to $amount seconds")
        val currentAmount =
            when (target) {
                RescheduleDelay.Again -> currentState().delayAgain
                RescheduleDelay.Hard -> currentState().delayHard
                RescheduleDelay.Good -> currentState().delayGood
            }
        if (amount == currentAmount) return
        updateCurrentState {
            when (target) {
                RescheduleDelay.Again -> copy(delayAgain = amount)
                RescheduleDelay.Hard -> copy(delayHard = amount)
                RescheduleDelay.Good -> copy(delayGood = amount)
            }
        }
    }

    fun onAllowEmptyChange(isEnabled: Boolean) {
        Timber.i("Allow empty status is changing to $isEnabled")
        if (currentState().allowEmpty == isEnabled) return
        updateCurrentState { copy(allowEmpty = isEnabled) }
    }

    fun build() {
        Timber.i("Building/Rebuilding filtered deck")
        _state.update { currentState().copy(isBuildingBrowserSearch = true) }
        val newFilterForUpdate = currentState().asBackendData()
        viewModelScope.launch {
            undoableOp<OpChangesWithId> {
                val safeAddUpdateResult = safeAddOrUpdateFilteredDeck(newFilterForUpdate)
                if (safeAddUpdateResult.isSuccess) {
                    _state.update { DeckBuilt }
                    safeAddUpdateResult.getOrThrow()
                } else {
                    _state.update {
                        currentState().copy(
                            throwable = safeAddUpdateResult.exceptionOrNull(),
                            isBuildingBrowserSearch = false,
                        )
                    }
                    OpChangesWithId.getDefaultInstance()
                }
            }
        }
    }

    fun clearError() {
        Timber.i("Clearing error from state")
        updateCurrentState { copy(throwable = null) }
    }

    /** Get the current state as it's found in the associated [SavedStateHandle]. Throws if state is not found. */
    private fun currentState(): FilteredDeckOptions = requireNotNull(savedStateHandle[ARG_DATA])

    /** Retrieves from the current state the filter state indicated by [index]. */
    private fun currentFilterState(index: FilterIndex): SearchTermState? =
        when (index) {
            FilterIndex.First -> currentState().filter1State
            FilterIndex.Second -> currentState().filter2State
        }

    /** Saves to the [SavedStateHandle] the result of invoking [action] and after updates the state. */
    private fun updateCurrentState(action: FilteredDeckOptions.() -> FilteredDeckOptions) {
        val updatedState = currentState().action()
        savedStateHandle[ARG_DATA] = updatedState
        _state.update { currentState() }
    }

    /**
     * Saves to the [SavedStateHandle] the result of invoking [action] on the filter indicated by
     * [index] and after updates the state.
     */
    private fun updateCurrentFilterState(
        index: FilterIndex,
        action: SearchTermState.() -> SearchTermState?,
    ) {
        when (index) {
            FilterIndex.First -> {
                val changeSearchState = currentState().filter1State.action()
                // safe !! use because the first filter always exists, null type is for the second filter
                savedStateHandle[ARG_DATA] = currentState().copy(filter1State = changeSearchState!!)
            }

            FilterIndex.Second -> {
                val changeSearchState = currentState().filter2State?.action()
                savedStateHandle[ARG_DATA] = currentState().copy(filter2State = changeSearchState)
            }
        }
        _state.update { currentState() }
    }

    /** Queries the backend for cards search options and [FilteredDeckForUpdate] guarding against any exception. */
    private fun Collection.safeBackendDataQuery(did: DeckId): Result<Pair<FilteredDeckForUpdate, List<String>>> =
        try {
            val filteredDeckForUpdate = sched.getOrCreateFilteredDeck(did)
            val cardsOptions = sched.filteredDeckOrderLabels()
            Result.success(Pair(filteredDeckForUpdate, cardsOptions))
        } catch (ex: Exception) {
            Result.failure(ex)
        }

    /** Invokes the backend to add or update a filtered deck guarding against any exception. */
    private fun Collection.safeAddOrUpdateFilteredDeck(deckData: FilteredDeckForUpdate): Result<OpChangesWithId> =
        try {
            Result.success(sched.addOrUpdateFilteredDeck(deckData))
        } catch (ex: Exception) {
            Result.failure(ex)
        }

    /**
     * Builds the initial state from the [FilteredDeckForUpdate] retrieved at start combining it
     * with other pieces of data.
     */
    private fun FilteredDeckForUpdate.asInitialState(
        cardsOptions: List<String> = emptyList(),
        defaultSearch1: String? = null,
        defaultSearch2: String? = null,
    ): FilteredDeckOptions {
        val firstFilter = config.getSearchTerms(0)
        val secondFilter = if (config.searchTermsCount > 1) config.getSearchTerms(1) else null
        return FilteredDeckOptions(
            // backend uses 0 for creating a new filtered deck but we use null instead
            id = this.id.takeIf { it != 0L },
            name = this.name,
            shouldReschedule = this.config.reschedule,
            allowEmpty = this.allowEmpty,
            cardOptions = cardsOptions,
            filter1State =
                SearchTermState(
                    search = defaultSearch1 ?: firstFilter.search,
                    limit = firstFilter.limit.toString(),
                    index = firstFilter.order.number,
                ),
            isSecondFilterEnabled = if (secondFilter != null) id != 0L else false,
            filter2State =
                secondFilter?.let {
                    SearchTermState(
                        search = defaultSearch2 ?: it.search,
                        limit = it.limit.toString(),
                        index = it.order.number,
                    )
                },
            delayAgain = config.previewAgainSecs.toString(),
            delayHard = config.previewHardSecs.toString(),
            delayGood = config.previewGoodSecs.toString(),
        )
    }

    /** Converts the state into a [FilteredDeckForUpdate] to be used in backend calls. */
    private fun FilteredDeckOptions.asBackendData(): FilteredDeckForUpdate =
        filteredDeckForUpdate {
            val current = this@asBackendData
            // backend code expects the id of a new filtered deck to be 0
            id = current.id ?: 0
            name = current.name
            allowEmpty = current.allowEmpty
            val configBuilder =
                Deck.Filtered
                    .newBuilder()
                    .setReschedule(current.shouldReschedule)
                    .setPreviewAgainSecs(current.delayAgain.toIntOrNull() ?: 60)
                    .setPreviewHardSecs(current.delayHard.toIntOrNull() ?: 600)
                    .setPreviewGoodSecs(current.delayGood.toIntOrNull() ?: 0)
                    .addSearchTerms(
                        Deck.Filtered.SearchTerm
                            .newBuilder()
                            .setSearch(current.filter1State.search)
                            .setLimit(current.filter1State.limit.toIntOrNull() ?: 0)
                            .setOrder(
                                Deck.Filtered.SearchTerm.Order
                                    .forNumber(current.filter1State.index),
                            ),
                    )
            val filter2State = current.filter2State
            if (current.isSecondFilterEnabled && filter2State != null) {
                configBuilder.addSearchTerms(
                    Deck.Filtered.SearchTerm
                        .newBuilder()
                        .setSearch(filter2State.search)
                        .setLimit(filter2State.limit.toIntOrNull() ?: 0)
                        .setOrder(
                            Deck.Filtered.SearchTerm.Order
                                .forNumber(filter2State.index),
                        ),
                )
            }
            config = configBuilder.build()
        }

    companion object {
        /** Key used to store/retrieve our state in [SavedStateHandle]. */
        private const val ARG_DATA = "arg_data"
    }
}
