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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import anki.collection.OpChanges
import com.google.android.material.progressindicator.LinearProgressIndicator
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
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.newInstance
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SortType
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.attachFastScroller
import com.ichi2.anki.utils.ext.getCurrentDialogFragment
import com.ichi2.anki.utils.ext.ifNotZero
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.visibleItemPositions
import com.ichi2.anki.withProgress
import com.ichi2.utils.HandlerUtils
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
    Fragment(R.layout.cardbrowser),
    AnkiActivityProvider,
    ChangeManager.Subscriber {
    val viewModel: CardBrowserViewModel by activityViewModels()

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
                viewModel,
                onTap = ::onTap,
                onLongPress = { rowId ->
                    viewModel.handleRowLongPress(rowId.toRowSelection())
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
                setOnClickListener { viewModel.toggleSelectAllOrNone() }
            }

        progressIndicator = view.findViewById(R.id.browser_progress)

        setupFlows()

        setupFragmentResultListeners()
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
                        val visibleRowIds = layoutManager.visibleItemPositions.map { viewModel.getRowAtPosition(it) }
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
                    val dialog = BrowserColumnSelectionFragment.createInstance(viewModel.cardsOrNotes)
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

        viewModel.flowOfIsTruncated.launchCollectionInLifecycleScope(::onIsTruncatedChanged)
        viewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        viewModel.flowOfActiveColumns.launchCollectionInLifecycleScope(::onColumnsChanged)
        viewModel.flowOfCardsUpdated.launchCollectionInLifecycleScope(::cardsUpdatedChanged)
        viewModel.flowOfMultiSelectModeChanged.launchCollectionInLifecycleScope(::onMultiSelectModeChanged)
        viewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        viewModel.flowOfColumnHeadings.launchCollectionInLifecycleScope(::onColumnNamesChanged)
        viewModel.flowOfCardStateChanged.launchCollectionInLifecycleScope(::onCardsMarkedEvent)
        viewModel.flowOfToggleSelectionState.launchCollectionInLifecycleScope(::onToggleSelectionStateUpdated)
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
        if (handler === this || handler === viewModel) {
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

    private fun showColumnSelectionDialog(selectedColumn: ColumnHeading) {
        Timber.d("Fetching available columns for: ${selectedColumn.label}")

        // Prevent multiple dialogs from opening
        if (parentFragmentManager.findFragmentByTag(ColumnSelectionDialogFragment.TAG) != null) {
            Timber.d("ColumnSelectionDialog is already shown, ignoring duplicate click.")
            return
        }

        lifecycleScope.launch {
            val (_, availableColumns) = viewModel.previewColumnHeadings(viewModel.cardsOrNotes)

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
            viewModel.focusedRow = id
            if (viewModel.isInMultiSelectMode) {
                val wasSelected = viewModel.selectedRows.contains(id)
                viewModel.toggleRowSelection(id.toRowSelection())
                // Load NoteEditor on trailing side if card is selected
                if (wasSelected) {
                    viewModel.currentCardId = id.toCardId(viewModel.cardsOrNotes)
                    requireCardBrowserActivity().loadNoteEditorFragmentIfFragmented()
                }
            } else {
                val cardId = viewModel.queryDataForCardEdit(id)
                requireCardBrowserActivity().openNoteEditorForCard(cardId)
            }
        }

    fun showChangeDeckDialog() =
        launchCatchingTask {
            if (!viewModel.hasSelectedAnyRows()) {
                Timber.i("Not showing Change Deck - No Cards")
                return@launchCatchingTask
            }
            val selectableDecks =
                viewModel
                    .getAvailableDecks()
                    .map { d -> SelectableDeck(d) }
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
            withProgress { viewModel.toggleMark() }
        }

    fun toggleSuspendCards() = launchCatchingTask { withProgress { viewModel.toggleSuspendCards().join() } }

    /** @see CardBrowserViewModel.toggleBury */
    fun toggleBury() =
        launchCatchingTask {
            val result = withProgress { viewModel.toggleBury() } ?: return@launchCatchingTask
            // show a snackbar as there's currently no colored background for buried cards
            val message =
                when (result.wasBuried) {
                    true -> TR.studyingCardsBuried(result.count)
                    false -> resources.getQuantityString(R.plurals.unbury_cards_feedback, result.count, result.count)
                }
            ankiActivity.showUndoSnackbar(message)
        }

    fun rescheduleSelectedCards() {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.i("Attempted reschedule - no cards selected")
            return
        }
        if (ankiActivity.warnUserIfInNotesOnlyMode()) return

        launchCatchingTask {
            val allCardIds = viewModel.queryAllSelectedCardIds()
            showDialogFragment(SetDueDateDialog.newInstance(allCardIds))
        }
    }

    /** @see repositionCardsNoValidation */
    fun repositionSelectedCards(): Boolean {
        Timber.i("CardBrowser:: Reposition button pressed")
        if (ankiActivity.warnUserIfInNotesOnlyMode()) return false
        launchCatchingTask {
            when (val repositionCardsResult = viewModel.prepareToRepositionCards()) {
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
                viewModel.deleteSelectedNotes()
            }.ifNotZero { noteCount ->
                val deletedMessage = resources.getQuantityString(R.plurals.card_browser_cards_deleted, noteCount, noteCount)
                ankiActivity.showUndoSnackbar(deletedMessage)
            }
        }

    fun onResetProgress() {
        if (ankiActivity.warnUserIfInNotesOnlyMode()) return
        showDialogFragment(ForgetCardsDialog())
    }

    fun exportSelected() {
        val (type, selectedIds) = viewModel.querySelectionExportData() ?: return
        ExportDialogFragment.newInstance(type, selectedIds).show(parentFragmentManager, "exportDialog")
    }

    fun showOptionsDialog() {
        val dialog = BrowserOptionsDialog.newInstance(viewModel.cardsOrNotes, viewModel.isTruncated)
        dialog.show(parentFragmentManager, "browserOptionsDialog")
    }

    fun showCreateFilteredDeckDialog() {
        val dialog = CreateDeckDialog(ankiActivity, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
        dialog.onNewDeckCreated = {
            startActivity(
                FilteredDeckOptions.getIntent(
                    context = requireContext(),
                    deckId = null,
                    searchTerms = viewModel.searchTerms,
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
                viewModel.changeCardOrder(SortType.fromCardBrowserLabelIndex(which))
            },
        )
    }

    /**
     * @see CardBrowserViewModel.searchForSuspendedCards
     */
    fun searchForSuspendedCards() {
        launchCatchingTask { viewModel.searchForSuspendedCards() }
    }

    /**
     * @see CardBrowserViewModel.searchForMarkedNotes
     */
    fun searchForMarkedNotes() {
        launchCatchingTask { viewModel.searchForMarkedNotes() }
    }

    fun updateFlagForSelectedRows(flag: Flag) =
        launchCatchingTask {
            // list of cards with updated flags
            val updatedCardIds = withProgress { viewModel.updateSelectedCardsFlag(flag) }

            ankiActivity.onCardsUpdated(updatedCardIds)
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun showFindAndReplaceDialog() {
        FindAndReplaceDialogFragment().show(parentFragmentManager, FindAndReplaceDialogFragment.TAG)
    }

    @KotlinCleanup("DeckSelectionListener is almost certainly a bug - deck!!")
    @VisibleForTesting
    internal fun getChangeDeckDialog(selectableDecks: List<SelectableDeck>?): DeckSelectionDialog {
        val dialog =
            newInstance(
                getString(R.string.move_all_to_deck),
                null,
                false,
                selectableDecks!!,
            )
        // Add change deck argument so the dialog can be dismissed
        // after activity recreation, since the selected cards will be gone with it
        dialog.requireArguments().putBoolean(CHANGE_DECK_KEY, true)
        dialog.deckSelectionListener = DeckSelectionListener { deck: SelectableDeck? -> moveSelectedCardsToDeck(deck!!.deckId) }
        return dialog
    }

    /**
     * Change Deck
     * @param did Id of the deck
     */
    @VisibleForTesting
    internal fun moveSelectedCardsToDeck(did: DeckId): Job =
        launchCatchingTask {
            val changed = withProgress { viewModel.moveSelectedCardsToDeck(did).await() }
            (requireActivity() as CardBrowser).showUndoSnackbar(TR.browsingCardsUpdated(changed.count))
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
                viewModel.repositionSelectedRows(
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

    private fun calculateTopOffset(cardPosition: Int): Int {
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val view = cardsListView.getChildAt(cardPosition - firstVisiblePosition)
        return view?.top ?: 0
    }

    private fun autoScrollTo(rowSelection: RowSelection) {
        val newPosition = viewModel.getPositionOfId(rowSelection.rowId) ?: return
        layoutManager.scrollToPositionWithOffset(newPosition, rowSelection.topOffset)
    }

    private fun CardOrNoteId.toRowSelection() =
        RowSelection(rowId = this, topOffset = calculateTopOffset(viewModel.getPositionOfId(this)!!))

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

    companion object {
        /**
         * Argument key to add on change deck dialog,
         * so it can be dismissed on activity recreation,
         * since the cards are unselected when this happens
         */
        private const val CHANGE_DECK_KEY = "CHANGE_DECK"
    }
}

fun CardBrowser.showChangeDeckDialog() = cardBrowserFragment.showChangeDeckDialog()

fun CardBrowser.toggleMark() = cardBrowserFragment.toggleMark()

fun CardBrowser.toggleSuspendCards() = cardBrowserFragment.toggleSuspendCards()

fun CardBrowser.toggleBury() = cardBrowserFragment.toggleBury()

fun CardBrowser.rescheduleSelectedCards() = cardBrowserFragment.rescheduleSelectedCards()

fun CardBrowser.repositionSelectedCards() = cardBrowserFragment.repositionSelectedCards()

fun CardBrowser.deleteSelectedNotes() = cardBrowserFragment.deleteSelectedNotes()

fun CardBrowser.onResetProgress() = cardBrowserFragment.onResetProgress()

fun CardBrowser.exportSelected() = cardBrowserFragment.exportSelected()

fun CardBrowser.showOptionsDialog() = cardBrowserFragment.showOptionsDialog()

fun CardBrowser.showCreateFilteredDeckDialog() = cardBrowserFragment.showCreateFilteredDeckDialog()

fun CardBrowser.changeDisplayOrder() = cardBrowserFragment.changeDisplayOrder()

fun CardBrowser.searchForMarkedNotes() = cardBrowserFragment.searchForMarkedNotes()

fun CardBrowser.searchForSuspendedCards() = cardBrowserFragment.searchForSuspendedCards()

fun CardBrowser.updateFlagForSelectedRows(flag: Flag) = cardBrowserFragment.updateFlagForSelectedRows(flag)

fun CardBrowser.showFindAndReplaceDialog() = cardBrowserFragment.showFindAndReplaceDialog()
