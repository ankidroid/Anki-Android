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
import androidx.annotation.CheckResult
import anki.search.BrowserColumns.Sorting
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.getLabel
import com.ichi2.anki.model.CardsOrNotes

/**
 * A [BottomSheetDialogFragment] allowing selection of the sort order of the Card Browser
 */
class SortOrderBottomSheetFragment {
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

        @CheckResult
        fun getLabel(context: Context): String =
            when (this) {
                NoOrdering -> context.getString(R.string.card_browser_order_no_sorting)
                is AnkiColumn -> this.label
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

    companion object {
        const val TAG = "SortOrderBottomSheetFragment"
    }
}
