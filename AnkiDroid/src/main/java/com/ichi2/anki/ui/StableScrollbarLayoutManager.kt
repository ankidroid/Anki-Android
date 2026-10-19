// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class StableScrollbarLayoutManager(
    context: Context,
) : LinearLayoutManager(context) {
    private var measuredRange: MeasuredRange? = null
    private var rowHeightsBeforeChange: Map<Int, Int>? = null

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        val shape = ListShape(state.itemCount, width, height)
        val rowsChangedHeight = rowHeightsBeforeChange?.let(::anyRowChangedHeight) ?: false
        rowHeightsBeforeChange = null
        if (measuredRange?.shape != shape || rowsChangedHeight) {
            measuredRange = MeasuredRange(super.computeVerticalScrollRange(state), shape)
        }
    }

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?,
    ) {
        super.onAdapterChanged(oldAdapter, newAdapter)
        measuredRange = null
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        super.onItemsChanged(recyclerView)
        rememberRowHeights()
    }

    override fun onItemsUpdated(
        recyclerView: RecyclerView,
        positionStart: Int,
        itemCount: Int,
    ) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount)
        rememberRowHeights()
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = stableRange(state) ?: super.computeVerticalScrollRange(state)

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        val estimatedOffset = super.computeVerticalScrollOffset(state)
        val range = stableRange(state) ?: return estimatedOffset
        val extent = computeVerticalScrollExtent(state)
        val estimatedScrollable = super.computeVerticalScrollRange(state) - extent
        val scrollable = range - extent
        return when {
            estimatedOffset <= 0 -> 0
            estimatedOffset >= estimatedScrollable - 1 -> scrollable
            else -> (estimatedOffset.toFloat() / estimatedScrollable * scrollable).roundToInt().coerceIn(1, scrollable - 2)
        }
    }

    private fun stableRange(state: RecyclerView.State): Int? {
        val range = measuredRange?.range ?: return null
        val extent = computeVerticalScrollExtent(state)
        val estimatedRange = super.computeVerticalScrollRange(state)
        return range.takeIf { it - extent >= MIN_SCROLLABLE_PIXELS && estimatedRange - extent >= MIN_SCROLLABLE_PIXELS }
    }

    private fun anyRowChangedHeight(heightsBefore: Map<Int, Int>) =
        rowHeights().any { (position, height) -> heightsBefore[position].let { it != null && it != height } }

    private fun rememberRowHeights() {
        if (rowHeightsBeforeChange == null) rowHeightsBeforeChange = rowHeights()
    }

    private fun rowHeights(): Map<Int, Int> =
        (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .associate { getPosition(it) to getDecoratedMeasuredHeight(it) }

    private data class ListShape(
        val itemCount: Int,
        val width: Int,
        val height: Int,
    )

    private data class MeasuredRange(
        val range: Int,
        val shape: ListShape,
    )
}

private const val MIN_SCROLLABLE_PIXELS = 3
