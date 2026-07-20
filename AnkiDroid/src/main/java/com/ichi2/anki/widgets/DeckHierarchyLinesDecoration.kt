// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>
package com.ichi2.anki.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.appcompat.widget.ThemeUtils
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.utils.dp
import timber.log.Timber

class DeckHierarchyLinesDecoration(
    context: Context,
    private val expanderWidth: Float,
) : RecyclerView.ItemDecoration() {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            strokeWidth = LINE_STROKE_WIDTH_DP.dp.toPx(context).toFloat()

            color = ThemeUtils.getThemeAttrColor(context, com.google.android.material.R.attr.colorOnSurface)
            alpha = 30
        }

    private val nestedIndent = context.resources.getDimension(R.dimen.keyline_1)
    private val expanderCenterOffset = expanderWidth / 2f
    private val cornerRadius = 8.dp.toPx(context).toFloat()
    private val iconGap = 8.dp.toPx(context).toFloat()
    private val reusablePath = Path()
    private val reusableRect = RectF()

    /**
     * Cache used to store the bitmask of active vertical lines passing through each visible row.
     * The index maps to the child position in the RecyclerView, and the 64-bit Long stores the active depths.
     * It dynamically resizes if the RecyclerView holds more visible items.
     */
    private var siblingCache = LongArray(50)

    override fun onDrawOver(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val childCount = parent.childCount
        if (childCount == 0) return

        val currentList = (parent.adapter as? DeckAdapter)?.currentList ?: return
        if (currentList.isEmpty()) return

        if (siblingCache.size < childCount) {
            siblingCache = LongArray(childCount * 2)
        }
        // We use a 64-bit Long as a bitmask. Each bit index represents a depth level.
        // If bit 'd' is 1, it means there is a sibling further down the list at depth 'd'
        // so we must draw a vertical line
        var activeLines = 0L

        // Gather visible views and sort them by adapter position to ensure we process them top-to-bottom.
        // RecyclerView.getChildAt() order is not guaranteed to match adapter position order, especially during animations.
        val visibleViews = mutableListOf<Pair<Int, android.view.View>>()
        for (i in 0 until childCount) {
            val view = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(view)
            if (pos != RecyclerView.NO_POSITION) {
                visibleViews.add(pos to view)
            }
        }
        visibleViews.sortBy { it.first }

        if (visibleViews.isEmpty()) return

        val maxPos = visibleViews.last().first

        // Look ahead past the last visible item to find lines that continue below the screen
        if (maxPos != RecyclerView.NO_POSITION && maxPos + 1 < currentList.size) {
            val endPos = currentList.size
            var minDepthSeen = (currentList.getOrNull(maxPos)?.depth ?: 63) + 1
            for (i in maxPos + 1 until endPos) {
                val node = currentList.getOrNull(i) ?: continue
                val d = node.depth
                if (d >= 64) {
                    Timber.w("Deck depth exceeds 64 levels, skipping hierarchy lines")
                    continue
                }

                // We only care about finding the first sibling for each depth <= maxPos.depth.
                // Since deeper nodes are children of shallower nodes, a shallower node terminates
                // all lines for deeper depths. Thus, we only set a bit if we find a depth that is
                // strictly shallower than the shallowest depth we've seen so far in our look-ahead.
                if (d < minDepthSeen) {
                    minDepthSeen = d
                    activeLines = activeLines or (1L shl d)
                    if (d == 0) break // Root node resets everything below it
                }
            }
        }

        // Scan backwards over the sorted visible items to record the active lines after each node
        for (j in visibleViews.indices.reversed()) {
            val (pos, view) = visibleViews[j]
            val node = currentList.getOrNull(pos)
            if (node == null) {
                siblingCache[j] = 0L
                continue
            }

            // We store the bitmask we made earlier in the siblingcache for each row so that we can
            // know which lines need to pass through this row to reach the decks after it
            siblingCache[j] = activeLines

            // We look at the last deck that we can see and store it in d
            // eg:- if we have a child at the bottom of depth 2 our bits look like 0100
            val d = node.depth
            if (d < 64) {
                // We do an or with the activeLines (activelines stores the number of lines passing through
                // the current row) and this will give us the number of lines to draw
                activeLines = activeLines or (1L shl d)
                // We use a mask to mask off the bits for a deeper depth than what we calculated above
                val mask = if (d >= 63) -1L else (1L shl (d + 1)) - 1L
                activeLines = activeLines and mask
            }
        }

        // Get the ItemAnimator to help compute drawing parameters during animations
        val isAnimating = parent.itemAnimator?.isRunning ?: false

        // Create a layer to prevent alpha compounding of overlapping lines during animations
        val layerPaint = Paint().apply { alpha = 30 }
        val saveCount = c.saveLayer(0f, 0f, parent.width.toFloat(), parent.height.toFloat(), layerPaint)

        // Loop to draw the lines
        for (j in visibleViews.indices) {
            val (position, view) = visibleViews[j]
            val node = currentList.getOrNull(position)
            if (node == null) continue

            val viewAlpha = view.alpha
            // Hide lines entirely for views that are collapsing/disappearing
            if (viewAlpha <= 0f) continue
            // Apply a smooth fade out to lines when the item shrinks vertically
            val heightRatio =
                if (isAnimating &&
                    view.height < 48.dp.toPx(parent.context)
                ) {
                    view.height / 48.dp.toPx(parent.context).toFloat()
                } else {
                    1f
                }
            paint.alpha = (255 * viewAlpha * heightRatio).toInt().coerceIn(0, 255)

            val depth = node.depth

            // Handle vertical animation offsets
            val translationY = view.translationY
            val top = view.top + translationY
            val bottom = view.bottom + translationY
            val centerY = top + view.height / 2f

            // Helper to check the precomputed bitmask
            val hasSibling = { targetDepth: Int ->
                targetDepth < 64 && (siblingCache[j] and (1L shl targetDepth)) != 0L
            }

            for (level in 0 until depth - 1) {
                if (hasSibling(level + 1)) {
                    val x = getLineX(level)
                    c.drawLine(x, top, x, bottom, paint)
                }
            }

            if (depth > 0) {
                val level = depth - 1
                val x = getLineX(level)
                val childCenterX = getLineX(depth)
                // If the node has no children (is a leaf), the line should exactly match the visible
                // termination point of the lines pointing to sibling chevrons.
                val horizontalExtension = childCenterX - iconGap
                val endX = maxOf(x + cornerRadius, horizontalExtension)

                if (hasSibling(depth)) {
                    // Draw vertical line as top and bottom segments to avoid overlap at the T-junction
                    // which causes darker color from alpha overdraw
                    val strokeHalf = paint.strokeWidth / 2f
                    c.drawLine(x, top, x, centerY - strokeHalf, paint)
                    // If the view is animating out and shrinking, we shouldn't draw the bottom vertical stroke
                    // extending into empty space, as it causes a momentary visual glitch grid
                    if (!isAnimating || view.height >= 48.dp.toPx(parent.context)) {
                        c.drawLine(x, centerY + strokeHalf, x, bottom, paint)
                    }
                    // Draw horizontal line ending right at the vertical stroke's edge
                    c.drawLine(x - strokeHalf, centerY, endX, centerY, paint)
                } else {
                    reusablePath.reset()
                    reusablePath.moveTo(x, top)
                    reusablePath.lineTo(x, centerY - cornerRadius)
                    reusableRect.set(x, centerY - 2 * cornerRadius, x + 2 * cornerRadius, centerY)
                    reusablePath.arcTo(reusableRect, 180f, -90f, false)
                    reusablePath.lineTo(endX, centerY)
                    c.drawPath(reusablePath, paint)
                }
            }

            val nextNode = currentList.getOrNull(position + 1)
            // The vertical line originating beneath the chevron must be anchored to the chevron's
            // Y-coordinate center + offset instead of the view's center, since padding/layout might
            // cause the chevron to not be perfectly centered relative to the view bounds
            if (nextNode != null && nextNode.depth == depth + 1 && (!isAnimating || view.height >= 48.dp.toPx(parent.context))) {
                val x = getLineX(depth)
                val chevronBottomY = centerY + iconGap
                c.drawLine(x, chevronBottomY, x, bottom, paint)
            }
        }

        c.restoreToCount(saveCount)
        paint.alpha = 30 // restore default state
    }

    private fun getLineX(depth: Int): Float = depth * nestedIndent + expanderCenterOffset

    companion object {
        private const val LINE_STROKE_WIDTH_DP = 2f
    }
}
