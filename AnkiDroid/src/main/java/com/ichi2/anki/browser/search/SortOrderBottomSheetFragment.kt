// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.content.Context
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import anki.search.BrowserColumns.Sorting
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.ColumnType
import com.ichi2.anki.browser.getLabel
import com.ichi2.anki.browser.humanReadableExplanation
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType

private typealias Reverse = Boolean?

/**
 * A [BottomSheetDialogFragment] allowing selection of the sort order of the Card Browser
 */
class SortOrderBottomSheetFragment {
    /**
     * An item displayed in the sort order list
     *
     * - header: [SectionHeader]
     * - interactive row: [ColumnUiModel]
     */
    sealed interface SortListItem

    /** A non-interactive section header placed between groups of [ColumnUiModel]s. */
    data class SectionHeader(
        @StringRes val titleRes: Int,
    ) : SortListItem

    /**
     * The data to display a column (or 'no ordering') for selection as the sort column
     */
    sealed class ColumnUiModel : SortListItem {
        data object NoOrdering : ColumnUiModel()

        /**
         * @see anki.search.BrowserColumns.Column
         */
        data class AnkiColumn(
            val key: BrowserColumnKey,
            val label: String,
            val tooltipValue: String?,
            val canBeSorted: Boolean,
            val isShownInUI: Boolean,
            val type: ColumnType,
        ) : ColumnUiModel() {
            override fun toString() = this.label

            /**
             * The reason why a key is unavailable for sorting
             *
             * This assumes that the column is already confirmed to be unavailable
             * (e.g. FSRS columns return a reason, but are usable in cards mode)
             */
            @get:StringRes
            val sortUnavailableReason: Int?
                get() {
                    if (canBeSorted) return null

                    return when (this.key.value) {
                        // Questin/Answer require rendering the template, which is too expensive to
                        // do for all cards in the collection. 'Sort Field' can be used instead
                        CardBrowserColumn.QUESTION.ankiColumnKey,
                        CardBrowserColumn.ANSWER.ankiColumnKey,
                        -> R.string.card_browser_order_subtitle_use_sort_field

                        // FSRS data is stored in JSON in the card table, it's not yet feasible to
                        // sort on this data
                        CardBrowserColumn.FSRS_DIFFICULTY.ankiColumnKey,
                        CardBrowserColumn.FSRS_STABILITY.ankiColumnKey,
                        CardBrowserColumn.FSRS_RETRIEVABILITY.ankiColumnKey,
                        -> R.string.card_browser_order_subtitle_cards_mode_only

                        else -> null
                    }
                }
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
                NoOrdering -> context.getString(R.string.card_browser_order_no_sorting_title)
                is AnkiColumn -> this.label
            }

        @CheckResult
        fun getSubtitle(
            context: Context,
            sort: SortType.CollectionOrdering?,
        ): String? {
            val subtitleRes: Int? =
                when (this) {
                    // No Ordering's subtitle is 'Faster'
                    is NoOrdering -> R.string.card_browser_order_no_sorting_subtitle
                    // the selected column has an explanation of the sort: "Low to high"
                    is AnkiColumn if sort != null && sort.key == key ->
                        type.humanReadableExplanation(descending = sort.reverse)
                    // if a column is unavailable, the subtitle is the reason that it's unavailable
                    is AnkiColumn if !canBeSorted -> sortUnavailableReason
                    else -> null
                }
            return subtitleRes?.let { context.getString(it) }
        }

        @CheckResult
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
            fun buildList(viewModel: CardBrowserViewModel): List<SortListItem> {
                // start with our default order. Anything unknown is at the end of the list
                val orderIndex = CardBrowserColumn.entries.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedColumns = viewModel.allColumns.sortedWith(compareBy { orderIndex[it.key] ?: Int.MAX_VALUE })

                // active columns are visible in the UI. These come first, in UI order
                val activeColumnMap = viewModel.activeColumns.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedWithActive = sortedColumns.sortedWith(compareBy { activeColumnMap[it.key] ?: Int.MAX_VALUE })

                // columns not in [CardBrowserColumn] fall back to [ColumnType.UNSPECIFIED]
                val typeByKey = CardBrowserColumn.entries.associate { it.ankiColumnKey to it.type(viewModel.cardsOrNotes) }

                val ankiColumnsList =
                    sortedWithActive.map { column ->
                        // some Anki columns can't be sorted on.
                        // Display them, but mark them as unavailable
                        val canBeSorted =
                            when (viewModel.cardsOrNotes) {
                                CardsOrNotes.CARDS -> column.sortingCards != Sorting.SORTING_NONE
                                CardsOrNotes.NOTES -> column.sortingNotes != Sorting.SORTING_NONE
                            }

                        // TODO: design a tooltip using `column.getTooltip`
                        val label = column.getLabel(viewModel.cardsOrNotes)

                        val isActive = activeColumnMap.containsKey(column.key)

                        AnkiColumn(
                            key = BrowserColumnKey.from(column),
                            label = label,
                            tooltipValue = null,
                            isShownInUI = isActive,
                            canBeSorted = canBeSorted,
                            type = typeByKey[column.key] ?: ColumnType.UNSPECIFIED,
                        )
                    }

                // three groups: shown in the browser, sortable but hidden, unsortable
                val selected = ankiColumnsList.filter { it.isShownInUI && it.canBeSorted }
                val available = ankiColumnsList.filter { !it.isShownInUI && it.canBeSorted }
                val unavailable = ankiColumnsList.filter { !it.canBeSorted }

                return buildList {
                    add(NoOrdering)
                    if (selected.isNotEmpty()) {
                        add(SectionHeader(R.string.user_active_columns))
                        addAll(selected)
                    }
                    if (available.isNotEmpty()) {
                        add(SectionHeader(R.string.user_potential_columns))
                        addAll(available)
                    }
                    if (unavailable.isNotEmpty()) {
                        add(SectionHeader(R.string.card_browser_order_section_unavailable))
                        addAll(unavailable)
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "SortOrderBottomSheetFragment"
    }
}
