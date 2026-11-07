// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil
import kotlin.math.roundToInt

class StableScrollbarLayoutManager(
    context: Context,
) : LinearLayoutManager(context) {
    private var measuredRange: MeasuredRange? = null
    private var rowHeightsBeforeChange: Map<Int, Int>? = null
    private var scrolledFromTop: Int? = null
    private var pendingAnchor: Anchor? = null

    /** Remembers the first visible row, so a scroll made by the layout itself is still counted. */
    override fun onLayoutChildren(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ) {
        if (!state.isPreLayout && pendingAnchor == null) rememberAnchor(findFirstVisibleItemPosition())
        super.onLayoutChildren(recycler, state)
    }

    /** Measures the range again when the content changed, then corrects it if the layout changed or moved the list. */
    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        val shape = ListShape(state.itemCount, width)
        val rowsChangedHeight = rowHeightsBeforeChange?.let(::anyRowChangedHeight) ?: false
        rowHeightsBeforeChange = null
        val contentChanged = measuredRange?.shape != shape || rowsChangedHeight
        val rangeFitsScreen = measuredRange?.let { it.range - computeVerticalScrollExtent(state) < MIN_SCROLLABLE_PIXELS } ?: false
        val scrolledBefore = scrolledFromTop
        updateScrolledFromTop(contentChanged)
        if (contentChanged || rangeFitsScreen) {
            measuredRange = MeasuredRange(super.computeVerticalScrollRange(state), shape)
        }
        if (contentChanged || rangeFitsScreen || scrolledFromTop != scrolledBefore) {
            calibrateAtEnd(state)
            lowerRangeToRowsSoFar(state)
        }
    }

    /** Forgets everything measured for the old adapter. */
    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?,
    ) {
        super.onAdapterChanged(oldAdapter, newAdapter)
        measuredRange = null
        scrolledFromTop = null
        pendingAnchor = null
    }

    /** Keeps counting pixels from the top across a jump to a row that is laid out. */
    override fun scrollToPosition(position: Int) {
        rememberAnchor(position)
        super.scrollToPosition(position)
    }

    /** Keeps counting pixels from the top across a jump to a row that is laid out. */
    override fun scrollToPositionWithOffset(
        position: Int,
        offset: Int,
    ) {
        rememberAnchor(position)
        super.scrollToPositionWithOffset(position, offset)
    }

    /** Counts the pixels scrolled and corrects the range, without moving the thumb back on the way down. */
    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        val thumbTopBefore = if (dy > 0) thumbTop(state) else null
        val scrolled = super.scrollVerticallyBy(dy, recycler, state)
        scrolledFromTop = scrolledFromTop?.plus(scrolled)
        rememberScrolledFromTop()
        val minRange = thumbTopBefore?.let { rangeMovingThumbForward(it, state) }
        calibrateAtEnd(state, minRange)
        if (dy > 0) lowerRangeToRowsSoFar(state, minRange)
        return scrolled
    }

    /** After a full refresh, keeps the range but checks it again at the end. */
    override fun onItemsChanged(recyclerView: RecyclerView) {
        super.onItemsChanged(recyclerView)
        rememberRowHeights()
        measuredRange = measuredRange?.copy(recheck = true, endRange = null, heldUntilTop = false)
        scrolledFromTop = null
        pendingAnchor = null
    }

    /** Checks the range again if an updated row is off screen, and stops counting pixels if one is above the screen. */
    override fun onItemsUpdated(
        recyclerView: RecyclerView,
        positionStart: Int,
        itemCount: Int,
    ) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount)
        rememberRowHeights()
        if ((positionStart until positionStart + itemCount).any { findViewByPosition(it) == null }) {
            measuredRange = measuredRange?.copy(recheck = true, endRange = null, heldUntilTop = false)
        }
        if (positionStart < findFirstVisibleItemPosition()) {
            scrolledFromTop = null
            pendingAnchor = null
        }
    }

    /** Returns the stable range instead of LinearLayoutManager's estimate, which changes with the visible rows. */
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = stableRange(state) ?: super.computeVerticalScrollRange(state)

    /** Places the thumb by the rows above and below the screen, so it reaches an end only when the list does. */
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

    /** The thumb top as a fraction of the range. */
    private fun thumbTop(state: RecyclerView.State): Float? {
        val range = computeVerticalScrollRange(state).takeIf { it > 0 } ?: return null
        return computeVerticalScrollOffset(state).toFloat() / range
    }

    /** The smallest range that still moves the thumb forward after a scroll down. */
    private fun rangeMovingThumbForward(
        thumbTopBefore: Float,
        state: RecyclerView.State,
    ): Int? {
        val progress = scrollProgress(state.itemCount) ?: return null
        val extent = computeVerticalScrollExtent(state).takeIf { it > 0 } ?: return null
        val thumbTopAfter = thumbTop(state) ?: return null
        val top = thumbTopBefore + ((thumbTopAfter - thumbTopBefore) / 2).coerceIn(0f, 1f / extent)
        return if (top >= progress) Int.MAX_VALUE else ceil(extent / (1 - top / progress)).toInt()
    }

    /** The measured range, or null when there is too little to scroll and the estimate is used. */
    private fun stableRange(state: RecyclerView.State): Int? {
        val range = measuredRange?.range ?: return null
        val extent = computeVerticalScrollExtent(state)
        val estimatedRange = super.computeVerticalScrollRange(state)
        return range.takeIf { it - extent >= MIN_SCROLLABLE_PIXELS && estimatedRange - extent >= MIN_SCROLLABLE_PIXELS }
    }

    /** How far down the rows the screen is, from 0 to 1, counting rows cut by the edges in part. */
    private fun scrollProgress(itemCount: Int): Float? {
        val first = findViewByPosition(findFirstVisibleItemPosition()) ?: return null
        val last = findViewByPosition(findLastVisibleItemPosition()) ?: return null
        val rowsAbove = getPosition(first) + fractionOutside(first, paddingTop - getDecoratedTop(first))
        val rowsBelow = itemCount - 1 - getPosition(last) + fractionOutside(last, getDecoratedBottom(last) - (height - paddingBottom))
        if (rowsAbove + rowsBelow <= 0f) return 0f
        return rowsAbove / (rowsAbove + rowsBelow)
    }

    /** The part of [row] outside the screen, from 0 to 1. */
    private fun fractionOutside(
        row: View,
        pixelsOutside: Int,
    ) = (pixelsOutside.toFloat() / getDecoratedMeasuredHeight(row).coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Saves how far [position] is from the top so the next layout keeps counting, or stops counting. */
    private fun rememberAnchor(position: Int) {
        val row = findViewByPosition(position)
        pendingAnchor = scrolledFromTop?.let { scrolled -> row?.let { Anchor(position, scrolled + getDecoratedTop(it) - paddingTop) } }
        if (pendingAnchor == null) scrolledFromTop = null
    }

    /** Works out the pixels scrolled from the top after a layout, using the saved anchor if there is one. */
    private fun updateScrolledFromTop(contentChanged: Boolean) {
        val anchor = pendingAnchor
        pendingAnchor = null
        scrolledFromTop =
            when {
                contentChanged -> null
                anchor != null -> findViewByPosition(anchor.position)?.let { anchor.top - (getDecoratedTop(it) - paddingTop) }
                else -> scrolledFromTop
            }
        rememberScrolledFromTop()
    }

    /** Reads the pixels scrolled exactly while the first row is laid out, and releases a held range at the top. */
    private fun rememberScrolledFromTop() {
        findViewByPosition(0)?.let { scrolledFromTop = paddingTop - getDecoratedTop(it) }
        if (scrolledFromTop == 0) measuredRange = measuredRange?.copy(heldUntilTop = false)
    }

    /** At the end, sets the range to what was scrolled plus the screen, or holds it if the thumb would go back. */
    private fun calibrateAtEnd(
        state: RecyclerView.State,
        minRange: Int? = null,
    ) {
        val scrolled = scrolledFromTop ?: return
        val range = measuredRange ?: return
        if (range.heldUntilTop || (range.calibrated && !range.recheck)) return
        val lastRow = findViewByPosition(state.itemCount - 1) ?: return
        if (getDecoratedBottom(lastRow) > height - paddingBottom) return
        val exact = scrolled + computeVerticalScrollExtent(state)
        val held = minRange?.takeIf { exact < range.range && exact < it }?.coerceAtMost(range.range)
        measuredRange =
            range.copy(range = held ?: exact, calibrated = held == null, recheck = false, endRange = exact, heldUntilTop = held != null)
    }

    /** Lowers a range that is not exact yet towards the end range or the rows seen so far, never raising it. */
    private fun lowerRangeToRowsSoFar(
        state: RecyclerView.State,
        minRange: Int? = null,
    ) {
        val range = measuredRange ?: return
        if (range.calibrated || range.heldUntilTop) return
        val target = range.endRange ?: rowsSoFarEstimate(state) ?: return
        val lowered = maxOf(target, minRange ?: 0)
        if (lowered < range.range) measuredRange = range.copy(range = lowered, calibrated = lowered == range.endRange)
    }

    /** Estimates the range from the rows down to the screen bottom, at least down to the last visible row. */
    private fun rowsSoFarEstimate(state: RecyclerView.State): Int? {
        val scrolled = scrolledFromTop ?: return null
        val lastRow = findViewByPosition(findLastVisibleItemPosition()) ?: return null
        val visibleBottom = minOf(getDecoratedBottom(lastRow), height - paddingBottom)
        val rowsSoFar = getPosition(lastRow) + 1 - fractionOutside(lastRow, getDecoratedBottom(lastRow) - visibleBottom)
        val estimate = ((scrolled + visibleBottom - paddingTop) / rowsSoFar * state.itemCount).roundToInt()
        return maxOf(estimate, scrolled + getDecoratedBottom(lastRow) - paddingTop)
    }

    /** Whether a row that is still laid out changed height. */
    private fun anyRowChangedHeight(heightsBefore: Map<Int, Int>) =
        rowHeights().any { (position, height) -> heightsBefore[position].let { it != null && it != height } }

    /** Saves the row heights once, before the change is laid out. */
    private fun rememberRowHeights() {
        if (rowHeightsBeforeChange == null) rowHeightsBeforeChange = rowHeights()
    }

    /** The height of each laid out row, by position. */
    private fun rowHeights(): Map<Int, Int> =
        (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .associate { getPosition(it) to getDecoratedMeasuredHeight(it) }

    private data class ListShape(
        val itemCount: Int,
        val width: Int,
    )

    private data class MeasuredRange(
        val range: Int,
        val shape: ListShape,
        val calibrated: Boolean = false,
        val recheck: Boolean = false,
        val endRange: Int? = null,
        val heldUntilTop: Boolean = false,
    )

    private data class Anchor(
        val position: Int,
        val top: Int,
    )
}

private const val MIN_SCROLLABLE_PIXELS = 3
