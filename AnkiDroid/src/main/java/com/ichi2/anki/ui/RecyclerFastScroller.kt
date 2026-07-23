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
 *
 * This file incorporates code under the following license
 *
 *     Copyright 2016 Daniel Ciao
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *  https://github.com/pluscubed/recycler-fast-scroll/blob/3de76812553a77bfd25d3aea0a0af4d96516c3e3/library/src/main/java/com/pluscubed/recyclerfastscroll/RecyclerFastScroller.java
 *
 * CHANGES:
 * * Converted Java to Kotlin
 * * Removed Hungarian notation
 * * Add attachFastScroller method
 * * Reduced variable access
 * * converted hideDelay to time.Duration
 * * removed styleable elements and went with defaults (colorControlNormal)
 * * inlined a number of variables set in init { }
 */

package com.ichi2.anki.ui
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.GravityCompat
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.utils.ext.wholeAndFraction
import com.ichi2.anki.utils.postDelayed
import com.ichi2.utils.dp
import com.ichi2.utils.isRtl
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RecyclerFastScroller
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        private val bar: View
        private val handle: View
        val hiddenTranslationX: Int
        private val hide: Runnable
        private val minScrollHandleHeight: Int = 48.dp.toPx(context)
        var onHandleTouchListener: OnTouchListener? = null

        private var recyclerView: RecyclerView? = null

        private var animator: AnimatorSet? = null
        private var animatingIn: Boolean = false

        /**
         * the delay in millis to hide the scrollbar
         */
        private var hideDelay: Duration = DEFAULT_AUTO_HIDE_DELAY
        private var handleNormalColor: Int =
            MaterialColors.getColor(context, android.R.attr.colorControlNormal, 0)
        private var handlePressedColor: Int =
            MaterialColors.getColor(context, android.R.attr.colorAccent, 0)
        private var barColor: Int =
            MaterialColors.getColor(context, android.R.attr.colorControlNormal, 0)
        private var barInset = 0

        private var hideOverride = false
        private var adapter: RecyclerView.Adapter<*>? = null

        // Thumb height and range, cached so the handle keeps a steady size and scale while
        // scrolling rows of different heights. 0 means "recompute on the next layout". Item count,
        // bar height and width invalidate it (width because rows can rewrap and change the total
        // scroll range).
        private var cachedHandleHeight = 0
        private var cachedScrollRange = 0
        private var cachedItemCount = RecyclerView.NO_POSITION
        private var cachedBarHeight = 0
        private var cachedWidth = 0
        private var accumulatedScrollOffset = 0
        private var scrollOffsetInitialized = false
        private var scrollRangeCalibrated = false
        private var canCalibrateScrollRange = false
        private var isDraggingHandle = false
        private var dragVisibleItemCount = 1
        private var handlePositionInitialized = false
        private var wasAtBottom = false
        private var isAnimatingHandleToBottom = false
        private var handleAnimationTargetY = 0f

        private fun invalidateScrollMetrics() {
            cachedHandleHeight = 0
            cachedScrollRange = 0
            cachedItemCount = RecyclerView.NO_POSITION
            cachedBarHeight = 0
            cachedWidth = 0
            accumulatedScrollOffset = 0
            scrollOffsetInitialized = false
            scrollRangeCalibrated = false
            canCalibrateScrollRange = false
            handlePositionInitialized = false
            wasAtBottom = false
            isAnimatingHandleToBottom = false
        }

        private fun onAdapterDataChanged() {
            invalidateScrollMetrics()
            requestLayout()
        }

        // We cache scroll thumb params to make it smooth on variable-height rows,
        // since RecyclerView's scrollbar range is only an estimate.
        // We update the cached values when the adapter changes or the bar height/width changes.
        private val adapterObserver: RecyclerView.AdapterDataObserver =
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() = onAdapterDataChanged()

                // Content-only row updates, such as focus or selection changes, intentionally use
                // the default no-op callback so they cannot move or resize the scroll thumb.

                override fun onItemRangeInserted(
                    positionStart: Int,
                    itemCount: Int,
                ) = onAdapterDataChanged()

                override fun onItemRangeRemoved(
                    positionStart: Int,
                    itemCount: Int,
                ) = onAdapterDataChanged()
            }

        /**
         * @throws RuntimeException if set to more than 48dp
         */
        var touchTargetWidth: Int = 24.dp.toPx(context)
            set(touchTargetWidth) {
                field = touchTargetWidth

                val eightDp: Int = 8.dp.toPx(context)
                barInset = touchTargetWidth - eightDp

                val fortyEightDp: Int = 48.dp.toPx(context)
                if (touchTargetWidth > fortyEightDp) {
                    throw RuntimeException("Touch target width cannot be larger than 48dp!")
                }

                bar.layoutParams =
                    LayoutParams(
                        touchTargetWidth,
                        LayoutParams.MATCH_PARENT,
                        GravityCompat.END,
                    )
                handle.layoutParams =
                    LayoutParams(
                        touchTargetWidth,
                        LayoutParams.MATCH_PARENT,
                        GravityCompat.END,
                    )

                updateHandleColorsAndInset()
                updateBarColorAndInset()
            }

        init {

            layoutParams = LayoutParams(minScrollHandleHeight, LayoutParams.MATCH_PARENT)

            bar = View(context)
            handle = View(context)
            addView(bar)
            addView(handle)

            // execute the setter logic
            touchTargetWidth = 24.dp.toPx(context)

            val eightDp: Int = 8.dp.toPx(context)
            hiddenTranslationX =
                (if (isRtl()) -1 else 1) * eightDp
            hide =
                Runnable {
                    if (!handle.isPressed) {
                        if (animator != null && animator!!.isStarted) {
                            animator!!.cancel()
                        }
                        animator = AnimatorSet()
                        val animator2 =
                            ObjectAnimator.ofFloat(
                                this@RecyclerFastScroller,
                                TRANSLATION_X,
                                hiddenTranslationX.toFloat(),
                            )
                        animator2.interpolator = FastOutLinearInInterpolator()
                        animator2.setDuration(150)
                        handle.isEnabled = false
                        animator!!.play(animator2)
                        animator!!.start()
                    }
                }
            translationX = hiddenTranslationX.toFloat()
        }

        /**
         * whether hiding is enabled
         */
        var isHidingEnabled: Boolean = true
            set(hidingEnabled) {
                field = hidingEnabled
                if (hidingEnabled) {
                    postAutoHide()
                }
            }

        private fun updateHandleColorsAndInset() {
            val drawable = StateListDrawable()

            if (!isRtl()) {
                drawable.addState(
                    PRESSED_ENABLED_STATE_SET,
                    InsetDrawable(handlePressedColor.toDrawable(), barInset, 0, 0, 0),
                )
                drawable.addState(
                    EMPTY_STATE_SET,
                    InsetDrawable(handleNormalColor.toDrawable(), barInset, 0, 0, 0),
                )
            } else {
                drawable.addState(
                    PRESSED_ENABLED_STATE_SET,
                    InsetDrawable(handlePressedColor.toDrawable(), 0, 0, barInset, 0),
                )
                drawable.addState(
                    EMPTY_STATE_SET,
                    InsetDrawable(handleNormalColor.toDrawable(), 0, 0, barInset, 0),
                )
            }
            handle.background = drawable
        }

        private fun updateBarColorAndInset() {
            val drawable: Drawable =
                if (!isRtl()) {
                    InsetDrawable(barColor.toDrawable(), barInset, 0, 0, 0)
                } else {
                    InsetDrawable(barColor.toDrawable(), 0, 0, barInset, 0)
                }
            drawable.alpha = 57
            bar.background = drawable
        }

        fun attachRecyclerView(recyclerView: RecyclerView) {
            this.recyclerView = recyclerView
            this.recyclerView!!.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int,
                    ) {
                        super.onScrolled(recyclerView, dx, dy)
                        // Track normal list scrolling from real pixel deltas. While the handle is being dragged,
                        // the offset is set from the drag position instead, so do not apply RecyclerView's dy too.
                        if (!isDraggingHandle) {
                            val scrollablePixels = resolveScrollablePixels(recyclerView)
                            updateAccumulatedScrollOffset(recyclerView, dy, scrollablePixels)
                        }
                        this@RecyclerFastScroller.show(true)
                    }
                },
            )
            if (recyclerView.adapter != null) attachAdapter(recyclerView.adapter)
        }

        private fun attachAdapter(adapter: RecyclerView.Adapter<*>?) {
            if (this.adapter === adapter) return
            this.adapter?.unregisterAdapterDataObserver(adapterObserver)
            adapter?.registerAdapterDataObserver(adapterObserver)
            this.adapter = adapter
            invalidateScrollMetrics()
        }

        /**
         * Show the fast scroller and hide after delay
         *
         * @param animate whether to animate showing the scroller
         */
        fun show(animate: Boolean) {
            requestLayout()

            post(
                Runnable {
                    if (hideOverride) {
                        return@Runnable
                    }
                    handle.isEnabled = true
                    if (animate) {
                        if (!animatingIn && translationX != 0f) {
                            if (animator != null && animator!!.isStarted) {
                                animator!!.cancel()
                            }
                            animator = AnimatorSet()
                            val animator =
                                ObjectAnimator.ofFloat(this@RecyclerFastScroller, TRANSLATION_X, 0f)
                            animator.interpolator = LinearOutSlowInInterpolator()
                            animator.setDuration(100)
                            animator.addListener(
                                object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        super.onAnimationEnd(animation)
                                        animatingIn = false
                                    }
                                },
                            )
                            animatingIn = true
                            this.animator!!.play(animator)
                            this.animator!!.start()
                        }
                    } else {
                        translationX = 0f
                    }
                    postAutoHide()
                },
            )
        }

        fun postAutoHide() {
            if (!isHidingEnabled) return
            recyclerView?.apply {
                removeCallbacks(hide)
                postDelayed(hide, hideDelay)
            }
        }

        /**
         * The current scroll progress as a value between 0.0 and 1.0.
         */
        private var pendingScrollProportion = 0f

        // Task that converts handle position into scroll command
        private val scrollTask =
            Runnable {
                val lm = recyclerView?.layoutManager as? LinearLayoutManager ?: return@Runnable
                val adapter = recyclerView?.adapter ?: return@Runnable

                try {
                    // Calculate the exact target including the decimal
                    val (targetIndex, fraction) =
                        computeDragTargetIndex(pendingScrollProportion, adapter.itemCount, dragVisibleItemCount)
                            .wholeAndFraction()
                    // Estimate height using the first visible view, this is a heuristic
                    val estimatedHeight = recyclerView?.getChildAt(0)?.height ?: 0

                    // Calculate the offset by pushing the item up by the fraction of its height
                    // e.g. If at 99.9%, push the last card up by 90% of its height so we can see the bottom.
                    val offset = -(fraction * estimatedHeight).toInt()
                    lm.scrollToPositionWithOffset(targetIndex.toInt(), offset)
                } catch (e: Exception) {
                    Timber.w(e, "scrollToPosition")
                }
            }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Retrieve the adapter to determine item count.
                    val adapter = recyclerView?.adapter ?: return false

                    if (adapter.itemCount == 0) return false

                    // Force the handle to be selected since the user is touching the track (the parent container) and not the handle itself.
                    handle.isPressed = true
                    isDraggingHandle = true
                    canCalibrateScrollRange = false
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        dragVisibleItemCount =
                            (recyclerView?.childCount ?: 1).coerceIn(1, adapter.itemCount)
                    }

                    // The valid scroll area is (height-handle.height), since the position of the handle is defined by it's top edge, we subtract it.
                    val scrollableHeight = height - handle.height

                    // Convert the finger's Y coordinate to track progress using the handle's center,
                    // then clamp it so the handle cannot leave the track.
                    val scrollProportion =
                        ((event.y - handle.height / 2) / scrollableHeight.coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                    pendingScrollProportion = scrollProportion
                    recyclerView?.let {
                        val scrollablePixels = resolveScrollablePixels(it)
                        accumulatedScrollOffset = (scrollProportion * scrollablePixels).toInt().coerceIn(0, scrollablePixels)
                        scrollOffsetInitialized = scrollablePixels > 0
                    }
                    // Leave room for the visible items so the list reaches its last screen only at
                    // the end of the track.
                    val targetPosition =
                        computeDragTargetIndex(scrollProportion, adapter.itemCount, dragVisibleItemCount)
                            .toInt()
                            .coerceIn(0, adapter.itemCount - 1)

                    try {
                        (recyclerView?.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(targetPosition, 0)
                            ?: recyclerView?.scrollToPosition(targetPosition)
                    } catch (e: Exception) {
                        Timber.w(e, "scrollToPosition")
                    }

                    // destroys any redundant calls to the scrolltask and sets a small delay to improve performance
                    recyclerView?.removeCallbacks(scrollTask)
                    recyclerView?.postDelayed(scrollTask, 20.milliseconds)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handle.isSelected = false
                    if (recyclerView != null) {
                        recyclerView?.removeCallbacks(scrollTask)
                        scrollTask.run()
                    }
                    handle.isPressed = false
                    isDraggingHandle = false
                    false
                }
                else -> super.onTouchEvent(event)
            }
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)

            val recyclerView = recyclerView ?: return
            // The adapter can be set after we were attached, so make sure our data observer is
            // registered. Without it the cached thumb size never refreshes on a data change.
            if (recyclerView.adapter !== adapter) attachAdapter(recyclerView.adapter)

            val itemCount = recyclerView.adapter?.itemCount ?: return
            if (itemCount == 0) {
                hideThumb()
                return
            }

            val barHeight = bar.height
            if (barHeight == 0) return

            // The whole list fits, so there is nothing to scroll: keep the thumb hidden.
            if (!recyclerView.canScrollVertically(-1) && !recyclerView.canScrollVertically(1)) {
                hideThumb()
                return
            }
            hideOverride = false

            val measuredScrollRange = recyclerView.computeVerticalScrollRange() + recyclerView.paddingBottom
            val scrollRange = resolveScrollRange(itemCount, barHeight, recyclerView.width, measuredScrollRange)
            if (scrollRange <= barHeight) return

            val scrollablePixels = scrollRange - barHeight
            updateAccumulatedScrollOffset(recyclerView, dy = 0, scrollablePixels)

            val handleHeight = resolveHandleHeight(barHeight, scrollRange)
            val isAtBottom = !recyclerView.canScrollVertically(1)
            val ratio =
                computeHandleScrollProportion(
                    isDraggingHandle = isDraggingHandle,
                    dragProportion = pendingScrollProportion,
                    scrollOffset = accumulatedScrollOffset,
                    scrollRange = cachedScrollRange,
                    barHeight = barHeight,
                    canScrollDown = !isAtBottom,
                    rangeCalibrated = scrollRangeCalibrated,
                )

            val y = ratio * (barHeight - handleHeight)
            val animateToBottom =
                shouldAnimateHandleToBottom(
                    wasAtBottom = wasAtBottom,
                    isAtBottom = isAtBottom,
                    isDraggingHandle = isDraggingHandle,
                    handlePositionInitialized = handlePositionInitialized,
                )
            layoutHandle(y, handleHeight, animateToBottom)
            wasAtBottom = isAtBottom
        }

        /**
         * Applies handle positions immediately except for the final correction when normal scrolling
         * reaches the real bottom. That correction is animated to avoid a visible jump; dragging always
         * remains synchronous with the user's finger.
         */
        private fun layoutHandle(
            targetY: Float,
            handleHeight: Int,
            animateToBottom: Boolean,
        ) {
            handle.layout(handle.left, 0, handle.right, handleHeight)

            if (isAnimatingHandleToBottom && handleAnimationTargetY == targetY) return

            if (!handlePositionInitialized || isDraggingHandle || !animateToBottom) {
                handle.animate().cancel()
                isAnimatingHandleToBottom = false
                handle.translationY = targetY
                handlePositionInitialized = true
                return
            }

            if (handle.translationY == targetY) return
            handle.animate().cancel()
            isAnimatingHandleToBottom = true
            handleAnimationTargetY = targetY
            handle
                .animate()
                .translationY(targetY)
                .setDuration(HANDLE_POSITION_ANIMATION_DURATION_MS)
                .setInterpolator(LinearInterpolator())
                .withEndAction { isAnimatingHandleToBottom = false }
                .start()
        }

        // Slides the scroller off screen and stops show() bringing it back while nothing scrolls.
        private fun hideThumb() {
            translationX = hiddenTranslationX.toFloat()
            hideOverride = true
        }

        private fun resolveScrollablePixels(recyclerView: RecyclerView): Int {
            val itemCount = recyclerView.adapter?.itemCount ?: return 0
            val barHeight = bar.height
            if (itemCount == 0 || barHeight == 0) return 0

            val measuredScrollRange = recyclerView.computeVerticalScrollRange() + recyclerView.paddingBottom
            val scrollRange = resolveScrollRange(itemCount, barHeight, recyclerView.width, measuredScrollRange)
            return (scrollRange - barHeight).coerceAtLeast(0)
        }

        /**
         * Maintains a stable pixel offset from actual scroll deltas instead of repeatedly using
         * RecyclerView's changing estimate. A normal scroll from the top enables calibration; when the
         * real bottom is reached, the accumulated distance becomes the cached scroll range.
         */
        private fun updateAccumulatedScrollOffset(
            recyclerView: RecyclerView,
            dy: Int,
            scrollablePixels: Int,
        ) {
            if (scrollablePixels <= 0) {
                accumulatedScrollOffset = 0
                scrollOffsetInitialized = false
                return
            }

            if (!scrollOffsetInitialized) {
                accumulatedScrollOffset = recyclerView.computeVerticalScrollOffset().coerceAtLeast(0)
                scrollOffsetInitialized = true
            } else {
                accumulatedScrollOffset =
                    computeScrollOffsetFromDelta(
                        currentOffset = accumulatedScrollOffset,
                        dy = dy,
                        canScrollUp = recyclerView.canScrollVertically(-1),
                    )
            }

            if (!recyclerView.canScrollVertically(-1)) {
                accumulatedScrollOffset = 0
                canCalibrateScrollRange = !isDraggingHandle
            } else if (!recyclerView.canScrollVertically(1) && canCalibrateScrollRange) {
                cachedScrollRange = accumulatedScrollOffset + bar.height
                scrollRangeCalibrated = true
            }
        }

        /**
         * Scroll range for the current list, computed once and cached. RecyclerView estimates it
         * from currently visible rows, so it can drift while scrolling variable-height rows.
         * Recomputed when the data, bar height or width change (see [invalidateScrollMetrics]).
         */
        private fun resolveScrollRange(
            itemCount: Int,
            barHeight: Int,
            width: Int,
            scrollRange: Int,
        ): Int {
            if (itemCount != cachedItemCount || barHeight != cachedBarHeight || width != cachedWidth) {
                cachedHandleHeight = 0
                cachedScrollRange = 0
                cachedItemCount = itemCount
                cachedBarHeight = barHeight
                cachedWidth = width
                accumulatedScrollOffset = 0
                scrollOffsetInitialized = false
                scrollRangeCalibrated = false
                canCalibrateScrollRange = false
                handlePositionInitialized = false
                wasAtBottom = false
                isAnimatingHandleToBottom = false
            }

            if (cachedScrollRange == 0) {
                cachedScrollRange = scrollRange
            }

            return cachedScrollRange
        }

        private fun resolveHandleHeight(
            barHeight: Int,
            scrollRange: Int,
        ): Int {
            if (cachedHandleHeight == 0) {
                cachedHandleHeight = computeThumbHeight(barHeight, scrollRange, minScrollHandleHeight)
            }
            return cachedHandleHeight
        }

        companion object {
            private const val HANDLE_POSITION_ANIMATION_DURATION_MS = 100L
            private val DEFAULT_AUTO_HIDE_DELAY = 1500.milliseconds
        }
    }

/**
 * Thumb height as the share of the content that fits on screen (bar height over the scroll
 * range), clamped to a usable range. Independent of scroll position, so a cached value stays valid.
 */
@VisibleForTesting
internal fun computeThumbHeight(
    barHeight: Int,
    scrollRange: Int,
    minHandleHeight: Int,
): Int =
    (barHeight.toFloat() * barHeight / scrollRange.coerceAtLeast(1))
        .toInt()
        .coerceAtLeast(minHandleHeight)
        .coerceAtMost(barHeight)

/**
 * Scroll progress from 0f to 1f, measured in pixels so the thumb tracks the scroll smoothly on
 * rows of different heights. Guards against a zero divisor when the list barely scrolls.
 */
@VisibleForTesting
internal fun computeScrollProportion(
    scrollOffset: Int,
    scrollRange: Int,
    barHeight: Int,
): Float {
    val scrollablePixels = (scrollRange - barHeight).coerceAtLeast(1)
    return (scrollOffset.toFloat() / scrollablePixels).coerceIn(0f, 1f)
}

@VisibleForTesting
internal fun computeScrollOffsetFromDelta(
    currentOffset: Int,
    dy: Int,
    canScrollUp: Boolean,
): Int {
    if (!canScrollUp) return 0
    return (currentOffset + dy).coerceAtLeast(0)
}

/**
 * Maps an uncalibrated range smoothly toward, but never onto, the end of the track. RecyclerView's
 * first range estimate can be shorter than the real content, so a linear mapping would put the
 * thumb at the bottom too early. Once a complete top-to-bottom scroll calibrates the range, the
 * regular linear mapping is exact.
 */
@VisibleForTesting
internal fun computeDisplayScrollProportion(
    scrollOffset: Int,
    scrollRange: Int,
    barHeight: Int,
    canScrollDown: Boolean,
    rangeCalibrated: Boolean,
): Float {
    if (!canScrollDown) return 1f

    val scrollablePixels = (scrollRange - barHeight).coerceAtLeast(1)
    val rawProportion = (scrollOffset.toFloat() / scrollablePixels).coerceAtLeast(0f)
    if (rangeCalibrated) return computeScrollProportion(scrollOffset, scrollRange, barHeight)
    if (rawProportion <= END_APPROACH_THRESHOLD) return rawProportion

    val tail = 1f - END_APPROACH_THRESHOLD
    val excess = (rawProportion - END_APPROACH_THRESHOLD) / tail
    return END_APPROACH_THRESHOLD + tail * excess / (1f + excess)
}

/**
 * Uses the finger's track position while dragging and the accumulated pixel offset otherwise.
 * Keeping these sources separate prevents scroll callbacks caused by a drag from moving the
 * handle away from the finger.
 */
@VisibleForTesting
internal fun computeHandleScrollProportion(
    isDraggingHandle: Boolean,
    dragProportion: Float,
    scrollOffset: Int,
    scrollRange: Int,
    barHeight: Int,
    canScrollDown: Boolean,
    rangeCalibrated: Boolean,
): Float =
    if (isDraggingHandle) {
        dragProportion.coerceIn(0f, 1f)
    } else {
        computeDisplayScrollProportion(scrollOffset, scrollRange, barHeight, canScrollDown, rangeCalibrated)
    }

/**
 * Maps drag progress to a valid first visible adapter position. Reserving the visible rows keeps
 * the last viewport aligned with the end of the track; the exact endpoint targets the final item
 * so the LayoutManager can clamp the list to its real bottom.
 */
@VisibleForTesting
internal fun computeDragTargetIndex(
    scrollProportion: Float,
    itemCount: Int,
    visibleItemCount: Int,
): Double {
    if (itemCount <= 0) return 0.0

    val proportion = scrollProportion.coerceIn(0f, 1f)
    if (proportion == 1f) return (itemCount - 1).toDouble()

    val lastFirstVisiblePosition = itemCount - visibleItemCount.coerceIn(1, itemCount)
    return proportion * lastFirstVisiblePosition.toDouble()
}

// Keep most of the track linear and reserve the final 4% for approaching an uncertain end smoothly.
// 4% is heuristic that makes the thumb move smoothly on smartphone
private const val END_APPROACH_THRESHOLD = 0.96f

@VisibleForTesting
internal fun shouldAnimateHandleToBottom(
    wasAtBottom: Boolean,
    isAtBottom: Boolean,
    isDraggingHandle: Boolean,
    handlePositionInitialized: Boolean,
): Boolean = handlePositionInitialized && !isDraggingHandle && !wasAtBottom && isAtBottom

fun RecyclerView.attachFastScroller(
    @IdRes id: Int,
) {
    (parent as? ViewGroup)
        ?.findViewById<RecyclerFastScroller>(id)
        ?.attachRecyclerView(this)
}
