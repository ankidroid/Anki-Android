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
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
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
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.attachFastScroller
import com.ichi2.anki.utils.ext.visibleItemPositions
import com.ichi2.utils.HandlerUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

// Minor BUG: 'don't keep activities' and huge selection
// At some point, starting between 35k and 60k selections, the scroll position is lost on recreation
// This occurred on a Pixel 9 Pro, Android 15
class CardBrowserFragment :
    Fragment(R.layout.cardbrowser),
    ChangeManager.Subscriber {
    val viewModel: CardBrowserViewModel by activityViewModels()

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
}
