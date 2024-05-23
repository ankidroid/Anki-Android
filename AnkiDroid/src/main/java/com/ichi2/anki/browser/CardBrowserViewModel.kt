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

import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import anki.collection.OpChangesWithCount
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckSpinnerSelection.Companion.ALL_DECKS_ID
import com.ichi2.anki.Flag
import com.ichi2.anki.PreviewerDestination
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.*
import com.ichi2.anki.model.SortType
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.servicelayer.CardService
import com.ichi2.anki.setUserFlag
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Consts.QUEUE_TYPE_MANUALLY_BURIED
import com.ichi2.libanki.Consts.QUEUE_TYPE_SIBLING_BURIED
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.hasTag
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Collections
import java.util.HashMap
import kotlin.math.max
import kotlin.math.min

@NeedsTest("reverseDirectionFlow/sortTypeFlow are not updated on .launch { }")
@NeedsTest("13442: selected deck is not changed, as this affects the reviewer")
@NeedsTest("search is called after launch()")
class CardBrowserViewModel(
    private val lastDeckIdRepository: LastDeckIdRepository,
    private val cacheDir: File,
    options: CardBrowserLaunchOptions?,
    preferences: SharedPreferencesProvider
) : ViewModel(), SharedPreferencesProvider by preferences {

    // Set by the UI to determine the number of cards to preload before returning search results
    // This is a hack, but will be removed soon when we move to the backend for card rendering
    // so isn't worth refactoring further
    var numCardsToRender: Int? = null

    /** A job which ensures that parallel searches do not occur */
    var searchJob: Job? = null
        private set

    // temporary flow for refactoring - called when cards are cleared
    val flowOfCardsUpdated = MutableSharedFlow<Unit>()

    val cards = CardBrowser.CardCollection<CardBrowser.CardCache>()

    val flowOfSearchState = MutableSharedFlow<SearchState>()

    /** The CardIds of all the cards in the results */
    val allCardIds get() = cards.map { c -> c.id }

    var searchTerms = ""
        private set
    private var restrictOnDeck: String = ""

    /** text in the search box (potentially unsubmitted) */
    // this does not currently bind to the value in the UI and is only used for posting
    val flowOfFilterQuery = MutableSharedFlow<String>()

    /**
     * Whether the browser is working in Cards mode or Notes mode.
     * default: [CARDS]
     * */
    private val flowOfCardsOrNotes = MutableStateFlow(CARDS)
    val cardsOrNotes get() = flowOfCardsOrNotes.value

    // card that was clicked (not marked)
    var currentCardId: CardId = 0

    private val sortTypeFlow = MutableStateFlow(SortType.NO_SORTING)
    val order get() = sortTypeFlow.value

    private val reverseDirectionFlow = MutableStateFlow(ReverseDirection(orderAsc = false))
    val orderAsc get() = reverseDirectionFlow.value.orderAsc

    val flowOfColumnIndex1 = MutableStateFlow(sharedPrefs().getInt(DISPLAY_COLUMN_1_KEY, 0))
    val flowOfColumnIndex2 = MutableStateFlow(sharedPrefs().getInt(DISPLAY_COLUMN_2_KEY, 0))
    val column1Index get() = flowOfColumnIndex1.value
    val column2Index get() = flowOfColumnIndex2.value

    val flowOfSearchQueryExpanded = MutableStateFlow(false)

    private val searchQueryInputFlow = MutableStateFlow<String?>(null)

    /** The query which is currently in the search box, potentially null. Only set when search box was open  */
    val tempSearchQuery get() = searchQueryInputFlow.value

    /** Whether the current element in the search bar can be saved */
    val flowOfCanSearch = searchQueryInputFlow
        .map { it?.isNotEmpty() == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    val flowOfIsTruncated: MutableStateFlow<Boolean> =
        MutableStateFlow(sharedPrefs().getBoolean("isTruncated", false))
    val isTruncated get() = flowOfIsTruncated.value

    private val _selectedRows: MutableSet<CardBrowser.CardCache> = Collections.synchronizedSet(LinkedHashSet())

    // immutable accessor for _selectedRows
    val selectedRows: Set<CardBrowser.CardCache> get() = _selectedRows

    private val refreshSelectedRowsFlow = MutableSharedFlow<Unit>()
    val flowOfSelectedRows: Flow<Set<CardBrowser.CardCache>> =
        flowOf(selectedRows).combine(refreshSelectedRowsFlow) { row, _ -> row }

    /** A list of either:
     * * [CardsOrNotes.CARDS] all selected card Ids
     * * [CardsOrNotes.NOTES] one selected Id for every note
     */
    val selectedRowIds: List<CardId>
        get() = selectedRows.map { c -> c.id }

    suspend fun queryAllSelectedCardIds(): List<CardId> = when (cardsOrNotes) {
        CARDS -> selectedRowIds
        NOTES ->
            selectedRows
                .flatMap { row -> withCol { cardIdsOfNote(nid = row.card.nid) } }
    }

    // TODO: move the tag computation to ViewModel
    suspend fun queryAllSelectedNoteIds(): List<NoteId> =
        withCol { notesOfCards(selectedRowIds) }

    var lastSelectedPosition = 0

    val flowOfIsInMultiSelectMode = flowOfSelectedRows
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    val isInMultiSelectMode
        get() = flowOfIsInMultiSelectMode.value

    val lastDeckId: DeckId?
        get() = lastDeckIdRepository.lastDeckId

    suspend fun setDeckId(deckId: DeckId) {
        Timber.i("setting deck: %d", deckId)
        lastDeckIdRepository.lastDeckId = deckId
        restrictOnDeck = if (deckId == ALL_DECKS_ID) {
            ""
        } else {
            val deckName = withCol { decks.name(deckId) }
            "deck:\"$deckName\" "
        }
        flowOfDeckId.update { deckId }
    }

    val flowOfDeckId = MutableStateFlow(lastDeckId)
    val deckId get() = flowOfDeckId.value

    val cardInfoDestination: CardInfoDestination?
        get() {
            val firstSelectedCard = selectedRowIds.firstOrNull() ?: return null
            return CardInfoDestination(firstSelectedCard)
        }

    private suspend fun getInitialDeck(): DeckId {
        // TODO: Handle the launch intent
        val lastDeckId = lastDeckId
        if (lastDeckId == ALL_DECKS_ID) {
            return ALL_DECKS_ID
        }

        // If a valid value for last deck exists then use it, otherwise use libanki selected deck
        return if (lastDeckId != null && withCol { decks.get(lastDeckId) != null }) {
            lastDeckId
        } else {
            withCol { decks.selected() }
        }
    }

    val flowOfInitCompleted = MutableStateFlow(false)

    /**
     * Whether the task launched from CardBrowserViewModel.init has completed.
     *
     * If `false`, we don't have the initial values to perform the first search
     */
    @get:VisibleForTesting
    val initCompleted get() = flowOfInitCompleted.value

    /**
     * A search should be triggered if these properties change
     */
    private val searchRequested = flowOf(flowOfCardsOrNotes, flowOfDeckId)
        .flattenMerge()

    /**
     * Emits an item when:
     * * [initCompleted] is true
     * * A property which defines the search has been changed ([searchRequested])
     *
     * @see launchSearchForCards
     */
    private val performSearchFlow = flowOfInitCompleted.combineTransform(searchRequested) { init, _ ->
        if (!init) return@combineTransform
        emit(Unit)
    }

    init {
        Timber.d("CardBrowserViewModel::init")

        var selectAllDecks = false
        when (options) {
            is CardBrowserLaunchOptions.SystemContextMenu -> { searchTerms = options.search.toString() }
            is CardBrowserLaunchOptions.SearchQueryJs -> {
                searchTerms = options.search
                selectAllDecks = options.allDecks
            }
            is CardBrowserLaunchOptions.DeepLink -> { searchTerms = options.search }
            null -> {}
        }

        flowOfColumnIndex1
            .onEach { index -> sharedPrefs().edit { putInt(DISPLAY_COLUMN_1_KEY, index) } }
            .launchIn(viewModelScope)

        flowOfColumnIndex2
            .onEach { index -> sharedPrefs().edit { putInt(DISPLAY_COLUMN_2_KEY, index) } }
            .launchIn(viewModelScope)

        performSearchFlow.onEach {
            launchSearchForCards()
        }.launchIn(viewModelScope)

        reverseDirectionFlow
            .ignoreValuesFromViewModelLaunch()
            .onEach { newValue -> withCol { newValue.updateConfig(config) } }
            .launchIn(viewModelScope)

        sortTypeFlow
            .ignoreValuesFromViewModelLaunch()
            .onEach { sortType -> withCol { sortType.save(config, sharedPrefs()) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val initialDeckId = if (selectAllDecks) ALL_DECKS_ID else getInitialDeck()
            // PERF: slightly inefficient if the source was lastDeckId
            setDeckId(initialDeckId)
            val cardsOrNotes = withCol { CardsOrNotes.fromCollection() }
            flowOfCardsOrNotes.update { cardsOrNotes }

            withCol {
                sortTypeFlow.update { SortType.fromCol(config, sharedPrefs()) }
                reverseDirectionFlow.update { ReverseDirection.fromConfig(config) }
            }
            Timber.i("initCompleted")
            flowOfInitCompleted.update { true }
        }
    }

    /** Whether any rows are selected */
    fun hasSelectedAnyRows(): Boolean = selectedRows.isNotEmpty()

    /**
     * All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked
     */
    suspend fun toggleMark(cardIds: List<CardId>) {
        if (!hasSelectedAnyRows()) {
            Timber.i("Not marking cards - nothing selected")
            return
        }
        undoableOp(this) {
            val noteIds = notesOfCards(cardIds)
            // if all notes are marked, remove the mark
            // if no notes are marked, add the mark
            // if there is a mix, enable the mark on all
            val wantMark = !noteIds.all { getNote(it).hasTag("marked") }
            Timber.i("setting mark = %b for %d notes", wantMark, noteIds.size)
            if (wantMark) {
                tags.bulkAdd(noteIds, "marked")
            } else {
                tags.bulkRemove(noteIds, "marked")
            }
        }
        selectedRows.forEach { it.reload() }
    }

    /**
     * Deletes the selected notes,
     * @return the number of deleted notes
     */
    suspend fun deleteSelectedNotes(): Int =
        undoableOp { removeNotes(cids = selectedRowIds) }.count

    fun setCardsOrNotes(newValue: CardsOrNotes) = viewModelScope.launch {
        Timber.i("setting mode to %s", newValue)
        withCol {
            // Change this to only change the preference on a state change
            newValue.saveToCollection()
        }
        flowOfCardsOrNotes.update { newValue }
    }

    fun setTruncated(value: Boolean) {
        viewModelScope.launch {
            flowOfIsTruncated.emit(value)
        }
        sharedPrefs().edit {
            putBoolean("isTruncated", value)
        }
    }

    fun selectAll() {
        if (_selectedRows.addAll(cards.wrapped)) {
            refreshSelectedRowsFlow()
        }
    }

    fun selectNone() {
        if (_selectedRows.isEmpty()) return
        _selectedRows.clear()
        refreshSelectedRowsFlow()
    }

    fun toggleRowSelectionAtPosition(position: Int) {
        val card = cards[position]
        if (_selectedRows.contains(card)) {
            _selectedRows.remove(card)
        } else {
            _selectedRows.add(card)
        }
        refreshSelectedRowsFlow()
    }

    @VisibleForTesting
    fun selectRowAtPosition(pos: Int) {
        if (_selectedRows.add(cards[pos])) {
            refreshSelectedRowsFlow()
        }
    }

    /**
     * Selects the cards between [startPos] and [endPos]
     */
    fun selectRowsBetweenPositions(startPos: Int, endPos: Int) {
        val cards = (min(startPos, endPos)..max(startPos, endPos)).map { cards[it] }
        if (_selectedRows.addAll(cards)) {
            refreshSelectedRowsFlow()
        }
    }

    /** emits a new value in [flowOfSelectedRows] */
    private fun refreshSelectedRowsFlow() = viewModelScope.launch {
        refreshSelectedRowsFlow.emit(Unit)
    }

    fun selectedRowCount(): Int = selectedRows.size

    suspend fun changeCardOrder(which: SortType): Job? {
        val changeType = when {
            which != order -> ChangeCardOrder.OrderChange(which)
            // if the same element is selected again, reverse the order
            which != SortType.NO_SORTING -> ChangeCardOrder.DirectionChange
            else -> null
        } ?: return null

        Timber.i("updating order: %s", changeType)

        return when (changeType) {
            is ChangeCardOrder.OrderChange -> {
                sortTypeFlow.update { which }
                reverseDirectionFlow.update { ReverseDirection(orderAsc = false) }
                launchSearchForCards()
            }
            ChangeCardOrder.DirectionChange -> {
                reverseDirectionFlow.update { ReverseDirection(orderAsc = !orderAsc) }
                cards.reverse()
                flowOfSearchState.emit(SearchState.Completed)
                null
            }
        }
    }

    fun setColumn1Index(value: Int) = flowOfColumnIndex1.update { value }

    fun setColumn2Index(value: Int) = flowOfColumnIndex2.update { value }
    suspend fun suspendCards() {
        if (!hasSelectedAnyRows()) {
            return
        }
        val cardIds = queryAllSelectedCardIds()

        undoableOp {
            // if all cards are suspended, unsuspend all
            // if no cards are suspended, suspend all
            // if there is a mix, suspend all
            val wantUnsuspend = cardIds.all { getCard(it).queue == Consts.QUEUE_TYPE_SUSPENDED }
            if (wantUnsuspend) {
                sched.unsuspendCards(cardIds)
            } else {
                sched.suspendCards(cardIds).changes
            }
        }
    }

    /**
     * if all cards are buried, unbury all
     * if no cards are buried, bury all
     * if there is a mix, bury all
     *
     * if no cards are checked, do nothing
     *
     * @return Whether the operation was bury/unbury, and the number of affected cards.
     * `null` if nothing happened
     */
    suspend fun toggleBury(): BuryResult? {
        if (!hasSelectedAnyRows()) {
            Timber.w("no cards to bury")
            return null
        }

        // https://github.com/ankitects/anki/blob/074becc0cee1e9ae59be701ad6c26787f74b4594/qt/aqt/browser/browser.py#L896-L902
        fun Card.isBuried(): Boolean =
            queue == QUEUE_TYPE_MANUALLY_BURIED || queue == QUEUE_TYPE_SIBLING_BURIED

        val cardIds = queryAllSelectedCardIds()

        // this variable exists as `undoableOp` needs an OpChanges as return value
        var wasBuried: Boolean? = null
        undoableOp {
            // this differs from Anki Desktop which uses the first selected card to determine the
            // 'checked' status
            val wantUnbury = cardIds.all { getCard(it).isBuried() }

            wasBuried = !wantUnbury
            if (wantUnbury) {
                Timber.i("unburying %d cards", cardIds.size)
                sched.unburyCards(cardIds)
            } else {
                Timber.i("burying %d cards", cardIds.size)
                sched.buryCards(cardIds).changes
            }
        }
        return BuryResult(wasBuried = wasBuried!!, count = cardIds.size)
    }

    suspend fun getSelectionExportData(): Pair<ExportDialogFragment.ExportType, List<Long>>? {
        if (!isInMultiSelectMode) return null
        return when (cardsOrNotes) {
            CARDS -> Pair(ExportDialogFragment.ExportType.Cards, selectedRowIds)
            NOTES -> Pair(
                ExportDialogFragment.ExportType.Notes,
                withCol { CardService.selectedNoteIds(selectedRowIds) }
            )
        }
    }

    /**
     * @see [com.ichi2.libanki.sched.Scheduler.sortCards]
     * @return the number of cards which were repositioned
     */
    suspend fun repositionSelectedRows(position: Int) = undoableOp {
        sched.sortCards(selectedRowIds, position, 1, shuffle = false, shift = true)
    }.count

    /** Returns the number of rows of the current result set  */
    val rowCount: Int
        get() = cards.size()

    fun getCardIdAtPosition(position: Int): CardId = cards[position].id

    fun getRowAtPosition(position: Int) = cards[position]

    /** Given a card ID, returns a position-aware card */
    fun findCardById(id: CardId): CardBrowser.CardCache? = cards.find { it.id == id }

    val cardIdToPositionMap: Map<CardId, Int>
        get() = cards.mapIndexed { i, c -> c.id to i }.toMap()

    override fun onCleared() {
        super.onCleared()
        invalidate()
    }

    private fun invalidate() {
        // TODO: this may no longer be needed now we call invalidate from onCleared
        cards.clear()
        selectNone()
    }

    private suspend fun updateSavedSearches(func: HashMap<String, String>.() -> Unit): HashMap<String, String> {
        val filters = savedSearches()
        func(filters)
        withCol { config.set("savedFilters", filters) }
        return filters
    }
    suspend fun savedSearches(): HashMap<String, String> =
        withCol { config.get("savedFilters") } ?: hashMapOf()

    fun savedSearchesUnsafe(col: com.ichi2.libanki.Collection): HashMap<String, String> =
        col.config.get("savedFilters") ?: hashMapOf()

    suspend fun removeSavedSearch(searchName: String): HashMap<String, String> {
        Timber.d("removing user search")
        return updateSavedSearches {
            remove(searchName)
        }
    }

    suspend fun saveSearch(searchName: String, searchTerms: String): SaveSearchResult {
        Timber.d("saving user search")
        var alreadyExists = false
        updateSavedSearches {
            if (get(searchName) != null) {
                alreadyExists = true
            } else {
                set(searchName, searchTerms)
            }
        }
        return if (alreadyExists) SaveSearchResult.ALREADY_EXISTS else SaveSearchResult.SUCCESS
    }

    /** Ignores any values before [initCompleted] is set */
    private fun <T> Flow<T>.ignoreValuesFromViewModelLaunch(): Flow<T> =
        this.filter { initCompleted }

    suspend fun setFilterQuery(filterQuery: String) {
        this.flowOfFilterQuery.emit(filterQuery)
        launchSearchForCards(filterQuery)
    }

    suspend fun searchForMarkedNotes() = setFilterQuery("tag:marked")

    suspend fun searchForSuspendedCards() = setFilterQuery("is:suspended")
    suspend fun setFlagFilter(flag: Flag) {
        Timber.i("filtering to flag: %s", flag)
        val flagSearchTerm = "flag:${flag.code}"
        val searchTerms = when {
            searchTerms.contains("flag:") -> searchTerms.replaceFirst("flag:.".toRegex(), flagSearchTerm)
            searchTerms.isNotEmpty() -> "$flagSearchTerm $searchTerms"
            else -> flagSearchTerm
        }
        setFilterQuery(searchTerms)
    }

    suspend fun filterByTags(selectedTags: List<String>, cardState: CardStateFilter) {
        val sb = StringBuilder(cardState.toSearch)
        // join selectedTags as "tag:$tag" with " or " between them
        val tagsConcat = selectedTags.joinToString(" or ") { tag -> "\"tag:$tag\"" }
        if (selectedTags.isNotEmpty()) {
            sb.append("($tagsConcat)") // Only if we added anything to the tag list
        }
        setFilterQuery(sb.toString())
    }

    /** Previewing */

    val previewIntentData: PreviewerDestination
        get() {
            // If in NOTES mode, we show one Card per Note, as this matches Anki Desktop
            return if (selectedRowCount() > 1) {
                PreviewerDestination(currentIndex = 0, PreviewerIdsFile(cacheDir, selectedRowIds))
            } else {
                // Preview all cards, starting from the one that is currently selected
                val startIndex = indexOfFirstCheckedCard() ?: 0
                PreviewerDestination(startIndex, PreviewerIdsFile(cacheDir, allCardIds))
            }
        }

    /** @return the index of the first checked card in [cards], or `null` if no cards are checked */
    private fun indexOfFirstCheckedCard(): Int? {
        val idToFind = selectedRows.firstOrNull()?.id ?: return null
        return allCardIds.indexOf(idToFind)
    }

    fun setSearchQueryExpanded(expand: Boolean) {
        if (expand) {
            expandSearchQuery()
        } else {
            collapseSearchQuery()
        }
    }

    private fun collapseSearchQuery() {
        searchQueryInputFlow.update { null }
        flowOfSearchQueryExpanded.update { false }
    }

    private fun expandSearchQuery() {
        flowOfSearchQueryExpanded.update { true }
    }

    fun updateQueryText(newText: String) {
        searchQueryInputFlow.update { newText }
    }

    fun removeUnsubmittedInput() {
        searchQueryInputFlow.update { null }
    }

    fun moveSelectedCardsToDeck(deckId: DeckId): Deferred<OpChangesWithCount> = viewModelScope.async {
        val selectedCardIds = queryAllSelectedCardIds()
        return@async undoableOp {
            setDeck(selectedCardIds, deckId)
        }
    }

    suspend fun updateSelectedCardsFlag(flag: Flag): List<Card> {
        val idsToChange = queryAllSelectedCardIds()
        return withCol {
            setUserFlag(flag, cids = idsToChange)
            selectedRowIds
                .map { getCard(it) }
                .onEach { load() }
        }
    }

    /**
     * Turn off [Multi-Select Mode][isInMultiSelectMode] and return to normal state
     */
    fun endMultiSelectMode() = selectNone()

    suspend fun launchSearchForCards(searchQuery: String): Job? {
        searchTerms = searchQuery
        return launchSearchForCards()
    }

    /**
     * @see com.ichi2.anki.searchForCards
     */
    @NeedsTest("Invalid searches are handled. For instance: 'and'")
    suspend fun launchSearchForCards(): Job? {
        if (!initCompleted) return null
        // update the UI while we're searching
        clearCardsList()

        val query: String = if (searchTerms.contains("deck:")) {
            "($searchTerms)"
        } else {
            if ("" != searchTerms) "$restrictOnDeck($searchTerms)" else restrictOnDeck
        }

        searchJob?.cancel()
        searchJob = launchCatchingIO(
            errorMessageHandler = { error -> flowOfSearchState.emit(SearchState.Error(error)) }
        ) {
            flowOfSearchState.emit(SearchState.Searching)
            Timber.d("performing search: '%s'", query)
            val cards = com.ichi2.anki.searchForCards(query, order.toSortOrder(), cardsOrNotes)
            Timber.d("Search returned %d card(s)", cards.size)

            // Render the first few items
            val cardsToRender = min((numCardsToRender ?: 0), cards.size)
            for (i in 0 until cardsToRender) {
                ensureActive()
                cards[i].load(false, column1Index, column2Index)
            }
            ensureActive()
            this@CardBrowserViewModel.cards.replaceWith(cards)
            flowOfSearchState.emit(SearchState.Completed)
        }
        return searchJob!!
    }

    private suspend fun clearCardsList() {
        cards.reset()
        flowOfCardsUpdated.emit(Unit)
    }

    companion object {
        const val DISPLAY_COLUMN_1_KEY = "cardBrowserColumn1"
        const val DISPLAY_COLUMN_2_KEY = "cardBrowserColumn2"
        fun factory(
            lastDeckIdRepository: LastDeckIdRepository,
            cacheDir: File,
            preferencesProvider: SharedPreferencesProvider? = null,
            options: CardBrowserLaunchOptions?
        ) = viewModelFactory {
            initializer {
                CardBrowserViewModel(
                    lastDeckIdRepository,
                    cacheDir,
                    options,
                    preferencesProvider ?: AnkiDroidApp.sharedPreferencesProvider
                )
            }
        }
    }

    /**
     * @param wasBuried `true` if all cards were buried, `false` if unburied
     * @param count the number of affected cards
     */
    data class BuryResult(val wasBuried: Boolean, val count: Int)

    private sealed interface ChangeCardOrder {
        data class OrderChange(val sortType: SortType) : ChangeCardOrder
        data object DirectionChange : ChangeCardOrder
    }

    /** Whether [CardBrowserViewModel] is processing a search */
    sealed interface SearchState {
        /** The class is initializing */
        data object Initializing : SearchState

        /** A search is in progress */
        data object Searching : SearchState

        /** A search has been completed */
        data object Completed : SearchState

        /**
         * A search error, for instance:
         *
         * [net.ankiweb.rsdroid.BackendException.BackendSearchException]
         *
         * Invalid search: an `and` was found but it is not connecting two search terms.
         * If you want to search for the word itself, wrap it in double quotes: `"and"`.
         */
        data class Error(val error: String) : SearchState
    }
}

enum class SaveSearchResult {
    ALREADY_EXISTS,
    SUCCESS
}

/**
 * Temporary file containing the IDs of the cards to be displayed at the previewer
 */
class PreviewerIdsFile(path: String) : File(path), Parcelable {

    /**
     * @param directory parent directory of the file. Generally it should be the cache directory
     * @param cardIds ids of the cards to be displayed
     */
    constructor(directory: File, cardIds: List<CardId>) : this(createTempFile("previewerIds", ".tmp", directory).path) {
        DataOutputStream(FileOutputStream(this)).use { outputStream ->
            outputStream.writeInt(cardIds.size)
            for (id in cardIds) {
                outputStream.writeLong(id)
            }
        }
    }

    fun getCardIds(): List<Long> = DataInputStream(FileInputStream(this)).use { inputStream ->
        val size = inputStream.readInt()
        List(size) { inputStream.readLong() }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(path)
    }

    companion object {
        @JvmField
        @Suppress("unused")
        val CREATOR = object : Parcelable.Creator<PreviewerIdsFile> {
            override fun createFromParcel(source: Parcel?): PreviewerIdsFile {
                return PreviewerIdsFile(source!!.readString()!!)
            }

            override fun newArray(size: Int): Array<PreviewerIdsFile> {
                return arrayOf()
            }
        }
    }
}
