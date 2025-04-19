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
import androidx.annotation.CheckResult
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.search.BrowserColumns
import anki.search.BrowserRow
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.DeckSpinnerSelection.Companion.ALL_DECKS_ID
import com.ichi2.anki.Flag
import com.ichi2.anki.PreviewerDestination
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ALL_FIELDS_AS_FIELD
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.TAGS_AS_FIELD
import com.ichi2.anki.browser.RepositionCardsRequest.RepositionData
import com.ichi2.anki.export.ExportDialogFragment.ExportType
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.SortType
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.preferences.booleanPref
import com.ichi2.anki.utils.ext.normalizeForSearch
import com.ichi2.anki.utils.ext.setUserFlagForCards
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.CardType
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Note
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.QueueType
import com.ichi2.libanki.QueueType.ManuallyBuried
import com.ichi2.libanki.QueueType.SiblingBuried
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import net.ankiweb.rsdroid.BackendException
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

// TODO: move the tag computation to ViewModel
@NeedsTest("reverseDirectionFlow/sortTypeFlow are not updated on .launch { }")
@NeedsTest("columIndex1/2 config is not not updated on init")
@NeedsTest("13442: selected deck is not changed, as this affects the reviewer")
@NeedsTest("search is called after launch()")
@NeedsTest("default search text updated on init")
class CardBrowserViewModel(
    private val lastDeckIdRepository: LastDeckIdRepository,
    private val cacheDir: File,
    options: CardBrowserLaunchOptions?,
    preferences: SharedPreferencesProvider,
    private val manualInit: Boolean = false,
) : ViewModel(),
    SharedPreferencesProvider by preferences {
    var lastSelectedPosition: Int = 0
    var oldCardTopOffset: Int = 0

    // TODO: abstract so we can use a `Context` and `pref_display_filenames_in_browser_key`
    val showMediaFilenames = sharedPrefs().getBoolean("card_browser_show_media_filenames", false)

    /** A job which ensures that parallel searches do not occur */
    var searchJob: Job? = null
        private set

    // temporary flow for refactoring - called when cards are cleared
    val flowOfCardsUpdated = MutableSharedFlow<Unit>()

    val cards = BrowserRowCollection(CARDS, mutableListOf())

    val flowOfSearchState = MutableSharedFlow<SearchState>()

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

    /**
     * A map from column backend key to backend column definition
     *
     * @see [flowOfColumnHeadings]
     */
    private val flowOfAllColumns = MutableSharedFlow<Map<String, BrowserColumns.Column>>()

    val flowOfActiveColumns =
        MutableStateFlow(
            BrowserColumnCollection(
                listOf(
                    CardBrowserColumn.QUESTION,
                    CardBrowserColumn.ANSWER,
                ),
            ),
        )

    @get:VisibleForTesting
    val activeColumns
        get() = flowOfActiveColumns.value.columns

    val flowOfSearchQueryExpanded = MutableStateFlow(false)

    private val searchQueryInputFlow = MutableStateFlow<String?>(null)

    /** The query which is currently in the search box, potentially null. Only set when search box was open  */
    val tempSearchQuery get() = searchQueryInputFlow.value

    /** Whether the current element in the search bar can be saved */
    val flowOfCanSearch =
        searchQueryInputFlow
            .map { it?.isNotEmpty() == true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    val flowOfIsTruncated: MutableStateFlow<Boolean> =
        MutableStateFlow(sharedPrefs().getBoolean("isTruncated", false))
    val isTruncated get() = flowOfIsTruncated.value

    var tapCardToEdit by booleanPref("tapCardToEdit", true)

    var shouldIgnoreAccents: Boolean = false

    var defaultBrowserSearch: String? = null

    private val _selectedRows: MutableSet<CardOrNoteId> = Collections.synchronizedSet(LinkedHashSet())

    // immutable accessor for _selectedRows
    val selectedRows: Set<CardOrNoteId> get() = _selectedRows

    private val refreshSelectedRowsFlow = MutableSharedFlow<Unit>()
    val flowOfSelectedRows: Flow<Set<CardOrNoteId>> =
        flowOf(selectedRows).combine(refreshSelectedRowsFlow) { row, _ -> row }

    suspend fun queryAllSelectedCardIds() = selectedRows.queryCardIds(this.cardsOrNotes)

    suspend fun queryAllSelectedNoteIds() = selectedRows.queryNoteIds(this.cardsOrNotes)

    @VisibleForTesting
    internal suspend fun queryAllCardIds() = cards.queryCardIds()

    var lastSelectedId: CardOrNoteId? = null

    val flowOfIsInMultiSelectMode =
        flowOfSelectedRows
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    val isInMultiSelectMode
        get() = flowOfIsInMultiSelectMode.value

    val lastDeckId: DeckId?
        get() = lastDeckIdRepository.lastDeckId

    private suspend fun initDefaultSearch() =
        withCol {
            shouldIgnoreAccents = config.getBool(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH)
            defaultBrowserSearch = config.getString(ConfigKey.String.DEFAULT_SEARCH_TEXT)
        }

    suspend fun setDeckId(deckId: DeckId) {
        Timber.i("setting deck: %d", deckId)
        lastDeckIdRepository.lastDeckId = deckId
        restrictOnDeck =
            if (deckId == ALL_DECKS_ID) {
                ""
            } else {
                val deckName = withCol { decks.name(deckId) }
                "deck:\"$deckName\" "
            }
        flowOfDeckId.update { deckId }
    }

    val flowOfDeckId = MutableStateFlow(lastDeckId)
    val deckId get() = flowOfDeckId.value

    suspend fun queryCardInfoDestination(): CardInfoDestination? {
        val firstSelectedCard = selectedRows.firstOrNull()?.toCardId(cardsOrNotes) ?: return null
        return CardInfoDestination(firstSelectedCard)
    }

    suspend fun queryDataForCardEdit(id: CardOrNoteId): CardId = id.toCardId(cardsOrNotes)

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

    val flowOfColumnHeadings: StateFlow<List<ColumnHeading>> =
        combine(flowOfActiveColumns, flowOfCardsOrNotes, flowOfAllColumns) { activeColumns, cardsOrNotes, allColumns ->
            Timber.d("updated headings for %d columns", activeColumns.count)
            activeColumns.columns.map {
                ColumnHeading(
                    label = allColumns[it.ankiColumnKey]!!.getLabel(cardsOrNotes),
                )
            }
            // stateIn is required for tests
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = emptyList())

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
    private val searchRequested =
        flowOf(flowOfCardsOrNotes, flowOfDeckId)
            .flattenMerge()

    /**
     * Emits an item when:
     * * [initCompleted] is true
     * * A property which defines the search has been changed ([searchRequested])
     *
     * @see launchSearchForCards
     */
    private val performSearchFlow =
        flowOfInitCompleted.combineTransform(searchRequested) { init, _ ->
            if (!init) return@combineTransform
            emit(Unit)
        }

    init {
        Timber.d("CardBrowserViewModel::init")

        var selectAllDecks = false
        when (options) {
            is CardBrowserLaunchOptions.SystemContextMenu -> {
                searchTerms = options.search.toString()
            }
            is CardBrowserLaunchOptions.SearchQueryJs -> {
                searchTerms = options.search
                selectAllDecks = options.allDecks
            }
            is CardBrowserLaunchOptions.DeepLink -> {
                searchTerms = options.search
            }
            null -> {}
        }

        performSearchFlow
            .onEach {
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

        flowOfCardsOrNotes
            .onEach { cardsOrNotes ->
                Timber.d("loading columns for %s mode", cardsOrNotes)
                updateActiveColumns(BrowserColumnCollection.load(sharedPrefs(), cardsOrNotes))
            }.launchIn(viewModelScope)

        viewModelScope.launch {
            initDefaultSearch()
            // Prioritize intent-based search
            if (searchTerms.isEmpty()) {
                val defaultSearchText = withCol { config.getString(ConfigKey.String.DEFAULT_SEARCH_TEXT) }

                Timber.d("Default search term text: $defaultSearchText")
                if (defaultSearchText.isNotEmpty()) {
                    searchTerms = defaultSearchText
                }
            }

            val initialDeckId = if (selectAllDecks) ALL_DECKS_ID else getInitialDeck()
            // PERF: slightly inefficient if the source was lastDeckId
            setDeckId(initialDeckId)
            refreshBackendColumns()

            val cardsOrNotes = withCol { CardsOrNotes.fromCollection(this@withCol) }
            flowOfCardsOrNotes.update { cardsOrNotes }

            withCol {
                sortTypeFlow.update { SortType.fromCol(config, cardsOrNotes, sharedPrefs()) }
                reverseDirectionFlow.update { ReverseDirection.fromConfig(config) }
            }
            Timber.i("initCompleted")

            if (!manualInit) {
                flowOfInitCompleted.update { true }
                launchSearchForCards()
            }
        }
    }

    /**
     * Called if `onCreate` is called again, which may be due to the collection being reopened
     *
     * If this is the case, the backend has lost the active columns state, which is required for
     * [transformBrowserRow]
     */
    fun onReinit() {
        // this can occur after process death, if so, the ViewModel starts normally
        if (!initCompleted) return

        Timber.d("onReinit: executing")

        // we currently have no way to test whether setActiveBrowserColumns was called
        // so set it again. This needs to be done immediately to ensure that the RecyclerView
        // gets correct values when initialized
        CollectionManager
            .getBackend()
            .setActiveBrowserColumns(flowOfActiveColumns.value.backendKeys)

        // if the language has changed, the backend column labels may have changed
        viewModelScope.launch {
            refreshBackendColumns()
        }
    }

    /** Handles an update to the list of backend columns */
    private suspend fun refreshBackendColumns() {
        flowOfAllColumns.emit(withCol { allBrowserColumns() }.associateBy { it.key })
    }

    /** Handles an update of the visible columns */
    private suspend fun updateActiveColumns(columns: BrowserColumnCollection) {
        Timber.d("updating active columns")
        withCol { backend.setActiveBrowserColumns(columns.backendKeys) }
        flowOfActiveColumns.update { columns }
    }

    @VisibleForTesting
    fun manualInit() {
        require(manualInit) { "'manualInit' should be true" }
        flowOfInitCompleted.update { true }
        Timber.d("manualInit")
    }

    /** Whether any rows are selected */
    fun hasSelectedAnyRows(): Boolean = selectedRows.isNotEmpty()

    /**
     * All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked
     */
    suspend fun toggleMark() {
        val cardIds = queryAllSelectedCardIds()
        if (cardIds.isEmpty()) {
            Timber.i("Not marking cards - nothing selected")
            return
        }
        undoableOp(this) {
            val noteIds = notesOfCards(cardIds)
            // if all notes are marked, remove the mark
            // if no notes are marked, add the mark
            // if there is a mix, enable the mark on all
            val wantMark = !noteIds.all { getNote(it).hasTag(this@undoableOp, "marked") }
            Timber.i("setting mark = %b for %d notes", wantMark, noteIds.size)
            if (wantMark) {
                tags.bulkAdd(noteIds, "marked")
            } else {
                tags.bulkRemove(noteIds, "marked")
            }
        }
    }

    /**
     * Deletes the selected notes,
     * @return the number of deleted notes
     */
    suspend fun deleteSelectedNotes(): Int {
        // PERF: use `undoableOp(this)` & notify CardBrowser of changes
        // this does a double search
        val cardIds = queryAllSelectedCardIds()
        return undoableOp { removeNotes(cids = cardIds) }
            .count
            .also {
                endMultiSelectMode()
                refreshSearch()
            }
    }

    fun setCardsOrNotes(newValue: CardsOrNotes) =
        viewModelScope.launch {
            Timber.i("setting mode to %s", newValue)
            withCol {
                // Change this to only change the preference on a state change
                newValue.saveToCollection(this@withCol)
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

    suspend fun getTemplatePreviewArguments(cardId: CardId): TemplatePreviewerUiArguments {
        val noteId = withCol { notesOfCards(mutableListOf(cardId)).first() }
        val note = withCol { getNote(noteId) }
        val ord =
            if (note.notetype.isCloze) {
                val tempNote = withCol { Note.fromNotetypeId(this@withCol, note.notetype.id) }
                tempNote.fields = note.fields // makes possible to get the cloze numbers from the fields
                val clozeNumbers = withCol { clozeNumbersInNote(tempNote) }
                if (clozeNumbers.isNotEmpty()) {
                    clozeNumbers.first() - 1
                } else {
                    0
                }
            } else {
                0
            }
        return TemplatePreviewerUiArguments(
            notetype = withCol { note.notetype },
            fields = note.fields,
            tags = note.tags,
            id = noteId,
            ord = ord,
            fillEmpty = false,
        )
    }

    fun setIgnoreAccents(value: Boolean) {
        Timber.d("Setting ignore accent in search to: $value")
        viewModelScope.launch {
            shouldIgnoreAccents = value
            withCol { config.setBool(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH, value) }
        }
    }

    fun setDefaultSearchText(text: String) {
        Timber.d("Setting default search text to: $text")
        viewModelScope.launch {
            defaultBrowserSearch = text
            withCol { config.setString(ConfigKey.String.DEFAULT_SEARCH_TEXT, text) }
        }
    }

    fun selectAll() {
        if (_selectedRows.addAll(cards)) {
            Timber.d("selecting all: %d item(s)", cards.size)
            refreshSelectedRowsFlow()
        }
    }

    fun selectNone() {
        if (_selectedRows.isEmpty()) return
        Timber.d("selecting none")
        _selectedRows.clear()
        refreshSelectedRowsFlow()
    }

    @VisibleForTesting
    fun toggleRowSelectionAtPosition(position: Int) = toggleRowSelection(cards[position])

    fun toggleRowSelection(id: CardOrNoteId) {
        if (_selectedRows.contains(id)) {
            _selectedRows.remove(id)
        } else {
            _selectedRows.add(id)
        }
        Timber.d("toggled selecting id '%s'; %d selected", id, selectedRowCount())
        lastSelectedId = id
        refreshSelectedRowsFlow()
    }

    @VisibleForTesting
    fun selectRowAtPosition(pos: Int) {
        if (_selectedRows.add(cards[pos])) {
            refreshSelectedRowsFlow()
        }
    }

    fun selectRowsBetween(
        start: CardOrNoteId,
        end: CardOrNoteId,
    ) {
        val startPos = cards.indexOf(start)
        val endPos = cards.indexOf(end)

        selectRowsBetweenPositions(startPos, endPos)
    }

    /**
     * @throws BackendException if the row is deleted
     */
    fun transformBrowserRow(id: CardOrNoteId): Pair<BrowserRow, Boolean> {
        val row = CollectionManager.getBackend().browserRowForId(id.cardOrNoteId)
        val isSelected = selectedRows.contains(id)
        return Pair(row, isSelected)
    }

    /**
     * Selects the cards between [startPos] and [endPos]
     */
    fun selectRowsBetweenPositions(
        startPos: Int,
        endPos: Int,
    ) {
        val begin = min(startPos, endPos)
        val end = max(startPos, endPos)
        Timber.d("selecting indices between %d and %d", begin, end)
        val cards = (begin..end).map { cards[it] }
        if (_selectedRows.addAll(cards)) {
            refreshSelectedRowsFlow()
        }
    }

    /** emits a new value in [flowOfSelectedRows] */
    private fun refreshSelectedRowsFlow() =
        viewModelScope.launch {
            refreshSelectedRowsFlow.emit(Unit)
            Timber.d("refreshed selected rows")
        }

    fun selectedRowCount(): Int = selectedRows.size

    suspend fun selectedNoteCount() = selectedRows.queryNoteIds(cardsOrNotes).distinct().size

    fun hasSelectedAllDecks(): Boolean = lastDeckId == ALL_DECKS_ID

    fun changeCardOrder(which: SortType) {
        val changeType =
            when {
                which != order -> ChangeCardOrder.OrderChange(which)
                // if the same element is selected again, reverse the order
                which != SortType.NO_SORTING -> ChangeCardOrder.DirectionChange
                else -> null
            } ?: return

        Timber.i("updating order: %s", changeType)

        when (changeType) {
            is ChangeCardOrder.OrderChange -> {
                sortTypeFlow.update { which }
                reverseDirectionFlow.update { ReverseDirection(orderAsc = false) }
                launchSearchForCards()
            }
            ChangeCardOrder.DirectionChange -> {
                reverseDirectionFlow.update { ReverseDirection(orderAsc = !orderAsc) }
                cards.reverse()
                viewModelScope.launch { flowOfSearchState.emit(SearchState.Completed) }
            }
        }
    }

    /**
     * Updates the backend with a new collection of columns
     *
     * @param columns the new columns to use
     * @param cardsOrNotes the mode to update columns for. If this is the active mode, then flows
     *  will be updated with the new columns
     *
     * @return Whether the operation was successful (a valid list was provided, and it was a change)
     */
    @CheckResult
    fun updateActiveColumns(
        columns: List<CardBrowserColumn>,
        cardsOrNotes: CardsOrNotes,
    ): Boolean {
        if (columns.isEmpty()) {
            Timber.d("updateColumns: no columns")
            return false
        }
        if (activeColumns == columns) {
            Timber.d("updateColumns: no changes")
            return false
        }

        // update the backend with the new columns
        val columnCollection =
            BrowserColumnCollection.replace(sharedPrefs(), cardsOrNotes, columns).newColumns

        // A user can edit the non-active columns if they:
        // * Edit the cards/notes setting in the browser options
        // * Edit the visible columns
        // * Save the columns and discard the options changes
        val isEditingCurrentHeadings = cardsOrNotes == this.cardsOrNotes
        Timber.d("editing columns for current headings: %b", isEditingCurrentHeadings)

        if (isEditingCurrentHeadings) {
            viewModelScope.launch {
                updateActiveColumns(columnCollection)
            }
        }

        return true
    }

    /**
     * Toggles the 'suspend' state of the selected cards
     *
     * If all cards are suspended, unsuspend all
     * If no cards are suspended, suspend all
     * If there is a mix, suspend all
     *
     * Changes are handled by [ChangeManager]
     */
    fun toggleSuspendCards() =
        viewModelScope.launch {
            if (!hasSelectedAnyRows()) {
                return@launch
            }
            Timber.d("toggling selected cards suspend status")
            val cardIds = queryAllSelectedCardIds()

            undoableOp<OpChanges> {
                val wantUnsuspend = cardIds.all { getCard(it).queue == QueueType.Suspended }
                if (wantUnsuspend) {
                    sched.unsuspendCards(cardIds)
                } else {
                    sched.suspendCards(cardIds).changes
                }
            }
            Timber.d("finished 'toggleSuspendCards'")
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
        fun Card.isBuried(): Boolean = queue == ManuallyBuried || queue == SiblingBuried

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

    fun querySelectionExportData(): Pair<ExportType, List<Long>>? {
        if (!hasSelectedAnyRows()) return null
        return when (this.cardsOrNotes) {
            CARDS -> Pair(ExportType.Cards, selectedRows.map { it.cardOrNoteId })
            NOTES -> Pair(ExportType.Notes, selectedRows.map { it.cardOrNoteId })
        }
    }

    /**
     * Obtains data to be displayed to the user then sent to [repositionSelectedRows]
     */
    @NeedsTest("verify behavior for repositioning with 'Randomize order'")
    suspend fun prepareToRepositionCards(): RepositionCardsRequest {
        val selectedCardIds = queryAllSelectedCardIds()
        // Only new cards may be repositioned (If any non-new found show error dialog and return false)
        if (selectedCardIds.any { withCol { getCard(it).queue != QueueType.New } }) {
            return RepositionCardsRequest.ContainsNonNewCardsError
        }

        // query obtained from Anki Desktop
        // https://github.com/ankitects/anki/blob/1fb1cbbf85c48a54c05cb4442b1b424a529cac60/qt/aqt/operations/scheduling.py#L117
        try {
            return withCol {
                val (min, max) =
                    db
                        .query("select min(due), max(due) from cards where type=? and odid=0", CardType.New.code)
                        .use {
                            it.moveToNext()
                            Pair(max(0, it.getInt(0)), it.getInt(1))
                        }
                val defaults = sched.repositionDefaults()
                RepositionData(
                    min = min,
                    max = max,
                    random = defaults.random,
                    shift = defaults.shift,
                )
            }
        } catch (e: Exception) {
            // TODO: Remove this once we've verified no production errors
            Timber.w(e, "getting min/max position")
            CrashReportService.sendExceptionReport(e, "prepareToRepositionCards")
            return RepositionData(
                min = null,
                max = null,
            )
        }
    }

    /**
     * @see [com.ichi2.libanki.sched.Scheduler.sortCards]
     * @return the number of cards which were repositioned
     */
    suspend fun repositionSelectedRows(
        position: Int,
        step: Int,
        shuffle: Boolean,
        shift: Boolean,
    ): Int {
        val ids = queryAllSelectedCardIds()
        Timber.d("repositioning %d cards to %d", ids.size, position)
        return undoableOp {
            sched.sortCards(cids = ids, position, step = step, shuffle = shuffle, shift = shift)
        }.count
    }

    /** Returns the number of rows of the current result set  */
    val rowCount: Int
        get() = cards.size

    fun getRowAtPosition(position: Int) = cards[position]

    private suspend fun updateSavedSearches(func: MutableMap<String, String>.() -> Unit): Map<String, String> {
        val filters = savedSearches().toMutableMap()
        func(filters)
        withCol { config.set("savedFilters", filters) }
        return filters
    }

    suspend fun savedSearches(): Map<String, String> = withCol { config.get("savedFilters") } ?: hashMapOf()

    fun savedSearchesUnsafe(col: com.ichi2.libanki.Collection): Map<String, String> = col.config.get("savedFilters") ?: hashMapOf()

    suspend fun removeSavedSearch(searchName: String): Map<String, String> {
        Timber.d("removing user search")
        return updateSavedSearches {
            remove(searchName)
        }
    }

    @CheckResult
    suspend fun saveSearch(
        searchName: String,
        searchTerms: String,
    ): SaveSearchResult {
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
    private fun <T> Flow<T>.ignoreValuesFromViewModelLaunch(): Flow<T> = this.filter { initCompleted }

    private suspend fun setFilterQuery(filterQuery: String) {
        this.flowOfFilterQuery.emit(filterQuery)
        launchSearchForCards(filterQuery)
    }

    /**
     * Searches for all marked notes and replaces the current search results with these marked notes.
     */
    suspend fun searchForMarkedNotes() {
        // only intended to be used if the user has no selection
        if (hasSelectedAnyRows()) return
        setFilterQuery("tag:marked")
    }

    /**
     * Searches for all suspended cards and replaces the current search results with these suspended cards.
     */
    suspend fun searchForSuspendedCards() {
        // only intended to be used if the user has no selection
        if (hasSelectedAnyRows()) return
        setFilterQuery("is:suspended")
    }

    suspend fun setFlagFilter(flag: Flag) {
        Timber.i("filtering to flag: %s", flag)
        val flagSearchTerm = "flag:${flag.code}"
        val searchTerms =
            when {
                searchTerms.contains("flag:") -> searchTerms.replaceFirst("flag:.".toRegex(), flagSearchTerm)
                searchTerms.isNotEmpty() -> "$flagSearchTerm $searchTerms"
                else -> flagSearchTerm
            }
        setFilterQuery(searchTerms)
    }

    suspend fun filterByTags(
        selectedTags: List<String>,
        cardState: CardStateFilter,
    ) {
        val sb = StringBuilder(cardState.toSearch)
        // join selectedTags as "tag:$tag" with " or " between them
        val tagsConcat = selectedTags.joinToString(" or ") { tag -> "\"tag:$tag\"" }
        if (selectedTags.isNotEmpty()) {
            sb.append("($tagsConcat)") // Only if we added anything to the tag list
        }
        setFilterQuery(sb.toString())
    }

    /** Previewing */
    suspend fun queryPreviewIntentData(): PreviewerDestination {
        // If in NOTES mode, we show one Card per Note, as this matches Anki Desktop
        return if (selectedRowCount() > 1) {
            PreviewerDestination(currentIndex = 0, PreviewerIdsFile(cacheDir, queryAllSelectedCardIds()))
        } else {
            // Preview all cards, starting from the one that is currently selected
            val startIndex = indexOfFirstCheckedCard() ?: 0
            PreviewerDestination(startIndex, PreviewerIdsFile(cacheDir, queryOneCardIdPerNote()))
        }
    }

    private suspend fun queryOneCardIdPerNote(): List<CardId> = cards.queryOneCardIdPerRow()

    /** @return the index of the first checked card in [cards], or `null` if no cards are checked */
    private fun indexOfFirstCheckedCard(): Int? {
        val idToFind = selectedRows.firstOrNull() ?: return null
        return cards.indexOf(idToFind)
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

    fun moveSelectedCardsToDeck(deckId: DeckId): Deferred<OpChangesWithCount> =
        viewModelScope.async {
            val selectedCardIds = queryAllSelectedCardIds()
            return@async undoableOp {
                setDeck(selectedCardIds, deckId)
            }
        }

    suspend fun updateSelectedCardsFlag(flag: Flag): List<CardId> {
        val idsToChange = queryAllSelectedCardIds()
        undoableOp { setUserFlagForCards(cids = idsToChange, flag = flag) }
        return idsToChange
    }

    /**
     * Turn off [Multi-Select Mode][isInMultiSelectMode] and return to normal state
     */
    fun endMultiSelectMode() = selectNone()

    /**
     * @param forceRefresh if `true`, perform a search even if the search query is unchanged
     */
    fun launchSearchForCards(
        searchQuery: String,
        forceRefresh: Boolean = true,
    ) {
        if (!forceRefresh && searchTerms == searchQuery) {
            Timber.d("skipping duplicate search: forceRefresh is false")
            return
        }
        searchTerms =
            if (shouldIgnoreAccents) {
                searchQuery.normalizeForSearch()
            } else {
                searchQuery
            }

        viewModelScope.launch {
            launchSearchForCards()
        }
    }

    /**
     * @see com.ichi2.anki.searchForRows
     */
    @NeedsTest("Invalid searches are handled. For instance: 'and'")
    fun launchSearchForCards() {
        if (!initCompleted) return

        viewModelScope.launch {
            // update the UI while we're searching
            clearCardsList()

            val query: String =
                if (searchTerms.contains("deck:")) {
                    "($searchTerms)"
                } else {
                    if ("" != searchTerms) "$restrictOnDeck($searchTerms)" else restrictOnDeck
                }

            searchJob?.cancel()
            searchJob =
                launchCatchingIO(
                    errorMessageHandler = { error -> flowOfSearchState.emit(SearchState.Error(error)) },
                ) {
                    flowOfSearchState.emit(SearchState.Searching)
                    Timber.d("performing search: '%s'", query)
                    val cards = com.ichi2.anki.searchForRows(query, order.toSortOrder(), cardsOrNotes)
                    Timber.d("Search returned %d card(s)", cards.size)

                    ensureActive()
                    this@CardBrowserViewModel.cards.replaceWith(cardsOrNotes, cards)
                    flowOfSearchState.emit(SearchState.Completed)
                }
        }
    }

    private fun refreshSearch() = launchSearchForCards()

    private suspend fun clearCardsList() {
        cards.reset()
        flowOfCardsUpdated.emit(Unit)
    }

    suspend fun queryCardIdAtPosition(index: Int): CardId = cards.queryCardIdsAt(index).first()

    suspend fun querySelectedCardIdAtPosition(index: Int): CardId = selectedRows.toList()[index].toCardId(cardsOrNotes)

    /**
     * Obtains two lists of column headings with preview data
     * (preview uses the first row of data, if it exists)
     *
     * The two lists are:
     * (1): An ordered list of columns which is displayed to the user
     * (2): A list of columns which are available to display to the user
     */
    suspend fun previewColumnHeadings(cardsOrNotes: CardsOrNotes): Pair<List<ColumnWithSample>, List<ColumnWithSample>> {
        val currentColumns =
            when {
                // if we match, use the loaded the columns
                cardsOrNotes == this.cardsOrNotes -> activeColumns
                else -> BrowserColumnCollection.load(sharedPrefs(), cardsOrNotes).columns
            }

        val columnsWithSample = ColumnWithSample.loadSample(cards.firstOrNull(), cardsOrNotes)

        // we return this as two lists as 'currentColumns' uses the collection ordering
        return Pair(
            columnsWithSample
                .filter { currentColumns.contains(it.columnType) }
                .sortedBy { currentColumns.indexOf(it.columnType) },
            columnsWithSample.filter { !currentColumns.contains(it.columnType) },
        )
    }

    /**
     * Replaces occurrences of search with the new value.
     *
     * @return the number of affected notes
     * @see com.ichi2.libanki.Collection.findReplace
     * @see com.ichi2.libanki.Tags.findAndReplace
     */
    fun findAndReplace(result: FindReplaceResult) =
        viewModelScope.async {
            // TODO pass the selection as the user saw it in the dialog to avoid running "find
            //  and replace" on a different selection
            val noteIds = if (result.onlyOnSelectedNotes) queryAllSelectedNoteIds() else emptyList()

            if (result.field == TAGS_AS_FIELD) {
                undoableOp {
                    tags.findAndReplace(noteIds, result.search, result.replacement, result.regex, result.matchCase)
                }.count
            } else {
                val field =
                    if (result.field == ALL_FIELDS_AS_FIELD) null else result.field
                undoableOp {
                    findReplace(noteIds, result.search, result.replacement, result.regex, field, result.matchCase)
                }.count
            }
        }

    companion object {
        fun factory(
            lastDeckIdRepository: LastDeckIdRepository,
            cacheDir: File,
            preferencesProvider: SharedPreferencesProvider? = null,
            options: CardBrowserLaunchOptions?,
        ) = viewModelFactory {
            initializer {
                CardBrowserViewModel(
                    lastDeckIdRepository,
                    cacheDir,
                    options,
                    preferencesProvider ?: AnkiDroidApp.sharedPreferencesProvider,
                )
            }
        }
    }

    /**
     * @param wasBuried `true` if all cards were buried, `false` if unburied
     * @param count the number of affected cards
     */
    data class BuryResult(
        val wasBuried: Boolean,
        val count: Int,
    )

    private sealed interface ChangeCardOrder {
        data class OrderChange(
            val sortType: SortType,
        ) : ChangeCardOrder

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
        data class Error(
            val error: String,
        ) : SearchState
    }

    fun saveScrollingState(id: CardOrNoteId) {
        cards.indexOf(id).takeIf { it >= 0 }?.let { position ->
            lastSelectedPosition = position
        }
    }
}

enum class SaveSearchResult {
    ALREADY_EXISTS,
    SUCCESS,
}

/**
 * Temporary file containing the IDs of the cards to be displayed at the previewer
 */
class PreviewerIdsFile(
    path: String,
) : File(path),
    Parcelable {
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

    fun getCardIds(): List<Long> =
        DataInputStream(FileInputStream(this)).use { inputStream ->
            val size = inputStream.readInt()
            List(size) { inputStream.readLong() }
        }

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(path)
    }

    companion object {
        @JvmField
        @Suppress("unused")
        val CREATOR =
            object : Parcelable.Creator<PreviewerIdsFile> {
                override fun createFromParcel(source: Parcel?): PreviewerIdsFile = PreviewerIdsFile(source!!.readString()!!)

                override fun newArray(size: Int): Array<PreviewerIdsFile> = arrayOf()
            }
    }
}

sealed class RepositionCardsRequest {
    /** Only new cards may be repositioned */
    data object ContainsNonNewCardsError : RepositionCardsRequest()

    /** Should contain queue top & bottom positions. Null on error */
    class RepositionData(
        val min: Int?,
        val max: Int?,
        val random: Boolean = false,
        val shift: Boolean = false,
    ) : RepositionCardsRequest() {
        val queueTop: Int?
        val queueBottom: Int?

        init {
            if (min != null && max != null) {
                // queue top: the lower of the two
                queueTop = min(min, max)
                queueBottom = max(min, max)
            } else {
                queueTop = null
                queueBottom = null
            }
        }

        fun toHumanReadableContent(): String? {
            if (queueTop == null || queueBottom == null) return null
            // ints are required for the translation
            return TR.browsingQueueTop(queueTop) + "\n" + TR.browsingQueueBottom(queueBottom)
        }
    }
}

fun BrowserColumns.Column.getLabel(cardsOrNotes: CardsOrNotes): String = if (cardsOrNotes == CARDS) cardsModeLabel else notesModeLabel

data class ColumnHeading(
    val label: String,
)

data class TemplatePreviewerUiArguments(
    val notetype: NotetypeJson,
    val fields: MutableList<String>,
    val tags: MutableList<String>,
    val id: NoteId = 0,
    val ord: Int = 0,
    val fillEmpty: Boolean = false,
    val deckId: DeckId = DEFAULT_DECK_ID,
)
