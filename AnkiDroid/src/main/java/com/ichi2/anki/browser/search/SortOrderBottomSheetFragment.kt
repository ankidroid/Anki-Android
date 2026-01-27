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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import anki.search.BrowserColumns.Sorting
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.getLabel
import com.ichi2.anki.databinding.FragmentBottomSheetListBinding
import com.ichi2.anki.databinding.ViewBrowserSortOrderBottomSheetItemBinding
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType
import com.ichi2.anki.utils.ext.behavior
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

private typealias Reverse = Boolean?

/**
 * A [BottomSheetDialogFragment] allowing selection of the sort order of the Card Browser
 *
 * @param viewModelProviderFactory A factory producing a [CardBrowserViewModel]
 */
class SortOrderBottomSheetFragment(
    private val viewModelProviderFactory: ViewModelProvider.Factory = ViewModelProvider.NewInstanceFactory(),
) : BottomSheetDialogFragment(R.layout.fragment_bottom_sheet_list) {
    @VisibleForTesting
    val viewModel: CardBrowserViewModel by activityViewModels { viewModelProviderFactory }

    @VisibleForTesting
    val binding by viewBinding(FragmentBottomSheetListBinding::bind)

    @VisibleForTesting
    lateinit var currentSortType: SortType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.currentSortType =
            requireNotNull(
                BundleCompat.getParcelable(requireArguments(), ARG_CURRENT_SORT_TYPE, SortType::class.java),
            ) { ARG_CURRENT_SORT_TYPE }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.title) {
            isVisible = true
            text = getString(R.string.card_browser_change_display_order_title)
        }

        with(this.behavior) {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false
        }

        val adapter =
            SortOrderHolderAdapter(
                columns = ColumnUiModel.buildList(viewModel),
                currentlySelectedSort = currentSortType,
            )

        adapter.onItemClickedListener = { sortType ->
            Timber.i("selected sort type '%s'", sortType)
            viewModel.setSortType(sortType)
            dismiss()
        }

        binding.list.adapter = adapter
    }

    /**
     * Display the dialog, adding the fragment to the given [FragmentManager].
     *
     * @param manager The [FragmentManager] this fragment will be added to.
     *
     * @see BottomSheetDialogFragment.show
     */
    fun show(manager: FragmentManager) = this.show(manager, TAG)

    /**
     * The data to display a column (or 'no ordering') for selection as the sort column
     */
    sealed class ColumnUiModel {
        data object NoOrdering : ColumnUiModel()

        data class AnkiColumn(
            val key: BrowserColumnKey,
            val label: String,
            val tooltipValue: String?,
            val canBeSorted: Boolean,
            val isShownInUI: Boolean,
        ) : ColumnUiModel() {
            override fun toString() = this.label
        }

        /**
         * Whether the item is usable in the current state
         *
         * 'Question' is unavailable in Cards mode
         */
        val available: Boolean
            get() =
                when (this) {
                    is NoOrdering -> true
                    is AnkiColumn -> this.canBeSorted
                }

        val tooltip: String? get() =
            when (this) {
                NoOrdering -> null
                is AnkiColumn -> this.tooltipValue
            }

        fun getLabel(context: Context): String =
            when (this) {
                NoOrdering -> context.getString(R.string.card_browser_order_no_sorting)
                is AnkiColumn -> this.label
            }

        fun toSortType(reverse: Reverse): SortType =
            when (this) {
                is NoOrdering -> SortType.NoOrdering
                is AnkiColumn ->
                    SortType.CollectionOrdering(
                        key = this.key,
                        reverse = requireNotNull(reverse),
                    )
            }

        companion object {
            @CheckResult
            fun buildList(viewModel: CardBrowserViewModel): List<ColumnUiModel> {
                // obtain the columns
                val allColumns = viewModel.flowOfAllColumns.value.values

                // start with our default order. Anything unknown is at the end of the list
                val orderIndex = CardBrowserColumn.entries.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedColumns = allColumns.sortedWith(compareBy { orderIndex[it.key] ?: Int.MAX_VALUE })

                // active columns are visible in the UI. These come first, in UI order
                val activeColumnMap = viewModel.activeColumns.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedWithActive = sortedColumns.sortedWith(compareBy { activeColumnMap[it.key] ?: Int.MAX_VALUE })

                val ankiColumnsList =
                    sortedWithActive
                        // build the UI Model
                        .map { column ->
                            // some Anki columns can't be sorted on.
                            // Display them, but mark them as unavailable
                            val canBeSorted =
                                when (viewModel.cardsOrNotes) {
                                    CardsOrNotes.CARDS -> column.sortingCards != Sorting.SORTING_NONE
                                    CardsOrNotes.NOTES -> column.sortingNotes != Sorting.SORTING_NONE
                                }

                            // TODO: tooltip
                            val label = column.getLabel(viewModel.cardsOrNotes)
                            // val tooltip = it.getTooltip(viewModel.cardsOrNotes)

                            val isActive = activeColumnMap.containsKey(column.key)

                            AnkiColumn(
                                key = BrowserColumnKey.from(column),
                                label = label,
                                tooltipValue = null,
                                isShownInUI = isActive,
                                canBeSorted = canBeSorted,
                            )
                        }.sortedByDescending {
                            // columns which are in the UI are first, even if unusable
                            // the rest get displayed at the end
                            it.isShownInUI || it.canBeSorted
                        }

                return listOf(NoOrdering) + ankiColumnsList
            }
        }
    }

    /**
     * @see ViewBrowserSortOrderBottomSheetItemBinding
     */
    @VisibleForTesting
    inner class SortOrderHolderAdapter(
        @VisibleForTesting
        val columns: List<ColumnUiModel>,
        private val currentlySelectedSort: SortType,
    ) : RecyclerView.Adapter<SortOrderHolderAdapter.Holder>() {
        var onItemClickedListener: ((SortType) -> Unit) = { }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): Holder {
            val binding =
                ViewBrowserSortOrderBottomSheetItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            return Holder(binding)
        }

        private var originalTextColor: Int? = null

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val column = this.columns[position]
            val context = holder.binding.root.context

            holder.binding.root.isEnabled = column.available
            holder.binding.root.alpha = if (column.available) 1.0f else 0.4f

            // TODO: use tooltip
            holder.binding.text.text = column.getLabel(context)
            holder.binding.text.useOriginalTextColor()

            setupImageViews(column, holder)

            // setup divider
            holder.binding.divider.isVisible = false

            // show a divider after the last column shown in the UI
            if (column is ColumnUiModel.AnkiColumn) {
                val nextColumn = this.columns.getOrNull(position + 1) as? ColumnUiModel.AnkiColumn
                if (column.isShownInUI && nextColumn?.isShownInUI == false) {
                    holder.binding.divider.isVisible = true
                }
            }

            if (!column.isCurrentSortOrder()) return

            holder.binding.text.markAsCurrentlySelected()

            // color the currently selected image view
            val selectedImageView =
                when (column) {
                    is ColumnUiModel.AnkiColumn -> {
                        requireNotNull(currentlySelectedSort as SortType.CollectionOrdering)
                        if (currentlySelectedSort.reverse) holder.binding.sortReverse else holder.binding.sortStandard
                    }
                    is ColumnUiModel.NoOrdering -> holder.binding.noSortOrder
                }
            selectedImageView.markAsCurrentlySelected()
        }

        private fun setupImageViews(
            column: ColumnUiModel,
            holder: Holder,
        ) {
            /**
             * Whether a button can be selected
             */
            fun canSelectButton(reverse: Reverse) =
                // the column is usable in the selected cards/notes mode
                column.available &&
                    // the button is not for the current sort order
                    when (reverse) {
                        null -> !column.isCurrentSortOrder()
                        else -> !column.buttonIsForCurrentSortOrder(reverse)
                    }

            fun ImageView.setup(
                buttonName: String,
                reversed: Reverse,
            ) {
                // TODO: show help on click if a button is not usable
                isEnabled = column.available
                clearColorFilter()
                setOnClickListener {
                    if (!canSelectButton(reverse = reversed)) {
                        Timber.i("ignored click on $buttonName for column %s", column)
                        return@setOnClickListener
                    }
                    Timber.i("$buttonName clicked for column %s", column)

                    onItemClickedListener(column.toSortType(reversed))
                }
            }

            holder.binding.sortReverse.setup("sortReversed", reversed = true)
            holder.binding.sortStandard.setup("sortStandard", reversed = false)
            holder.binding.noSortOrder.setup("noSortOrder", reversed = null)

            holder.binding.sortStandard.isVisible = column is ColumnUiModel.AnkiColumn
            holder.binding.sortReverse.isVisible = column is ColumnUiModel.AnkiColumn
            holder.binding.noSortOrder.isVisible = column is ColumnUiModel.NoOrdering
        }

        private fun ColumnUiModel.isCurrentSortOrder() =
            when (this) {
                is ColumnUiModel.NoOrdering -> currentlySelectedSort is SortType.NoOrdering
                is ColumnUiModel.AnkiColumn ->
                    currentlySelectedSort is SortType.CollectionOrdering &&
                        this.key == currentlySelectedSort.key
            }

        private fun ColumnUiModel.buttonIsForCurrentSortOrder(reverse: Boolean) =
            when (this) {
                is ColumnUiModel.NoOrdering -> currentlySelectedSort is SortType.NoOrdering
                is ColumnUiModel.AnkiColumn ->
                    currentlySelectedSort is SortType.CollectionOrdering &&
                        this.key == currentlySelectedSort.key &&
                        (currentlySelectedSort.reverse == reverse)
            }

        private fun TextView.useOriginalTextColor() {
            originalTextColor = originalTextColor ?: currentTextColor
            setTextColor(originalTextColor!!)
        }

        private fun TextView.markAsCurrentlySelected() {
            // TODO: do something better
            setTextColor(ContextCompat.getColor(context, R.color.material_blue_400))
        }

        private fun ImageView.markAsCurrentlySelected() {
            isEnabled = false
            // TODO: do something better
            setColorFilter(ContextCompat.getColor(context, R.color.material_blue_400))
        }

        override fun getItemCount() = columns.size

        inner class Holder(
            val binding: ViewBrowserSortOrderBottomSheetItemBinding,
        ) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val TAG = "SortOrderBottomSheetFragment"

        const val ARG_CURRENT_SORT_TYPE = "currentSortType"

        suspend fun createInstance(cardsOrNotes: CardsOrNotes) =
            SortOrderBottomSheetFragment().apply {
                val sortData = SortType.build(cardsOrNotes)

                Timber.i("creating SortOrderBottomSheetFragment with %s", sortData)

                arguments = bundleOf(ARG_CURRENT_SORT_TYPE to sortData)
            }
    }
}
