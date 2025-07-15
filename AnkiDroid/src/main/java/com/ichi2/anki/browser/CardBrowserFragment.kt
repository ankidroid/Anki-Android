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
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.attachFastScroller
import com.ichi2.utils.HandlerUtils
import com.ichi2.utils.dp
import com.ichi2.utils.updatePaddingRelative
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class CardBrowserFragment :
    Fragment(R.layout.cardbrowser),
    ChangeManager.Subscriber {
    val viewModel: CardBrowserViewModel by activityViewModels()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var cardsAdapter: BrowserMultiColumnAdapter

    @VisibleForTesting
    lateinit var cardsListView: RecyclerView

    @VisibleForTesting
    lateinit var browserColumnHeadings: ViewGroup

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
                onLongPress = viewModel::handleRowLongPress,
            )
        cardsListView.adapter = cardsAdapter
        cardsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        val layoutManager = LinearLayoutManager(requireContext())
        cardsListView.layoutManager = layoutManager
        cardsListView.addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))

        this.browserColumnHeadings = view.findViewById(R.id.browser_column_headings)

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

        fun isInMultiSelectModeChanged(inMultiSelect: Boolean) {
            if (inMultiSelect) {
                // A checkbox is added on the rows, match padding to keep the headings aligned
                // Due to the ripple on long press, we set padding
                browserColumnHeadings.updatePaddingRelative(start = 48.dp)
            } else {
                browserColumnHeadings.updatePaddingRelative(start = 0.dp)
            }

            // update adapter to remove check boxes
            cardsAdapter.notifyDataSetChanged()
            autoScrollTo(viewModel.lastSelectedPosition, viewModel.oldCardTopOffset)
        }

        fun searchStateChanged(searchState: SearchState) {
            cardsAdapter.notifyDataSetChanged()
            progressIndicator.isVisible = searchState == Initializing || searchState == Searching
        }

        fun onSelectedRowsChanged(rows: Set<Any>) = cardsAdapter.notifyDataSetChanged()

        fun onSelectedRowUpdated(id: CardOrNoteId?) {
            if (!viewModel.isInMultiSelectMode || viewModel.lastSelectedId == null) {
                viewModel.oldCardTopOffset =
                    calculateTopOffset(viewModel.lastSelectedPosition)
            }
        }

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

        viewModel.flowOfIsTruncated.launchCollectionInLifecycleScope(::onIsTruncatedChanged)
        viewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        viewModel.flowOfActiveColumns.launchCollectionInLifecycleScope(::onColumnsChanged)
        viewModel.flowOfCardsUpdated.launchCollectionInLifecycleScope(::cardsUpdatedChanged)
        viewModel.flowOfIsInMultiSelectMode.launchCollectionInLifecycleScope(::isInMultiSelectModeChanged)
        viewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        viewModel.rowLongPressFocusFlow.launchCollectionInLifecycleScope(::onSelectedRowUpdated)
        viewModel.flowOfColumnHeadings.launchCollectionInLifecycleScope(::onColumnNamesChanged)
        viewModel.flowOfCardStateChanged.launchCollectionInLifecycleScope(::onCardsMarkedEvent)
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
                viewModel.toggleRowSelection(id)
                viewModel.saveScrollingState(id)
                viewModel.oldCardTopOffset = calculateTopOffset(viewModel.lastSelectedPosition)
                // Load NoteEditor on trailing side if card is selected
                if (wasSelected) {
                    viewModel.currentCardId = id.toCardId(viewModel.cardsOrNotes)
                    requireCardBrowserActivity().loadNoteEditorFragmentIfFragmented()
                }
            } else {
                viewModel.lastSelectedPosition = (cardsListView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                viewModel.oldCardTopOffset = calculateTopOffset(viewModel.lastSelectedPosition)
                val cardId = viewModel.queryDataForCardEdit(id)
                requireCardBrowserActivity().openNoteEditorForCard(cardId)
            }
        }

    private fun calculateTopOffset(cardPosition: Int): Int {
        val layoutManager = cardsListView.layoutManager as LinearLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val view = cardsListView.getChildAt(cardPosition - firstVisiblePosition)
        return view?.top ?: 0
    }

    private fun autoScrollTo(
        newPosition: Int,
        offset: Int,
    ) {
        (cardsListView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(newPosition, offset)
    }

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
