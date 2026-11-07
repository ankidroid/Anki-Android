/*
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
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.GravityCompat
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.utils.ext.wholeAndFraction
import com.ichi2.anki.utils.postDelayed
import com.ichi2.utils.isRtl
import timber.log.Timber
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RecyclerFastScroller
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        @VisibleForTesting
        internal val bar: View

        @VisibleForTesting
        internal val handle: View
        val hiddenTranslationX: Int
        private val hide: Runnable
        private val minScrollHandleHeight: Int = 48.dp.toPx(context)
        var onHandleTouchListener: OnTouchListener? = null

        private var appBarLayoutOffset: Int = 0

        /**
         * Inset, in pixels, reserved at the bottom of the handle's travel.
         *
         * For rounded display corners with edge to edge support.
         *
         * The handle is constrained to `height - handleBottomInset`, so it stays touchable and its
         * bottom aligns with the list's last item at full scroll.
         *
         * The track is drawn edge-to-edge while scrolling and its bottom retracts to the same
         * positions as the last row of the content when approaching the end.
         */
        var handleBottomInset: Int = 0
            set(value) {
                if (field == value) return
                field = value
                requestLayout()
            }

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
        private val adapterObserver: RecyclerView.AdapterDataObserver =
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    super.onChanged()
                    drag?.onRowsChanged()
                    requestLayout()
                }

                override fun onItemRangeChanged(
                    positionStart: Int,
                    itemCount: Int,
                ) {
                    drag?.onRowsChanged()
                }

                override fun onItemRangeInserted(
                    positionStart: Int,
                    itemCount: Int,
                ) {
                    drag?.onRowsChanged()
                }

                override fun onItemRangeRemoved(
                    positionStart: Int,
                    itemCount: Int,
                ) {
                    drag?.onRowsChanged()
                }

                override fun onItemRangeMoved(
                    fromPosition: Int,
                    toPosition: Int,
                    itemCount: Int,
                ) {
                    drag?.onRowsChanged()
                }
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
         * The current drag. It is kept after release so a frame it queued can still run, and the next
         * press replaces it.
         */
        private var drag: DragSession? = null

        /**
         * Starts a [DragSession] for a press at [y].
         *
         * The adapter observer is registered here too, since some screens set their adapter after
         * attaching the scroller.
         */
        private fun startDrag(
            recyclerView: RecyclerView,
            y: Float,
        ) {
            drag?.cancel()
            attachAdapter(recyclerView.adapter)
            val session = DragSession(recyclerView, thumbHeight = handle.height, relayoutThumb = ::layoutBeforeDraw)
            drag = session
            session.press(y, scrollableHeight(session.thumbHeight), thumb = handle.top..handle.bottom, isThumbStale = isLayoutRequested)
        }

        // The valid scroll area is (usable track - handle.height), since the position of the handle is defined by its top edge, we subtract it.
        // The usable track excludes handleBottomInset (nav bar) so the handle can't be dragged into it.
        private fun scrollableHeight(thumbHeight: Int) = ((height - handleBottomInset) - thumbHeight).coerceAtLeast(1)

        /**
         * Lays the thumb out now. A settle runs in pre-draw, where a requestLayout would only show on
         * the next frame.
         */
        private fun layoutBeforeDraw() {
            forceLayout()
            measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            layout(left, top, right, bottom)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val recyclerView = recyclerView ?: return false
                    // Retrieve the adapter to determine item count.
                    val adapter = recyclerView.adapter ?: return false

                    if (adapter.itemCount == 0) return false

                    // Force the handle to be selected since the user is touching the track (the parent container) and not the handle itself.
                    handle.isPressed = true

                    val session = drag
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || session == null) {
                        startDrag(recyclerView, event.y)
                    } else {
                        session.moveTo(event.y, scrollableHeight(session.thumbHeight))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handle.isPressed = false
                    drag?.release()
                    postAutoHide()
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
            if (recyclerView == null) return

            val scrollOffset = recyclerView!!.computeVerticalScrollOffset() + appBarLayoutOffset
            // The content range and the visible viewport come from the RecyclerView itself.
            // Sizing & positioning the handle against the viewport (rather than the track height)
            // keeps it proportional even when the track is shortened to clear the navigation bar,
            // so the handle reaches the bottom of the track exactly when the list reaches its end.
            val verticalScrollRange = recyclerView!!.computeVerticalScrollRange()
            val verticalScrollExtent = recyclerView!!.computeVerticalScrollExtent()

            // The track (bar) spans the full height, but the handle travels only the area above
            // handleBottomInset so it stays clear of the navigation bar / rounded corner.
            val fullBarHeight = bar.height
            val trackHeight = (fullBarHeight - handleBottomInset).coerceAtLeast(0)
            val maxScrollOffset = (verticalScrollRange - verticalScrollExtent).coerceAtLeast(1)
            val ratio = (scrollOffset.toFloat() / maxScrollOffset).coerceIn(0f, 1f)

            var calculatedHandleHeight = (trackHeight.toFloat() * verticalScrollExtent / verticalScrollRange).toInt()
            if (calculatedHandleHeight < minScrollHandleHeight) {
                calculatedHandleHeight = minScrollHandleHeight
            }

            if (calculatedHandleHeight >= trackHeight) {
                translationX = hiddenTranslationX.toFloat()
                hideOverride = true
                return
            }

            hideOverride = false

            val y = ratio * (trackHeight - calculatedHandleHeight)

            handle.layout(handle.left, y.toInt(), handle.right, y.toInt() + calculatedHandleHeight)

            // The track is edge-to-edge (drawn under the navigation bar) while the bottom of the last
            // row is below the viewport. Once that bottom scrolls into view the track follows it
            // exactly, so they stay aligned.
            val layoutManager = recyclerView!!.layoutManager
            val lastPosition = (recyclerView!!.adapter?.itemCount ?: 0) - 1
            // The last row's bottom comes from its real on-screen position
            // Note: Offsets can't be used - LinearLayoutManager reports them in scrollbar units
            val lastRowBottomEdge =
                lastPosition
                    .takeIf { it >= 0 }
                    ?.let { layoutManager?.findViewByPosition(it) }
                    ?.let { layoutManager!!.getDecoratedBottom(it) }

            // off-screen (or not laid out) → edge-to-edge; visible → follow the row's bottom
            val isEdgeToEdge = lastRowBottomEdge == null || lastRowBottomEdge >= fullBarHeight
            val barBottom = bar.top + if (isEdgeToEdge) fullBarHeight else lastRowBottomEdge
            if (bar.bottom != barBottom) {
                bar.layout(bar.left, bar.top, bar.right, barBottom)
            }
        }

        /**
         * One press on the fast scroller, from ACTION_DOWN until the finger lifts.
         *
         * The finger's position maps to rows through a [DragPivot] taken from the rows on screen, so
         * the list moves in step with the finger from wherever it started.
         *
         * Lifecycle:
         * 1. Press ([press]): stops any fling and takes the pivot. A press on the thumb keeps the
         *    point it grabbed under the finger and does not scroll. A tap on the track scrolls there.
         * 2. Move ([moveTo]): updates [fingerProportion] and queues a frame. Moves within one frame
         *    are placed together.
         * 3. Layout ([placeRows], on the next animation frame): scrolls to [target]. If the target row
         *    is not laid out, it jumps to the row and [settleAfterLayout] corrects the position with
         *    the row's real height just before that frame is drawn. If the rows changed, the pivot is
         *    retaken from the rows on screen first.
         * 4. Release (ACTION_UP or ACTION_CANCEL): nothing scrolls. A queued frame or settle still
         *    runs, so the last move shows, unless the rows change first. The next press [cancel]s
         *    this session.
         */
        private class DragSession(
            private val recyclerView: RecyclerView,
            /** The thumb's height at the press, kept so a resize mid-drag cannot nudge the list. */
            val thumbHeight: Int,
            /** Lays the thumb out, so it is drawn where a settle left the list. */
            private val relayoutThumb: () -> Unit,
        ) {
            /** Where the finger is, as a share of the thumb's travel: 0 at the top, 1 at the bottom. */
            private var fingerProportion = 0f

            /** The finger's y at the last press or move, so a move past a track end is still placed. */
            private var fingerY = 0f

            /** The [fingerProportion] last placed. A pivot retaken after the rows change is taken here. */
            private var shownProportion = 0f

            /** Pixels from the thumb's centre to where the finger grabbed it, or null for a tap on the track. */
            private var grabOffset: Float? = null

            /** Anchors the finger's mapping to rows. Null until the rows are laid out after a change. */
            private var pivot: DragPivot? = null

            /** The pre-draw listener waiting to correct a jump, if any. */
            private var settle: ViewTreeObserver.OnPreDrawListener? = null

            /** Jumps left before [placeRows] stops chasing a row that never gets laid out. */
            private var settlesLeft = 0

            private var isFramePending = false

            private var isReleased = false

            private val frame =
                Runnable {
                    isFramePending = false
                    placeRows()
                }

            /**
             * Handles ACTION_DOWN at [y].
             *
             * @param thumb the thumb's drawn top to bottom
             * @param isThumbStale true if the thumb's layout is pending, so it may not match the list yet
             */
            fun press(
                y: Float,
                scrollableHeight: Int,
                thumb: IntRange,
                isThumbStale: Boolean,
            ) {
                recyclerView.stopScroll()
                val thumbProportion = (thumb.first.toFloat() / scrollableHeight).coerceIn(0f, 1f)
                val isLaidOut = !isThumbStale && !hasPendingLayout()
                pivot = if (isLaidOut) pivotAt(thumbProportion, snapToEnds = true) else null
                shownProportion = pivot?.proportion ?: thumbProportion.coerceIn(MIN_PIVOT_PROPORTION, 1 - MIN_PIVOT_PROPORTION)
                val isOnThumb = y.toInt() in thumb
                val grabProportion = if (thumbProportion == 0f || thumbProportion == 1f) thumbProportion else shownProportion
                grabOffset = if (isOnThumb) y - (thumbHeight / 2 + grabProportion * scrollableHeight) else null
                fingerY = y
                fingerProportion = proportionAt(y, scrollableHeight)
                if (grabOffset == null) requestFrame()
            }

            /** Handles ACTION_MOVE at [y]. Queues a frame if the finger moved or the pivot needs retaking. */
            fun moveTo(
                y: Float,
                scrollableHeight: Int,
            ) {
                val previousY = fingerY
                fingerY = y
                fingerProportion = proportionAt(y, scrollableHeight)
                if (y != previousY || pivot == null) requestFrame()
            }

            /** Handles ACTION_UP or ACTION_CANCEL. */
            fun release() {
                isReleased = true
            }

            /**
             * Called on any adapter change. The pivot's rows may have moved, so the next placement
             * retakes it. After release, the finger is gone and a queued placement was for the old
             * rows, so it is dropped.
             */
            fun onRowsChanged() {
                pivot = null
                if (isReleased) cancel()
            }

            /** Drops a queued frame or settle, when a new press replaces this session or the rows change after release. */
            fun cancel() {
                recyclerView.removeCallbacks(frame)
                isFramePending = false
                cancelSettle()
            }

            /**
             * The finger's share of the thumb's travel. The finger keeps the point it grabbed on the
             * thumb, and a tap on the track puts the thumb's centre under it.
             */
            private fun proportionAt(
                y: Float,
                scrollableHeight: Int,
            ) = ((y - (grabOffset ?: 0f) - thumbHeight / 2) / scrollableHeight).coerceIn(0f, 1f)

            /**
             * Queues [placeRows] for the next animation frame. Moves until then only update
             * [fingerProportion], so they are placed together in one scroll.
             */
            private fun requestFrame() {
                if (isFramePending) return
                isFramePending = true
                recyclerView.postOnAnimation(frame)
            }

            /**
             * Scrolls the list to where the finger maps to.
             *
             * @param isSettling true when a settle calls it after a jump. It then keeps counting down
             * [settlesLeft] instead of starting again, so the chase for a row is capped.
             */
            private fun placeRows(isSettling: Boolean = false) {
                cancelSettle()
                val itemCount = recyclerView.adapter?.itemCount ?: return
                if (itemCount == 0) return
                if (!isSettling) settlesLeft = MAX_SETTLES

                try {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    if (layoutManager == null) {
                        recyclerView.scrollToPosition((fingerProportion * itemCount).toInt().coerceAtMost(itemCount - 1))
                        return
                    }
                    if (!refreshPivot(itemCount)) return
                    val target = target(itemCount) ?: return
                    shownProportion = fingerProportion
                    if (scrollExactlyTo(target, layoutManager, itemCount)) return
                    jumpAndSettle(target, layoutManager, itemCount)
                } catch (e: Exception) {
                    Timber.w(e, "scrollToPosition")
                }
            }

            /**
             * Drops a [pivot] taken for a different item count, and retakes a missing one at
             * [shownProportion], so the list carries on from the rows on screen.
             *
             * No pivot is taken at a track end. The ends need none, and the next move away takes it
             * from the end the list went to.
             *
             * @return false while a layout is pending. A settle runs [placeRows] again after it.
             */
            private fun refreshPivot(itemCount: Int): Boolean {
                if (pivot?.itemCount != itemCount) pivot = null
                if (pivot != null) return true
                if (hasPendingLayout()) {
                    settleAfterLayout()
                    return false
                }
                if (fingerProportion > 0f && fingerProportion < 1f) {
                    pivot = pivotAt(shownProportion, snapToEnds = false)
                }
                return true
            }

            /**
             * Scrolls straight to [target] if its row is laid out, so the distance is exact.
             *
             * @return false if the row is not laid out, or a layout is pending so its position is stale
             */
            private fun scrollExactlyTo(
                target: DragTarget,
                layoutManager: LinearLayoutManager,
                itemCount: Int,
            ): Boolean {
                if (recyclerView.isLayoutRequested) return false
                val helper = OrientationHelper.createVerticalHelper(layoutManager)
                val distance = layoutManager.distanceTo(target, helper, itemCount) ?: return false
                if (distance != 0) recyclerView.scrollBy(0, distance)
                return true
            }

            /**
             * Jumps to [target]'s row by its edge, then lets a settle apply the fraction once the row
             * is laid out with its real height. Does nothing once [settlesLeft] runs out.
             */
            private fun jumpAndSettle(
                target: DragTarget,
                layoutManager: LinearLayoutManager,
                itemCount: Int,
            ) {
                if (settlesLeft-- <= 0) return
                layoutManager.jumpTo(target, itemCount)
                settleAfterLayout()
            }

            /**
             * Runs [placeRows] again just before the next frame is drawn, once its layout is done, so
             * that frame shows the corrected rows and thumb rather than the jump.
             */
            private fun settleAfterLayout() {
                val listener =
                    object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            if (recyclerView.isLayoutRequested) return true
                            placeRows(isSettling = true)
                            relayoutThumb()
                            return true
                        }
                    }
                settle = listener
                recyclerView.viewTreeObserver.addOnPreDrawListener(listener)
            }

            private fun cancelSettle() {
                val listener = settle ?: return
                settle = null
                recyclerView.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
            }

            /**
             * Maps [fingerProportion] to where the list should be.
             *
             * Dragging down from the pivot moves the list's bottom edge from [DragPivot.bottomRow] to
             * the end in step with the finger, and dragging up moves its top edge from
             * [DragPivot.topRow] to the start. The track ends always mean the list ends. For a given
             * pivot it depends only on the finger, so any path to the same point shows the same rows.
             *
             * @return null without a pivot, except at the track ends
             */
            private fun target(itemCount: Int): DragTarget? {
                if (fingerProportion >= 1f) return DragTarget(itemCount.toDouble(), isBottomRow = true)
                if (fingerProportion <= 0f) return DragTarget(0.0, isBottomRow = false)
                val pivot = pivot ?: return null
                return when {
                    fingerProportion > pivot.proportion -> {
                        val rowsLeft = (itemCount - pivot.bottomRow).coerceAtLeast(0.0)
                        val bottomRow = pivot.bottomRow + rowsLeft * (fingerProportion - pivot.proportion) / (1 - pivot.proportion)
                        DragTarget(bottomRow, isBottomRow = true)
                    }
                    fingerProportion < pivot.proportion -> {
                        val topRow = pivot.topRow * fingerProportion / pivot.proportion
                        DragTarget(topRow, isBottomRow = false)
                    }
                    else -> DragTarget(pivot.topRow, isBottomRow = false)
                }
            }

            /**
             * Takes a pivot at [proportion] from the rows at the list's top and bottom edges.
             *
             * @param snapToEnds use 0 or 1 if the list is already at that end. Only for a press, where
             * the grab offset is then measured from the pivot, unless the thumb is drawn at a track end.
             * @return null if no laid-out row covers an edge
             */
            private fun pivotAt(
                proportion: Float,
                snapToEnds: Boolean,
            ): DragPivot? {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
                val itemCount = recyclerView.adapter?.itemCount ?: return null
                val helper = OrientationHelper.createVerticalHelper(layoutManager)
                val topRow = layoutManager.topRowIndex(helper) ?: return null
                val bottomRow = layoutManager.bottomRowIndex(helper, itemCount) ?: return null
                val pivotProportion =
                    when {
                        snapToEnds && topRow <= 0.0 -> 0f
                        snapToEnds && bottomRow >= itemCount -> 1f
                        else -> proportion.coerceIn(MIN_PIVOT_PROPORTION, 1 - MIN_PIVOT_PROPORTION)
                    }
                return DragPivot(topRow, bottomRow, pivotProportion, itemCount)
            }

            /** True if the rows on screen are stale: a layout or an adapter update is still to come. */
            private fun hasPendingLayout() = recyclerView.isLayoutRequested || recyclerView.hasPendingAdapterUpdates()

            /**
             * Pixels to scroll so [target]'s point sits on its edge of the list.
             *
             * @return null if the target row is not laid out
             */
            private fun LinearLayoutManager.distanceTo(
                target: DragTarget,
                helper: OrientationHelper,
                itemCount: Int,
            ): Int? {
                val (position, fraction) = target.rowAndFraction(itemCount)
                val row = findViewByPosition(position) ?: return null
                val point = helper.getDecoratedStart(row) + (fraction * helper.getDecoratedMeasurement(row)).roundToInt()
                return point - if (target.isBottomRow) helper.endAfterPadding else helper.startAfterPadding
            }

            /**
             * Brings [target]'s row on screen by its own edge, without the fraction. For a bottom edge
             * target, scrollToPosition lines up a row below the screen with the bottom edge.
             */
            private fun LinearLayoutManager.jumpTo(
                target: DragTarget,
                itemCount: Int,
            ) {
                val (position, _) = target.rowAndFraction(itemCount)
                if (target.isBottomRow) scrollToPosition(position) else scrollToPositionWithOffset(position, 0)
            }

            /** Row position at the list's top edge, or null if no laid-out row covers it. */
            private fun LinearLayoutManager.topRowIndex(helper: OrientationHelper): Double? {
                val y = helper.startAfterPadding
                val row =
                    laidOutChildren().firstOrNull { helper.getDecoratedStart(it) <= y && y < helper.getDecoratedEnd(it) } ?: return null
                return getPosition(row) + (y - helper.getDecoratedStart(row)).toDouble() / helper.getDecoratedMeasurement(row)
            }

            /**
             * Row position at the list's bottom edge, or null if no laid-out row covers it. A row
             * ending exactly on the edge counts, as the row below it is not laid out. A last row
             * ending above the edge gives itemCount.
             */
            private fun LinearLayoutManager.bottomRowIndex(
                helper: OrientationHelper,
                itemCount: Int,
            ): Double? {
                val y = helper.endAfterPadding
                val row = laidOutChildren().firstOrNull { helper.getDecoratedStart(it) < y && y <= helper.getDecoratedEnd(it) }
                if (row != null) {
                    return getPosition(row) + (y - helper.getDecoratedStart(row)).toDouble() / helper.getDecoratedMeasurement(row)
                }
                val lastRow = findViewByPosition(itemCount - 1) ?: return null
                return if (helper.getDecoratedEnd(lastRow) <= y) itemCount.toDouble() else null
            }

            private fun LinearLayoutManager.laidOutChildren(): List<View> = (0 until childCount).mapNotNull { getChildAt(it) }

            /**
             * The rows at the list's edges when a drag's mapping was anchored, and the thumb
             * proportion they belong to.
             *
             * Row positions are fractional adapter positions: 12.25 is a quarter of the way down row
             * 12, and itemCount is the bottom of the last row.
             *
             * @property topRow row position at the list's top edge, inside the padding
             * @property bottomRow row position at the list's bottom edge, inside the padding
             * @property proportion share of the thumb's travel, from 0 to 1
             * @property itemCount the adapter's item count when it was taken
             */
            private class DragPivot(
                val topRow: Double,
                val bottomRow: Double,
                val proportion: Float,
                val itemCount: Int,
            )

            /**
             * Where the list should be for the finger.
             *
             * @property row fractional row position, as in [DragPivot]
             * @property isBottomRow true if [row] goes on the list's bottom edge, false for the top edge
             */
            private class DragTarget(
                val row: Double,
                val isBottomRow: Boolean,
            ) {
                /**
                 * Splits [row] into an adapter position and the share of that row's height, from 0
                 * to 1. A bottom edge on a row boundary uses the end of the row above, which is on
                 * screen.
                 */
                fun rowAndFraction(itemCount: Int): Pair<Int, Double> {
                    if (row >= itemCount) return Pair(itemCount - 1, 1.0)
                    val (wholeRows, fraction) = row.coerceAtLeast(0.0).wholeAndFraction()
                    if (isBottomRow && fraction == 0.0 && wholeRows > 0) return Pair(wholeRows.toInt() - 1, 1.0)
                    return Pair(wholeRows.toInt(), fraction)
                }
            }

            private companion object {
                /**
                 * Jumps one placement may make before it waits for the next move. A jump lays its row
                 * out, so one is normally enough and the rest is headroom. The cap stops a row that
                 * never gets laid out from re-laying out the list on every frame.
                 */
                const val MAX_SETTLES = 3

                /**
                 * Keeps a pivot just off a track end the list has not reached, so the finger still has
                 * track left to scroll to that end.
                 */
                const val MIN_PIVOT_PROPORTION = 0.000001f
            }
        }

        companion object {
            private val DEFAULT_AUTO_HIDE_DELAY = 1500.milliseconds
        }
    }

fun RecyclerView.attachFastScroller(
    @IdRes id: Int,
) = (parent as ViewGroup)
    .findViewById<RecyclerFastScroller>(id)
    .attachRecyclerView(this)
