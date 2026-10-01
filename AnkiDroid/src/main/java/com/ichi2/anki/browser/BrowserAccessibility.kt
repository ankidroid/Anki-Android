// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_MULTIPLE
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_NONE
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat.SORT_DIRECTION_ASCENDING
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat.SORT_DIRECTION_DESCENDING
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat.SORT_DIRECTION_NONE
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.model.SortType

/** The headings and scrolling rows belong to a single accessibility table. */
internal fun View.setBrowserTableAccessibility(viewModel: CardBrowserViewModel) {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    setAccessibilityInfo {
        val selectionMode = if (viewModel.isInMultiSelectMode) SELECTION_MODE_MULTIPLE else SELECTION_MODE_NONE
        setCollectionInfo(
            CollectionInfoCompat.obtain(
                viewModel.rowCount + 1,
                viewModel.flowOfActiveColumns.value.count,
                false,
                selectionMode,
            ),
        )
    }
}

internal fun View.setBrowserHeadingAccessibility(
    columnIndex: Int,
    columnKey: String,
    sortType: () -> SortType,
) {
    setAccessibilityInfo {
        val ordering = sortType() as? SortType.CollectionOrdering
        val direction =
            when {
                ordering == null || ordering.key.value != columnKey -> SORT_DIRECTION_NONE
                ordering.reverse -> SORT_DIRECTION_DESCENDING
                else -> SORT_DIRECTION_ASCENDING
            }
        isHeading = true
        setCellInfo(row = 0, column = columnIndex) {
            setSortDirection(direction)
        }
    }
}

internal fun View.setBrowserCellAccessibility(
    columnIndex: Int,
    position: () -> Int,
) {
    setAccessibilityInfo {
        val row = position()
        if (row == RecyclerView.NO_POSITION) return@setAccessibilityInfo
        // Row zero contains the column headings.
        setCellInfo(row = row + 1, column = columnIndex)
    }
}

private fun AccessibilityNodeInfoCompat.setCellInfo(
    row: Int,
    column: Int,
    configure: CollectionItemInfoCompat.Builder.() -> Unit = {},
) = setCollectionItemInfo(
    CollectionItemInfoCompat
        .Builder()
        .setRowIndex(row)
        .setRowSpan(1)
        .setColumnIndex(column)
        .setColumnSpan(1)
        .apply(configure)
        .build(),
)

private fun View.setAccessibilityInfo(initialize: AccessibilityNodeInfoCompat.() -> Unit) {
    ViewCompat.setAccessibilityDelegate(
        this,
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.initialize()
            }
        },
    )
}

/** Preserve RecyclerView's scrolling actions while using the surrounding table's coordinates. */
internal class BrowserLayoutManager(
    context: Context,
    private val columnCount: () -> Int,
) : LinearLayoutManager(context) {
    override fun onInitializeAccessibilityNodeInfo(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        info: AccessibilityNodeInfoCompat,
    ) {
        super.onInitializeAccessibilityNodeInfo(recycler, state, info)
        info.setCollectionInfo(null)
    }

    override fun onInitializeAccessibilityNodeInfoForItem(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        host: View,
        info: AccessibilityNodeInfoCompat,
    ) {
        super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info)
        val item = info.collectionItemInfo ?: return
        info.setCellInfo(row = item.rowIndex + 1, column = 0) {
            setColumnSpan(columnCount())
            setSelected(item.isSelected)
        }
    }
}
