/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import anki.collection.OpChanges
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivityProvider
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.FilteredDeckOptions
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.MultiSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.RowSelection
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_ALL
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_NONE
import com.ichi2.anki.browser.RepositionCardFragment.Companion.REQUEST_REPOSITION_NEW_CARDS
import com.ichi2.anki.browser.RepositionCardsRequest.ContainsNonNewCardsError
import com.ichi2.anki.browser.RepositionCardsRequest.RepositionData
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.CardBrowserOrderDialog
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.model.SortType
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.requireNavigationDrawerActivity
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.attachFastScroller
import com.ichi2.anki.undoAndShowSnackbar
import com.ichi2.anki.utils.ext.getCurrentDialogFragment
import com.ichi2.anki.utils.ext.ifNotZero
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.visibleItemPositions
import com.ichi2.anki.utils.showDialogFragmentImpl
import com.ichi2.anki.withProgress
import com.ichi2.utils.HandlerUtils
import com.ichi2.utils.TagsUtil.getUpdatedTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Translations
import timber.log.Timber

// Minor BUG: 'don't keep activities' and huge selection
// At some point, starting between 35k and 60k selections, the scroll position is lost on recreation
// This occurred on a Pixel 9 Pro, Android 15
class CardBrowserFragment :
    Fragment(),
    AnkiActivityProvider,
    ChangeManager.Subscriber,
    TagsDialogListener {
    val activityViewModel: CardBrowserViewModel by activityViewModels()

    val viewModel: CardBrowserFragmentViewModel by viewModels()

    override val ankiActivity: CardBrowser
        get() = requireAnkiActivity() as CardBrowser

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var cardsAdapter: BrowserMultiColumnAdapter

    @VisibleForTesting
    lateinit var cardsListView: RecyclerView

    /** LayoutManager for [cardsListView] */
    val layoutManager: LinearLayoutManager
        get() = cardsListView.layoutManager as LinearLayoutManager

    @VisibleForTesting
    lateinit var browserColumnHeadings: ViewGroup

    lateinit var toggleRowSelections: ImageButton

    private lateinit var progressIndicator: LinearProgressIndicator

    // DEFECT: Doesn't need to be a local
    private var tagsDialogListenerAction: TagsDialogListenerAction? = null
    private val tagsDialogFactory: TagsDialogFactory
        get() = ankiActivity.tagsDialogFactory

    private var undoSnackbar: Snackbar? = null

    // Dev option for Issue 18709
    private val useSearchView: Boolean
        get() = requireCardBrowserActivity().useSearchView

    // only usable if 'useSearchView' is set
    private var searchBar: SearchBar? = null
    private var searchView: SearchView? = null
    private var deckChip: Chip? = null

    @get:LayoutRes
    private val layout: Int
        get() = if (useSearchView) R.layout.card_browser_searchview_fragment else R.layout.card_browser_fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(layout, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Selected cards aren't restored on activity recreation,
        // so it is necessary to dismiss the change deck dialog
        getCurrentDialogFragment<DeckSelectionDialog>()?.let { dialogFragment ->
            if (dialogFragment.requireArguments().getBoolean(CHANGE_DECK_KEY, false)) {
                Timber.d("onCreate(): Change deck dialog dismissed")
                dialogFragment.dismiss()
            }
        }

        cardsListView =
            view.findViewById<RecyclerView>(R.id.card_browser_list).apply {
                attachFastScroller(R.id.browser_scroller)
            }
        DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL).apply {
            setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.browser_divider)!!)
            cardsListView.addItemDecoration(this)
        }
        cardsAdapter =
            BrowserMultiColumnAdapter(
                requireContext(),
                activityViewModel,
                onTap = ::onTap,
                onLongPress = { rowId ->
                    activityViewModel.handleRowLongPress(rowId.toRowSelection())
                },
                onRightClick = { rowId ->
                    activityViewModel.handleRightClick(rowId.toRowSelection())
                },
            )
        cardsListView.adapter = cardsAdapter
        cardsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        val layoutManager = LinearLayoutManager(requireContext())
        cardsListView.layoutManager = layoutManager
        cardsListView.addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))

        browserColumnHeadings = view.findViewById(R.id.browser_column_headings)
        toggleRowSelections =
            view.findViewById<ImageButton>(R.id.toggle_row_selections).apply {
                setOnClickListener { activityViewModel.toggleSelectAllOrNone() }
            }

        progressIndicator = view.findViewById(R.id.browser_progress)

        deckChip =
            view.findViewById<Chip>(R.id.chip_decks)?.apply {
                setOnClickListener { viewModel.openDeckSelectionDialog() }
            }
        searchBar =
            view.findViewById<SearchBar>(R.id.search_bar)?.apply {
                setNavigationOnClickListener {
                    requireNavigationDrawerActivity().onNavigationPressed()
                }
            }
        searchView = view.findViewById<SearchView>(R.id.search_view)

        setupFlows()

        setupFragmentResultListeners()

        setupMenu()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    // TODO: extract conditionally inflating the menus
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    Timber.d("CardBrowserFragment::onMenuItemSelected")
                    prepareForUndoableOperation()
                    when (menuItem.itemId) {
                        android.R.id.home -> {
                            activityViewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
                            return true
                        }
                        R.id.action_sort_by_size -> {
                            changeDisplayOrder()
                            return true
                        }
                        R.id.action_show_marked -> {
                            activityViewModel.searchForMarkedNotes()
                            return true
                        }
                        R.id.action_show_suspended -> {
                            activityViewModel.searchForSuspendedCards()
                            return true
                        }
                        R.id.action_search_by_tag -> {
                            showFilterByTagsDialog()
                            return true
                        }
                        R.id.action_delete_card -> {
                            deleteSelectedNotes()
                            return true
                        }
                        R.id.action_mark_card -> {
                            toggleMark()
                            return true
                        }
                        R.id.action_suspend_card -> {
                            toggleSuspendCards()
                            return true
                        }
                        R.id.action_toggle_bury -> {
                            toggleBury()
                            return true
                        }
                        R.id.action_change_deck -> {
                            showChangeDeckDialog()
                            return true
                        }
                        R.id.action_select_all -> {
                            activityViewModel.selectAll()
                            return true
                        }
                        R.id.action_reset_cards_progress -> {
                            Timber.i("CardBrowserFragment:: Reset progress button pressed")
                            onResetProgress()
                            return true
                        }
                        R.id.action_reschedule_cards -> {
                            Timber.i("CardBrowserFragment:: Reschedule button pressed")
                            rescheduleSelectedCards()
                            return true
                        }
                        R.id.action_reposition_cards -> {
                            repositionSelectedCards()
                            return true
                        }
                        R.id.action_edit_tags -> {
                            showEditTagsDialog()
                        }
                        R.id.action_open_options -> {
                            showOptionsDialog()
                        }
                        R.id.action_export_selected -> {
                            exportSelected()
                        }
                        R.id.action_create_filtered_deck -> {
                            showCreateFilteredDeckDialog()
                        }
                        R.id.action_find_replace -> {
                            showFindAndReplaceDialog()
                        }
                    }
                    return false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::cardsListView.isInitialized) {
            cardsListView.adapter = null
        }
    }

    @Suppress("UNUSED_PARAMETER", "unused")
    private fun setupFlows() {
        fun onIsTruncatedChanged(isTruncated: Boolean) = cardsAdapter.notifyDataSetChanged()

        fun cardsUpdatedChanged(unit: Unit) = cardsAdapter.notifyDataSetChanged()

        fun onColumnsChanged(columnCollection: BrowserColumnCollection) {
            Timber.d("columns changed")
            cardsAdapter.notifyDataSetChanged()
        }

        fun onMultiSelectModeChanged(modeChange: ChangeMultiSelectMode) {
            val inMultiSelect = modeChange.resultedInMultiSelect
            toggleRowSelections.isVisible = inMultiSelect

            // update adapter to remove check boxes
            cardsAdapter.notifyDataSetChanged()
            if (modeChange is SingleSelectCause.DeselectRow) {
                cardsAdapter.notifyDataSetChanged()
                autoScrollTo(modeChange.selection)
            } else if (modeChange is MultiSelectCause.RowSelected) {
                cardsAdapter.notifyDataSetChanged()
                autoScrollTo(modeChange.selection)
            } else if (modeChange is SingleSelectCause && !modeChange.previouslySelectedRowIds.isNullOrEmpty()) {
                // if any visible rows are selected, anchor on the first row

                // obtain the offset of the row before we call notifyDataSetChanged
                val rowPositionAndOffset =
                    try {
                        val visibleRowIds = layoutManager.visibleItemPositions.map { activityViewModel.getRowAtPosition(it) }
                        val firstVisibleRowId = visibleRowIds.firstOrNull { modeChange.previouslySelectedRowIds!!.contains(it) }
                        firstVisibleRowId?.let { firstVisibleRowId.toRowSelection() }
                    } catch (e: Exception) {
                        Timber.w(e)
                        null
                    }
                cardsAdapter.notifyDataSetChanged()
                rowPositionAndOffset?.let { autoScrollTo(it) }
            }
        }

        fun searchStateChanged(searchState: SearchState) {
            cardsAdapter.notifyDataSetChanged()
            progressIndicator.isVisible = searchState == Initializing || searchState == Searching
        }

        fun onSelectedRowsChanged(rows: Set<Any>) = cardsAdapter.notifyDataSetChanged()

        fun onCardsMarkedEvent(unit: Unit) {
            cardsAdapter.notifyDataSetChanged()
        }

        fun onColumnNamesChanged(columnCollection: List<ColumnHeading>) {
            Timber.d("column names changed")
            browserColumnHeadings.removeAllViews()

            val layoutInflater = LayoutInflater.from(browserColumnHeadings.context)
            for (column in columnCollection) {
                Timber.d("setting up column %s", column)
                val columnView = layoutInflater.inflate(R.layout.browser_column_heading, browserColumnHeadings, false) as TextView

                columnView.text = column.label

                // Attach click listener to open the selection dialog
                columnView.setOnClickListener {
                    Timber.d("Clicked column: ${column.label}")
                    showColumnSelectionDialog(column)
                }

                // Attach long press listener to open the manage column dialog
                columnView.setOnLongClickListener {
                    Timber.d("Long-pressed column: ${column.label}")
                    val dialog = BrowserColumnSelectionFragment.createInstance(activityViewModel.cardsOrNotes)
                    dialog.show(parentFragmentManager, null)
                    true
                }
                browserColumnHeadings.addView(columnView)
            }
        }

        fun onToggleSelectionStateUpdated(selectionState: ToggleSelectionState) {
            toggleRowSelections.setImageResource(
                when (selectionState) {
                    SELECT_ALL -> R.drawable.ic_select_all_white
                    SELECT_NONE -> R.drawable.ic_deselect_white
                },
            )
            toggleRowSelections.contentDescription =
                getString(
                    when (selectionState) {
                        SELECT_ALL -> R.string.card_browser_select_all
                        SELECT_NONE -> R.string.card_browser_select_none
                    },
                )
        }

        fun onSearchForDecks(decks: List<SelectableDeck>) {
            val dialog =
                DeckSelectionDialog.newInstance(
                    title = getString(R.string.search_deck),
                    summaryMessage = null,
                    keepRestoreDefaultButton = false,
                    decks = decks,
                )
            showDialogFragmentImpl(childFragmentManager, dialog)
        }

        fun onDeckChanged(deck: SelectableDeck?) {
            deckChip?.text = deck?.getFullDisplayName(requireContext())
        }

        activityViewModel.flowOfIsTruncated.launchCollectionInLifecycleScope(::onIsTruncatedChanged)
        activityViewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        activityViewModel.flowOfActiveColumns.launchCollectionInLifecycleScope(::onColumnsChanged)
        activityViewModel.flowOfCardsUpdated.launchCollectionInLifecycleScope(::cardsUpdatedChanged)
        activityViewModel.flowOfMultiSelectModeChanged.launchCollectionInLifecycleScope(::onMultiSelectModeChanged)
        activityViewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        activityViewModel.flowOfColumnHeadings.launchCollectionInLifecycleScope(::onColumnNamesChanged)
        activityViewModel.flowOfCardStateChanged.launchCollectionInLifecycleScope(::onCardsMarkedEvent)
        activityViewModel.flowOfToggleSelectionState.launchCollectionInLifecycleScope(::onToggleSelectionStateUpdated)
        viewModel.flowOfSearchForDecks.launchCollectionInLifecycleScope(::onSearchForDecks)
        activityViewModel.flowOfDeckSelection.launchCollectionInLifecycleScope(::onDeckChanged)
        activityViewModel.flowOfScrollRequest.launchCollectionInLifecycleScope(::autoScrollTo)
    }

    private fun setupFragmentResultListeners() {
        ankiActivity.setFragmentResultListener(REQUEST_REPOSITION_NEW_CARDS) { _, bundle ->
            repositionCardsNoValidation(
                position = bundle.getInt(RepositionCardFragment.ARG_POSITION),
                step = bundle.getInt(RepositionCardFragment.ARG_STEP),
                shuffle = bundle.getBoolean(RepositionCardFragment.ARG_RANDOM),
                shift = bundle.getBoolean(RepositionCardFragment.ARG_SHIFT),
            )
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        // TODO: dismiss undoSnackbar if it would undo a new action
        if (handler === this || handler === activityViewModel) {
            return
        }

        if (changes.browserSidebar ||
            changes.browserTable ||
            changes.noteText ||
            changes.card
        ) {
            cardsAdapter.notifyDataSetChanged()
        }
    }

    fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        // This method is called even when the user is typing in the search text field.
        // So we must ensure that all shortcuts uses a modifier.
        // A shortcut without modifier would be triggered while the user types, which is not what we want.
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+A - Show edit tags dialog")
                    showEditTagsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+A - Select All")
                    activityViewModel.selectAll()
                    return true
                }
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+E: Export selected cards")
                    exportSelected()
                    return true
                }
            }
            KeyEvent.KEYCODE_D -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+D: Change Deck")
                    showChangeDeckDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_K -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+K: Toggle Mark")
                    toggleMark()
                    return true
                }
            }
            KeyEvent.KEYCODE_R -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+R - Reschedule")
                    rescheduleSelectedCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("CTRL+ALT+F - Find and replace")
                    showFindAndReplaceDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_N -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+N: Reset card progress")
                    onResetProgress()
                    return true
                }
            }
            KeyEvent.KEYCODE_T -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+T: Toggle cards/notes")
                    showOptionsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+T: Show filter by tags dialog")
                    showFilterByTagsDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_S -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+S: Reposition selected cards")
                    repositionSelectedCards()
                    return true
                    // Ctrl+Alt+S / Ctrl+S in the activity take priority
                } else if (!event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Alt+S: Show suspended cards")
                    activityViewModel.searchForSuspendedCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_J -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+J: Toggle bury cards")
                    toggleBury()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+J: Toggle suspended cards")
                    toggleSuspendCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_O -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+O: Show order dialog")
                    changeDisplayOrder()
                    return true
                }
            }
            KeyEvent.KEYCODE_M -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+M: Search marked notes")
                    activityViewModel.searchForMarkedNotes()
                    return true
                }
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                Timber.i("ESC: Select none")
                activityViewModel.selectNone()
                return true
            }
        }
        return false
    }

    private fun showColumnSelectionDialog(selectedColumn: ColumnHeading) {
        Timber.d("Fetching available columns for: ${selectedColumn.label}")

        // Prevent multiple dialogs from opening
        if (parentFragmentManager.findFragmentByTag(ColumnSelectionDialogFragment.TAG) != null) {
            Timber.d("ColumnSelectionDialog is already shown, ignoring duplicate click.")
            return
        }

        lifecycleScope.launch {
            val (_, availableColumns) = activityViewModel.previewColumnHeadings(activityViewModel.cardsOrNotes)

            if (availableColumns.isEmpty()) {
                Timber.w("No available columns to replace ${selectedColumn.label}")
                showSnackbar(R.string.no_columns_available)
                return@launch
            }

            val dialog = ColumnSelectionDialogFragment.newInstance(selectedColumn)
            dialog.show(parentFragmentManager, ColumnSelectionDialogFragment.TAG)
        }
    }

    // TODO: Move this to ViewModel and test
    @VisibleForTesting
    fun onTap(id: CardOrNoteId) =
        launchCatchingTask {
            activityViewModel.focusedRow = id
            if (activityViewModel.isInMultiSelectMode) {
                val wasSelected = activityViewModel.selectedRows.contains(id)
                activityViewModel.toggleRowSelection(id.toRowSelection())
                // Load NoteEditor on trailing side if card is selected
                if (wasSelected) {
                    activityViewModel.currentCardId = id.toCardId(activityViewModel.cardsOrNotes)
                    requireCardBrowserActivity().loadNoteEditorFragmentIfFragmented()
                }
            } else {
                val cardId = activityViewModel.queryDataForCardEdit(id)
                requireCardBrowserActivity().openNoteEditorForCard(cardId)
            }
        }

    // TODO: This dialog should survive activity recreation
    fun showChangeDeckDialog() =
        launchCatchingTask {
            if (!activityViewModel.hasSelectedAnyRows()) {
                Timber.i("Not showing Change Deck - No Cards")
                return@launchCatchingTask
            }
            val selectableDecks =
                activityViewModel
                    .getAvailableDecks()
            val dialog = getChangeDeckDialog(selectableDecks)
            showDialogFragment(dialog)
        }

    /** All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked  */
    @NeedsTest("Test that the mark get toggled as expected for a list of selected cards")
    @VisibleForTesting
    fun toggleMark() =
        launchCatchingTask {
            withProgress { activityViewModel.toggleMark() }
        }

    fun toggleSuspendCards() = launchCatchingTask { withProgress { activityViewModel.toggleSuspendCards().join() } }

    /** @see CardBrowserViewModel.toggleBury */
    fun toggleBury() =
        launchCatchingTask {
            val result = withProgress { activityViewModel.toggleBury() } ?: return@launchCatchingTask
            // show a snackbar as there's currently no colored background for buried cards
            val message =
                when (result.wasBuried) {
                    true -> TR.studyingCardsBuried(result.count)
                    false -> resources.getQuantityString(R.plurals.unbury_cards_feedback, result.count, result.count)
                }
            showUndoSnackbar(message)
        }

    fun rescheduleSelectedCards() {
        if (!activityViewModel.hasSelectedAnyRows()) {
            Timber.i("Attempted reschedule - no cards selected")
            return
        }

        launchCatchingTask {
            val allCardIds = activityViewModel.queryAllSelectedCardIds()
            Timber.i(
                "Reschedule: mode=%s, selected rows=%d, cards=%d",
                activityViewModel.cardsOrNotes,
                activityViewModel.selectedRows.size,
                allCardIds.size,
            )
            showDialogFragment(SetDueDateDialog.newInstance(allCardIds))
        }
    }

    /** @see repositionCardsNoValidation */
    fun repositionSelectedCards(): Boolean {
        Timber.i("CardBrowser:: Reposition button pressed")
        launchCatchingTask {
            when (val repositionCardsResult = activityViewModel.prepareToRepositionCards()) {
                is ContainsNonNewCardsError -> {
                    // Only new cards may be repositioned (If any non-new found show error dialog and return false)
                    showDialogFragment(
                        SimpleMessageDialog.newInstance(
                            title = getString(R.string.vague_error),
                            message = getString(R.string.reposition_card_not_new_error),
                            reload = false,
                        ),
                    )
                    return@launchCatchingTask
                }
                is RepositionData -> {
                    val top = repositionCardsResult.queueTop
                    val bottom = repositionCardsResult.queueBottom
                    if (top == null || bottom == null) {
                        showSnackbar(R.string.something_wrong)
                        return@launchCatchingTask
                    }
                    val repositionDialog =
                        RepositionCardFragment.newInstance(
                            queueTop = top,
                            queueBottom = bottom,
                            random = repositionCardsResult.random,
                            shift = repositionCardsResult.shift,
                        )
                    showDialogFragment(repositionDialog)
                }
            }
        }
        return true
    }

    fun deleteSelectedNotes() =
        launchCatchingTask {
            withProgress(R.string.deleting_selected_notes) {
                activityViewModel.deleteSelectedNotes()
            }.ifNotZero { noteCount ->
                val deletedMessage = resources.getQuantityString(R.plurals.card_browser_cards_deleted, noteCount, noteCount)
                showUndoSnackbar(deletedMessage)
            }
        }

    fun onResetProgress() {
        launchCatchingTask {
            val allCardIds = activityViewModel.queryAllSelectedCardIds()
            Timber.i(
                "Reset Progress: mode=%s, selected rows=%d, cards=%d",
                activityViewModel.cardsOrNotes,
                activityViewModel.selectedRows.size,
                allCardIds.size,
            )
        }
        showDialogFragment(ForgetCardsDialog())
    }

    fun exportSelected() {
        val (type, selectedIds) = activityViewModel.querySelectionExportData() ?: return
        ExportDialogFragment.newInstance(type, selectedIds).show(parentFragmentManager, "exportDialog")
    }

    fun showOptionsDialog() {
        val dialog = BrowserOptionsDialog.newInstance(activityViewModel.cardsOrNotes, activityViewModel.isTruncated)
        dialog.show(parentFragmentManager, "browserOptionsDialog")
    }

    fun showCreateFilteredDeckDialog() {
        val dialog = CreateDeckDialog(ankiActivity, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
        dialog.onNewDeckCreated = {
            startActivity(
                FilteredDeckOptions.getIntent(
                    context = requireContext(),
                    deckId = null,
                    searchTerms = activityViewModel.searchTerms,
                ),
            )
        }
        launchCatchingTask {
            withProgress {
                dialog.showFilteredDeckDialog()
            }
        }
    }

    fun changeDisplayOrder() {
        showDialogFragment(
            // TODO: move this into the ViewModel
            CardBrowserOrderDialog.newInstance { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                activityViewModel.changeCardOrder(SortType.fromCardBrowserLabelIndex(which))
            },
        )
    }

    fun updateFlagForSelectedRows(flag: Flag) =
        launchCatchingTask {
            // list of cards with updated flags
            val updatedCardIds = withProgress { activityViewModel.updateSelectedCardsFlag(flag) }

            ankiActivity.onCardsUpdated(updatedCardIds)
        }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun filterByTag(vararg tags: String) {
        tagsDialogListenerAction = TagsDialogListenerAction.FILTER
        onSelectedTags(tags.toList(), emptyList(), CardStateFilter.ALL_CARDS)
        filterByTags(tags.toList(), CardStateFilter.ALL_CARDS)
    }

    fun showEditTagsDialog() {
        if (!activityViewModel.hasSelectedAnyRows()) {
            Timber.d("showEditTagsDialog: called with empty selection")
        }
        tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS
        lifecycleScope.launch {
            val noteIds = activityViewModel.queryAllSelectedNoteIds()
            val dialog =
                tagsDialogFactory.newTagsDialog().withArguments(
                    requireContext(),
                    type = TagsDialog.DialogType.EDIT_TAGS,
                    noteIds = noteIds,
                )
            showDialogFragment(dialog)
        }
    }

    fun showFilterByTagsDialog() {
        launchCatchingTask {
            tagsDialogListenerAction = TagsDialogListenerAction.FILTER
            val dialog =
                tagsDialogFactory.newTagsDialog().withArguments(
                    context = requireContext(),
                    type = TagsDialog.DialogType.FILTER_BY_TAG,
                    noteIds = emptyList(),
                )
            showDialogFragment(dialog)
        }
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        when (tagsDialogListenerAction) {
            TagsDialogListenerAction.FILTER -> filterByTags(selectedTags, stateFilter)
            TagsDialogListenerAction.EDIT_TAGS ->
                launchCatchingTask {
                    editSelectedCardsTags(selectedTags, indeterminateTags)
                }
            else -> {}
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun showFindAndReplaceDialog() {
        FindAndReplaceDialogFragment().show(parentFragmentManager, FindAndReplaceDialogFragment.TAG)
    }

    @KotlinCleanup("DeckSelectionListener is almost certainly a bug - deck!!")
    @VisibleForTesting
    internal fun getChangeDeckDialog(selectableDecks: List<SelectableDeck>?): DeckSelectionDialog {
        val dialog =
            DeckSelectionDialog.newInstance(
                getString(R.string.move_all_to_deck),
                null,
                false,
                selectableDecks!!,
            )
        // Add change deck argument so the dialog can be dismissed
        // after activity recreation, since the selected cards will be gone with it
        dialog.requireArguments().putBoolean(CHANGE_DECK_KEY, true)
        dialog.deckSelectionListener =
            DeckSelectionListener { deck: SelectableDeck? ->
                require(deck is SelectableDeck.Deck) { "Expected non-null deck" }
                moveSelectedCardsToDeck(deck.deckId)
            }
        return dialog
    }

    /**
     * Change Deck
     * @param did Id of the deck
     */
    @VisibleForTesting
    internal fun moveSelectedCardsToDeck(did: DeckId): Job =
        launchCatchingTask {
            val changed = withProgress { activityViewModel.moveSelectedCardsToDeck(did).await() }
            showUndoSnackbar(TR.browsingCardsUpdated(changed.count))
        }

    @VisibleForTesting
    internal fun repositionCardsNoValidation(
        position: Int,
        step: Int,
        shuffle: Boolean,
        shift: Boolean,
    ) = launchCatchingTask {
        val count =
            withProgress {
                activityViewModel.repositionSelectedRows(
                    position = position,
                    step = step,
                    shuffle = shuffle,
                    shift = shift,
                )
            }
        showSnackbar(
            TR.browsingChangedNewPosition(count),
            Snackbar.LENGTH_SHORT,
        )
    }

    private fun showUndoSnackbar(message: CharSequence) {
        showSnackbar(message) {
            setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
            undoSnackbar = this
        }
    }

    private fun calculateTopOffset(cardPosition: Int): Int {
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val view = cardsListView.getChildAt(cardPosition - firstVisiblePosition)
        return view?.top ?: 0
    }

    private fun autoScrollTo(rowSelection: RowSelection) {
        val newPosition = activityViewModel.getPositionOfId(rowSelection.rowId) ?: return
        layoutManager.scrollToPositionWithOffset(newPosition, rowSelection.topOffset)
    }

    private fun CardOrNoteId.toRowSelection() =
        RowSelection(rowId = this, topOffset = calculateTopOffset(activityViewModel.getPositionOfId(this)!!))

    private fun requireCardBrowserActivity(): CardBrowser = requireActivity() as CardBrowser

    // TODO: Move this to an extension method once we have context parameters
    private fun <T> Flow<T>.launchCollectionInLifecycleScope(block: suspend (T) -> Unit) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                this@launchCollectionInLifecycleScope.collect {
                    if (isRobolectric) {
                        HandlerUtils.postOnNewHandler { runBlocking { block(it) } }
                    } else {
                        block(it)
                    }
                }
            }
        }
    }

    /**
     * Updates the tags of selected/checked notes and saves them to the disk
     * @param selectedTags list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * For more info on [selectedTags] and [indeterminateTags] see [com.ichi2.anki.dialogs.tags.TagsDialogListener.onSelectedTags]
     */
    private suspend fun editSelectedCardsTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
    ) = withProgress {
        val selectedNoteIds = activityViewModel.queryAllSelectedNoteIds().distinct()
        undoableOp {
            val selectedNotes =
                selectedNoteIds
                    .map { noteId -> getNote(noteId) }
                    .onEach { note ->
                        val previousTags: List<String> = note.tags
                        val updatedTags = getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                        note.setTagsFromStr(this@undoableOp, tags.join(updatedTags))
                    }
            updateNotes(selectedNotes)
        }
    }

    private fun filterByTags(
        selectedTags: List<String>,
        cardState: CardStateFilter,
    ) = launchCatchingTask {
        activityViewModel.filterByTags(selectedTags, cardState)
    }

    fun prepareForUndoableOperation() {
        // dismiss undo-snackbar if shown to avoid race condition
        // (when another operation will be performed on the model, it will undo the latest operation)
        val snackbar = undoSnackbar ?: return
        if (snackbar.isShown) {
            snackbar.dismiss()
        }
    }

    val shortcuts get() =
        ShortcutGroup(
            listOf(
                shortcut("Ctrl+Shift+A", R.string.edit_tags_dialog),
                shortcut("Ctrl+A", R.string.card_browser_select_all),
                shortcut("Ctrl+Shift+E", Translations::exportingExport),
                shortcut("Ctrl+E", R.string.menu_add_note),
                shortcut("E", R.string.cardeditor_title_edit_card),
                shortcut("Ctrl+D", R.string.card_browser_change_deck),
                shortcut("Ctrl+K", Translations::browsingToggleMark),
                shortcut("Ctrl+Alt+R", Translations::browsingReschedule),
                shortcut("DEL", R.string.delete_card_title),
                shortcut("Ctrl+Alt+N", R.string.reset_card_dialog_title),
                shortcut("Ctrl+Alt+T", R.string.toggle_cards_notes),
                shortcut("Ctrl+T", R.string.card_browser_search_by_tag),
                shortcut("Ctrl+Shift+S", Translations::actionsReposition),
                shortcut("Ctrl+Alt+S", R.string.card_browser_list_my_searches),
                shortcut("Ctrl+S", R.string.card_browser_list_my_searches_save),
                shortcut("Alt+S", R.string.card_browser_show_suspended),
                shortcut("Ctrl+Shift+G", Translations::actionsGradeNow),
                shortcut("Ctrl+Shift+J", Translations::browsingToggleBury),
                shortcut("Ctrl+J", Translations::browsingToggleSuspend),
                shortcut("Ctrl+Shift+I", Translations::actionsCardInfo),
                shortcut("Ctrl+O", R.string.show_order_dialog),
                shortcut("Ctrl+M", R.string.card_browser_show_marked),
                shortcut("Esc", R.string.card_browser_select_none),
                shortcut("Ctrl+1", R.string.gesture_flag_red),
                shortcut("Ctrl+2", R.string.gesture_flag_orange),
                shortcut("Ctrl+3", R.string.gesture_flag_green),
                shortcut("Ctrl+4", R.string.gesture_flag_blue),
                shortcut("Ctrl+5", R.string.gesture_flag_pink),
                shortcut("Ctrl+6", R.string.gesture_flag_turquoise),
                shortcut("Ctrl+7", R.string.gesture_flag_purple),
            ),
            R.string.card_browser_context_menu,
        )

    private enum class TagsDialogListenerAction {
        FILTER,
        EDIT_TAGS,
    }

    companion object {
        /**
         * Argument key to add on change deck dialog,
         * so it can be dismissed on activity recreation,
         * since the cards are unselected when this happens
         */
        private const val CHANGE_DECK_KEY = "CHANGE_DECK"
    }
}
