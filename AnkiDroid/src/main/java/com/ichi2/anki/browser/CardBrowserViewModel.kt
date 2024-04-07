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

import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckSpinnerSelection.Companion.ALL_DECKS_ID
import com.ichi2.anki.Flag
import com.ichi2.anki.PreviewerDestination
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.DeckSearchFilter
import com.ichi2.anki.model.SortType
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.searchForCards
import com.ichi2.anki.servicelayer.CardService
import com.ichi2.anki.setUserFlag
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.hasTag
import com.ichi2.libanki.note
import com.ichi2.libanki.setTagsFromStr
import com.ichi2.libanki.undoableOp
import com.ichi2.utils.TagsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
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

/**
 * Logic for CardBrowser
 *
 * Handles:
 * * init
 * * searching
 *   * selected deck
 *   * cards or notes
 *   * changes in order
 *   * manual searches
 * * the returned results
 * *
 */
@NeedsTest("reverseDirectionFlow/sortTypeFlow are not updated on .launch { }")
@NeedsTest("13442: selected deck is not changed, as this affects the reviewer")
@NeedsTest("search is called after launch()")
class CardBrowserViewModel(
    private val lastDeckIdRepository: LastDeckIdRepository,
    private val cacheDir: File,
    options: CardBrowserLaunchOptions? = null,
    preferences: SharedPreferencesProvider
) : ViewModel(), SharedPreferencesProvider by preferences {

    /** The CardIds of all the cards in the results */
    val allCardIds get() = cards
        .map { c -> c.id }
        .let { if (orderAsc) it.reversed() else it }

    // This is badly implemented currently: searching for 'is:suspended' for example will take the
    // deck filter, discard the query and apply is:suspended on the deck
    val flowOfFilterQuery = MutableStateFlow("")

    // search terms depend on whether the search query is expanded.
    var searchTerms: String = ""
    var restrictOnDeck: String = ""
        private set

    private lateinit var deckSearchFilter: DeckSearchFilter

    /**
     * Whether the browser is working in Cards mode or Notes mode.
     * default: [CARDS]
     * */
    val flowOfCardsOrNotes = MutableStateFlow(CARDS)
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

    /**
     * The query which is currently in the search box, potentially null.
     * Only set when search box was open
     */
    val searchQueryInputFlow = MutableStateFlow<String>("")

    /** The query which is currently in the search box, potentially null. Only set when search box was open  */
    val tempSearchQuery get() = searchQueryInputFlow.value

    /** Whether the current element in the search bar can be saved */
    val flowOfCanSearch = searchQueryInputFlow
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    // TODO: make this private
    val searchResults = MutableStateFlow<SearchState>(SearchState.Searching(""))

    /**
     * Whether the task launched from CardBrowserViewModel.init has completed
     * If false, we don't have the initial values to perform the first search
     */
    @VisibleForTesting
    val flowOfInitCompleted = MutableStateFlow(false)
    val initCompleted get() = flowOfInitCompleted.value

    val cardFlow = flowOfInitCompleted.combineTransform(searchResults) { initCompleted, results ->
        if (initCompleted) {
            emit(results)
        }
    }.combine(reverseDirectionFlow) { results, reverseDirection ->
        when (results) {
            is SearchState.Searching -> return@combine results
            is SearchState.SearchResults -> {
                val transformedData = if (reverseDirection.orderAsc) results.results.reversed() else results.results
                return@combine SearchState.SearchResults(results.query, transformedData)
            }
        }
    }

    val canPreview = cardFlow.map { it.size() > 0 }

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
    val selectedRowIds: List<Long>
        get() = selectedRows.map { c -> c.id }

    // TODO: This probably shouldn't be a flow
    var numberOfCardsToRenderFlow = MutableStateFlow(0)

    private val selectedFlagFlow = MutableStateFlow<Flag?>(null)
    val currentFlag
        get() = selectedFlagFlow.value

    private val manuallyPerformSearchFlow = MutableSharedFlow<Unit>()

    val selectedCardIds: List<Long>
        get() = selectedRows.map { c -> c.id }

    suspend fun queryAllSelectedCardIds(): List<CardId> = when (cardsOrNotes) {
        CARDS -> selectedRowIds
        NOTES ->
            selectedRows
                .flatMap { row -> withCol { cardIdsOfNote(nid = row.card.nid) } }
    }

    var lastSelectedPosition = 0

    val flowOfIsInMultiSelectMode = flowOfSelectedRows
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    val isInMultiSelectMode
        get() = flowOfIsInMultiSelectMode.value

    private val lastDeckId: DeckId?
        get() = lastDeckIdRepository.lastDeckId

    fun setDeckId(deckId: DeckId) = viewModelScope.launch {
        Timber.i("setting deck: %d", deckId)
        setDeckRestriction(deckId)
        lastDeckIdRepository.lastDeckId = deckId
        flowOfDeckId.update { deckId }
    }

    val flowOfDeckId = MutableStateFlow(lastDeckId)
    val deckId get() = flowOfDeckId.value

    /**
     * A flow which emits a value when a property which should trigger a search
     * TODO: probably should be selectedFlagFlow
     */
    @OptIn(FlowPreview::class)
    val searchRequested = flowOf(manuallyPerformSearchFlow, sortTypeFlow, flowOfCardsOrNotes, flowOfDeckId)
        .flattenMerge()

    private val performSearchFlow = flowOfInitCompleted.combineTransform(searchRequested) { init, search ->
        if (!init) return@combineTransform
        emit(search)
    }.combine(numberOfCardsToRenderFlow) { _, numCardsToRender -> numCardsToRender }

    val cardInfoDestination: CardInfoDestination?
        get() {
            val firstSelectedCard = selectedRowIds.firstOrNull() ?: return null
            return CardInfoDestination(firstSelectedCard)
        }
    val searchQueryExpanded: Boolean
        get() = flowOfSearchQueryExpanded.value

    fun setSearchQueryExpanded(value: Boolean, numCardsToRender: Int) {
        if (!value) {
            collapseSearchQuery(numCardsToRender)
        } else {
            expandSearchQuery()
        }
    }

    private fun collapseSearchQuery(numCardsToRender: Int) {
        viewModelScope.launch {
            selectedFlagFlow.emit(null)
            searchQueryInputFlow.emit("")
            this@CardBrowserViewModel.flowOfFilterQuery.emit("")
            flowOfSearchQueryExpanded.emit(false)
        }
        // TODO: extract searchCards, make this a `var`
        searchTerms = ""
        searchCards(numCardsToRender)
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

        // debugging
        manuallyPerformSearchFlow.onEach { Timber.d("manuallyPerformSearchFlow") }
            .launchIn(viewModelScope)
        sortTypeFlow.onEach { Timber.d("sortTypeFlow") }
            .launchIn(viewModelScope)
        flowOfCardsOrNotes.onEach { Timber.d("cardsOrNotesFlow") }
            .launchIn(viewModelScope)
        flowOfDeckId.onEach { Timber.d("lastDeckIdFlow") }
            .launchIn(viewModelScope)
        searchRequested.onEach { Timber.d("searchRequested") }
            .launchIn(viewModelScope)
        flowOfInitCompleted.onEach { Timber.d("initCompleted") }
            .launchIn(viewModelScope)
        numberOfCardsToRenderFlow.onEach { Timber.d("numberOfCardsToRenderFlow") }
            .launchIn(viewModelScope)

        flowOfColumnIndex1
            .onEach { index -> sharedPrefs().edit { putInt(DISPLAY_COLUMN_1_KEY, index) } }
            .launchIn(viewModelScope)

        flowOfColumnIndex2
            .onEach { index -> sharedPrefs().edit { putInt(DISPLAY_COLUMN_2_KEY, index) } }
            .launchIn(viewModelScope)

        this.flowOfFilterQuery
            .filter { it != "" }
            .onEach { filterQueryText ->
                expandSearchQuery()
                updateQueryText(filterQueryText)
                submitQueryText(numberOfCardsToRenderFlow.first())
            }
            .launchIn(viewModelScope + Dispatchers.IO)

        selectedFlagFlow
            .filterNotNull()
            .onEach { flag ->
                Timber.v("filterByFlag %s", flag)
                setFlagFilter(flag)
            }
            .launchIn(viewModelScope)

        performSearchFlow.onEach { cardsToRender ->
            Timber.v("performSearchFlow: searching")
            searchCards(cardsToRender)
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
            setDeckId(initialDeckId).join()
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

    private suspend fun getInitialDeck(): DeckId {
        // If a valid value for last deck exists then use it, otherwise use libanki selected deck
        val lastDeckId = lastDeckId
        return if (lastDeckId != null && lastDeckId == ALL_DECKS_ID) {
            ALL_DECKS_ID
        } else if (lastDeckId != null && withCol { decks.get(lastDeckId) != null }) {
            lastDeckId
        } else {
            withCol { decks.selected() }
        }
    }

    val cards get() = searchResults.value.all()

    /** Returns the number of rows of the current result set  */
    val rowCount: Int
        get() = searchResults.value.size()

    fun hasSelectedCards(): Boolean {
        return selectedRows.isNotEmpty()
    }

    fun hasSingleSelection(): Boolean {
        return selectedRows.size == 1
    }

    /** Whether any rows are selected */
    fun hasSelectedAnyRows(): Boolean = selectedRows.isNotEmpty()

    /** Selected Flag */

    fun setSelectedFlag(flag: Flag) = viewModelScope.launch {
        Timber.v("change of flag to %s", flag)
        selectedFlagFlow.emit(flag)
    }

    /**
     * Updates the tags of selected/checked notes and saves them to the disk
     * @param selectedTags list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * For more info on [selectedTags] and [indeterminateTags] see [com.ichi2.anki.dialogs.tags.TagsDialogListener.onSelectedTags]
     */
    suspend fun editSelectedCardsTags(selectedTags: List<String>, indeterminateTags: List<String>) =
        undoableOp {
            val selectedNotes = selectedCardIds
                .map { cardId -> getCard(cardId).note() }
                .distinct()
                .onEach { note ->
                    val previousTags: List<String> = note.tags
                    val updatedTags =
                        TagsUtil.getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                    note.setTagsFromStr(tags.join(updatedTags))
                }
            updateNotes(selectedNotes)
        }

    /**
     * All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked
     */
    suspend fun toggleMark() {
        if (!hasSelectedAnyRows()) {
            Timber.i("Not marking cards - nothing selected")
            return
        }
        undoableOp(this) {
            val noteIds = notesOfCards(selectedCardIds)
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
        Timber.d("setting cardsOrNotes to %s", newValue)
        withCol {
            // Change this to only change the preference on a state change
            newValue.saveToCollection()
        }
        flowOfCardsOrNotes.update { newValue }
    }

    /** Last Deck ID */

    fun hasSelectedAllDecks(): Boolean = lastDeckId == ALL_DECKS_ID

    private suspend fun setDeckRestriction(deckId: DeckId) {
        deckSearchFilter = DeckSearchFilter.fromDeckId(deckId)
    }

    fun selectAllDecks() = setDeckId(ALL_DECKS_ID)

    fun searchCards(numCardsToRender: Int): Job? {
        if (!initCompleted) {
            Timber.w("searched before init")
            return null
        }
        val query = deckSearchFilter.filterSearch(searchTerms)
        val order = order.toSortOrder()

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            searchResults.emit(SearchState.Searching(query))
            Timber.d("performing search: %s", query)
            val cards = searchForCards(query, order, cardsOrNotes)
            Timber.d("Search returned %d cards", cards.size)

            // Render the first few items
            val cardsToRender = Math.min(numCardsToRender, cards.size)
            for (i in 0 until cardsToRender) {
                ensureActive()
                cards[i].load(false, column1Index, column2Index)
            }
            ensureActive()
            searchResults.emit(SearchState.SearchResults(query, cards))
            Timber.v("updated search results")
        }
        return searchJob
    }

    /** Submits a query using the currently input search text */
    fun submitQueryText(numCardsToRender: Int): Job = viewModelScope.launch {
        searchTerms = searchQueryInputFlow.value
        Timber.v("submitQueryText: %s", searchTerms)
        searchCards(numCardsToRender)
    }

    fun hasSelectedAllRows(): Boolean {
        // This needs to handle if there are no rows
        return selectedRows.size >= runBlocking { cardFlow.first().size() }
    }

    fun invalidate() {
        _selectedRows.clear()
        // TODO: clear CardOutputFlow
    }

    fun indexOf(id: CardId): Int? {
        val index = searchResults.value.allIds.indexOf(id)
        return if (index == -1) null else index
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
        if (_selectedRows.addAll(searchResults.value.all())) {
            refreshSelectedRowsFlow()
        }
    }

    fun selectNone() {
        if (_selectedRows.isEmpty()) return
        _selectedRows.clear()
        refreshSelectedRowsFlow()
    }

    fun toggleRowSelectionAtPosition(position: Int) {
        val row = getCardFromPosition(position)
        toggleRow(row)
    }

    fun toggleRow(row: CardBrowser.CardCache) {
        if (_selectedRows.contains(row)) {
            _selectedRows.remove(row)
        } else {
            _selectedRows.add(row)
        }
        refreshSelectedRowsFlow()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun selectPositions(vararg positions: Int) {
        var changed = false
        for (pos in positions) {
            if (!_selectedRows.add(getCardFromPosition(pos))) continue
            changed = true
        }
        if (changed) refreshSelectedRowsFlow()
    }

    /**
     * Selects the cards between [startPos] and [endPos]
     */
    fun selectRowsBetweenPositions(startPos: Int, endPos: Int) {
        val cards = (min(startPos, endPos)..max(startPos, endPos)).map { getCardFromPosition(it) }
        if (_selectedRows.addAll(cards)) {
            refreshSelectedRowsFlow()
        }
    }

    /** emits a new value in [flowOfSelectedRows] */
    private fun refreshSelectedRowsFlow() = viewModelScope.launch {
        refreshSelectedRowsFlow.emit(Unit)
    }

    fun selectedRowCount(): Int = selectedRows.size

    fun changeCardOrder(which: SortType) = viewModelScope.launch {
        if (which != order) {
            Timber.i("updating order to %s", which)
            sortTypeFlow.update { which }
            reverseDirectionFlow.update { ReverseDirection(orderAsc = false) }
        } else if (which != SortType.NO_SORTING) {
            Timber.i("reversing search order")
            // if the same element is selected again, reverse the order
            val newValue = !reverseDirectionFlow.first().orderAsc
            withCol { config.set("sortBackwards", newValue) }
            withCol { config.set("browserNoteSortBackwards", newValue) }

            reverseDirectionFlow.emit(ReverseDirection(newValue))
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

    /** @return an [ExportDialogParams] based on the current screen state */
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

    fun getCardIdAtPosition(position: Int): CardId = cards[position].id

    fun getRowAtPosition(position: Int) = cards[position]

    override fun onCleared() {
        super.onCleared()
        invalidate()
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
        this.filter { flowOfInitCompleted.value }

    fun all(): List<CardBrowser.CardCache> =
        searchResults.value.all().let { return if (orderAsc) it.reversed() else it }

    fun getCardFromPosition(position: Int): CardBrowser.CardCache {
        // TODO: Inefficient if called in a loop
        return all()[position]
    }

    fun removeFromView(cardsIds: Set<Long>) {
        val newCardList = searchResults.value.all()
            .filter { !cardsIds.contains(it.id) }
            .map { c -> CardBrowser.CardCache(c) }

        viewModelScope.launch {
            searchResults.emit(
                SearchState.SearchResults(
                    searchResults.value.query,
                    newCardList
                )
            )
        }

        // Suboptimal from a UX perspective, we should reorder
        // but this is only hit on a rare sad path and we'd need to rejig the data structures to allow an efficient
        // search
        Timber.w("Removing current selection due to unexpected removal of cards")
        selectNone()
    }

    sealed interface SearchState {
        val allIds: LongArray get() = when (this) {
            is Searching -> longArrayOf()
            is SearchResults -> this.results.map { it.id }.toLongArray()
        }

        fun size(): Int {
            return when (this) {
                is Searching -> 0
                is SearchResults -> this.results.size
            }
        }

        operator fun get(position: Int): CardBrowser.CardCache? {
            return when (this) {
                is Searching -> null
                is SearchResults -> this.results[position]
            }
        }

        fun all(): List<CardBrowser.CardCache> {
            return when (this) {
                is Searching -> emptyList()
                is SearchResults -> this.results
            }
        }

        val query: String

        data class Searching(override val query: String) : SearchState
        data class SearchResults(override val query: String, val results: List<CardBrowser.CardCache>) :
            SearchState
    }

    private var searchJob: Job? = null

    suspend fun setFilterQuery(filterQuery: String) {
        this.flowOfFilterQuery.emit(filterQuery)
    }

    fun searchForMarkedNotes() = viewModelScope.launch {
        this@CardBrowserViewModel.flowOfFilterQuery.emit("tag:marked")
    }

    fun searchForSuspendedCards() = viewModelScope.launch {
        this@CardBrowserViewModel.flowOfFilterQuery.emit("is:suspended")
    }

    suspend fun setFlagFilter(flag: Flag): Job {
        val flagSearchTerm = "flag:${flag.code}"
        val searchTerms = when {
            searchTerms.contains("flag:") -> searchTerms.replaceFirst("flag:.".toRegex(), flagSearchTerm)
            searchTerms.isNotEmpty() -> "$flagSearchTerm $searchTerms"
            else -> flagSearchTerm
        }
        return viewModelScope.launch {
            this@CardBrowserViewModel.flowOfFilterQuery.emit(searchTerms)
        }
    }

    fun filterByTags(selectedTags: List<String>, cardStateFilter: CardStateFilter): Job {
        val sb = StringBuilder(cardStateFilter.toSearch)
        // join selectedTags as "tag:$tag" with " or " between them
        val tagsConcat = selectedTags.joinToString(" or ") { tag -> "\"tag:$tag\"" }
        if (selectedTags.isNotEmpty()) {
            sb.append("($tagsConcat)") // Only if we added anything to the tag list
        }
        return viewModelScope.launch {
            this@CardBrowserViewModel.flowOfFilterQuery.emit(sb.toString())
        }
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

    /**
     * @return the index of the first checked card in [cards], or `null` if no cards are checked
     * **this must take into account a reversed list**
     */
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
        searchQueryInputFlow.update { "" }
        flowOfSearchQueryExpanded.update { false }
    }

    private fun expandSearchQuery() {
        flowOfSearchQueryExpanded.update { true }
    }

    fun updateQueryText(newText: String) {
        Timber.v("updating query: %s", newText)
        searchQueryInputFlow.update { newText }
    }

    fun removeUnsubmittedInput() {
        searchQueryInputFlow.update { "" }
    }

    suspend fun updateSelectedCardsFlag(flag: Flag): List<Card> {
        val idsToChange = queryAllSelectedCardIds()
        return withCol {
            setUserFlag(flag, cids = idsToChange)
            // TODO: This should have emitted a flow, but we're removing the code really soon
            // instead, the calling code waits on this method and performs the refresh there
            selectedRows
                .onEach { it.load(reload = true, column1Index, column2Index) }
                .map { it.card }
        }
    }

    /**
     * Turn off [Multi-Select Mode][isInMultiSelectMode] and return to normal state
     */
    fun endMultiSelectMode() = selectNone()

    companion object {
        const val DISPLAY_COLUMN_1_KEY = "cardBrowserColumn1"
        const val DISPLAY_COLUMN_2_KEY = "cardBrowserColumn2"
        fun factory(
            lastDeckIdRepository: LastDeckIdRepository,
            cacheDir: File,
            options: CardBrowserLaunchOptions?,
            preferencesProvider: SharedPreferencesProvider? = null
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
}

enum class SaveSearchResult {
    ALREADY_EXISTS,
    SUCCESS
}

/**
 * Temporary file containing the IDs of the cards to be displayed at the previewer
 *
 * @param directory parent directory of the file. Generally it should be the cache directory
 * @param cardIds ids of the cards to be displayed
 */
class PreviewerIdsFile(directory: File, cardIds: List<CardId>) :
    File(createTempFile("previewerIds", ".tmp", directory).absolutePath) {
    init {
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
}
