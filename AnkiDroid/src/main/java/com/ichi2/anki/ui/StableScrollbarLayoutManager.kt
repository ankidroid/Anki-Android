// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class StableScrollbarLayoutManager(
    context: Context,
) : LinearLayoutManager(context) {
    private var measuredRange: MeasuredRange? = null
    private var rowHeightsBeforeChange: Map<Int, Int>? = null
    private var scrolledFromTop: Int? = null
    private var pendingAnchor: Anchor? = null

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        val shape = ListShape(state.itemCount, width, height)
        val rowsChangedHeight = rowHeightsBeforeChange?.let(::anyRowChangedHeight) ?: false
        rowHeightsBeforeChange = null
        val contentChanged = measuredRange?.shape != shape || rowsChangedHeight
        updateScrolledFromTop(contentChanged)
        if (contentChanged) {
            measuredRange = MeasuredRange(super.computeVerticalScrollRange(state), shape)
        }
        calibrateAtEnd(state)
        lowerRangeToRowsSoFar(state)
    }

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?,
    ) {
        super.onAdapterChanged(oldAdapter, newAdapter)
        measuredRange = null
        scrolledFromTop = null
        pendingAnchor = null
    }

    override fun scrollToPosition(position: Int) {
        rememberAnchor(position)
        super.scrollToPosition(position)
    }

    override fun scrollToPositionWithOffset(
        position: Int,
        offset: Int,
    ) {
        rememberAnchor(position)
        super.scrollToPositionWithOffset(position, offset)
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        val scrolled = super.scrollVerticallyBy(dy, recycler, state)
        scrolledFromTop = scrolledFromTop?.plus(scrolled)
        findViewByPosition(0)?.let { scrolledFromTop = paddingTop - getDecoratedTop(it) }
        calibrateAtEnd(state)
        lowerRangeToRowsSoFar(state)
        return scrolled
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        super.onItemsChanged(recyclerView)
        rememberRowHeights()
        measuredRange = measuredRange?.copy(calibrated = false)
        scrolledFromTop = null
    }

    override fun onItemsUpdated(
        recyclerView: RecyclerView,
        positionStart: Int,
        itemCount: Int,
    ) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount)
        rememberRowHeights()
        measuredRange = measuredRange?.copy(calibrated = false)
        if (positionStart < findFirstVisibleItemPosition()) scrolledFromTop = null
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = stableRange(state) ?: super.computeVerticalScrollRange(state)

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        val range = stableRange(state) ?: return super.computeVerticalScrollOffset(state)
        val progress = scrollProgress(state.itemCount) ?: return super.computeVerticalScrollOffset(state)
        val scrollable = range - computeVerticalScrollExtent(state)
        return when {
            progress <= 0f -> 0
            progress >= 1f -> scrollable
            else -> (progress * scrollable).roundToInt().coerceIn(1, scrollable - 2)
        }
    }

    private fun stableRange(state: RecyclerView.State): Int? {
        val range = measuredRange?.range ?: return null
        val extent = computeVerticalScrollExtent(state)
        val estimatedRange = super.computeVerticalScrollRange(state)
        return range.takeIf { it - extent >= MIN_SCROLLABLE_PIXELS && estimatedRange - extent >= MIN_SCROLLABLE_PIXELS }
    }

    private fun scrollProgress(itemCount: Int): Float? {
        val first = findViewByPosition(findFirstVisibleItemPosition()) ?: return null
        val last = findViewByPosition(findLastVisibleItemPosition()) ?: return null
        val rowsAbove = getPosition(first) + fractionOutside(first, paddingTop - getDecoratedTop(first))
        val rowsBelow = itemCount - 1 - getPosition(last) + fractionOutside(last, getDecoratedBottom(last) - (height - paddingBottom))
        if (rowsAbove + rowsBelow <= 0f) return 0f
        return rowsAbove / (rowsAbove + rowsBelow)
    }

    private fun fractionOutside(
        row: View,
        pixelsOutside: Int,
    ) = (pixelsOutside.toFloat() / getDecoratedMeasuredHeight(row).coerceAtLeast(1)).coerceIn(0f, 1f)

    private fun rememberAnchor(position: Int) {
        val row = findViewByPosition(position)
        pendingAnchor = scrolledFromTop?.let { scrolled -> row?.let { Anchor(position, scrolled + getDecoratedTop(it) - paddingTop) } }
        if (pendingAnchor == null) scrolledFromTop = null
    }

    private fun updateScrolledFromTop(contentChanged: Boolean) {
        val anchor = pendingAnchor
        pendingAnchor = null
        scrolledFromTop =
            when {
                contentChanged -> null
                anchor != null -> findViewByPosition(anchor.position)?.let { anchor.top - (getDecoratedTop(it) - paddingTop) }
                else -> scrolledFromTop
            }
        findViewByPosition(0)?.let { scrolledFromTop = paddingTop - getDecoratedTop(it) }
    }

    private fun calibrateAtEnd(state: RecyclerView.State) {
        val scrolled = scrolledFromTop ?: return
        val range = measuredRange ?: return
        if (range.calibrated) return
        val lastRow = findViewByPosition(state.itemCount - 1) ?: return
        if (getDecoratedBottom(lastRow) > height - paddingBottom) return
        measuredRange = range.copy(range = scrolled + computeVerticalScrollExtent(state), calibrated = true)
    }

    private fun lowerRangeToRowsSoFar(state: RecyclerView.State) {
        val scrolled = scrolledFromTop ?: return
        val range = measuredRange ?: return
        if (range.calibrated) return
        val lastRow = findViewByPosition(findLastVisibleItemPosition()) ?: return
        val heightSoFar = scrolled + getDecoratedBottom(lastRow) - paddingTop
        val estimate = (heightSoFar.toFloat() / (getPosition(lastRow) + 1) * state.itemCount).toInt()
        if (estimate < range.range) measuredRange = range.copy(range = estimate)
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
        val calibrated: Boolean = false,
    )

    private data class Anchor(
        val position: Int,
        val top: Int,
    )
}

private const val MIN_SCROLLABLE_PIXELS = 3
