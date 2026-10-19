// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class RecyclerFastScrollerTest {
    @Test
    fun `scroller hides after a thumb drag`() {
        val list = FastScrollerTestList(evenRows(200))
        list.press()
        list.moveTo(trackProportion = 0.5f)
        list.release()

        list.waitPastAutoHide()

        assertThat(list.isThumbPressed, equalTo(false))
        assertThat(list.isScrollerHidden, equalTo(true))
    }

    @Test
    fun `scroller hides after the thumb is held still past the hide delay`() {
        val list = FastScrollerTestList(evenRows(200))
        list.press()
        list.moveTo(trackProportion = 0.5f)
        list.waitPastAutoHide()
        list.release()

        list.waitPastAutoHide()

        assertThat(list.isScrollerHidden, equalTo(true))
    }

    @Test
    fun `slow drag moves the list in one direction only`() {
        val list = FastScrollerTestList(evenRows(200))

        val positions = list.slowDragToEndCollecting { list.scrollY }

        assertThat(positions.firstStepBack(), nullValue())
    }

    @Test
    fun `slow drag over rows of different heights moves the list in one direction only`() {
        val list = FastScrollerTestList(evenRows(10) + List(6) { 4 * ROW_HEIGHT } + evenRows(10))

        val positions = list.slowDragToEndCollecting { list.scrollY }

        assertThat(positions.firstStepBack(), nullValue())
    }

    @Test
    fun `thumb stays under the finger on a short list`() {
        val list = FastScrollerTestList(evenRows(40))

        list.press()
        list.moveTo(trackProportion = 0.25f)
        list.holdStill()

        assertThat(list.thumbCenter, closeTo(list.fingerY.toDouble(), 1.0))
    }

    @Test
    fun `list reaches its last row only at the end of the track`() {
        val list = FastScrollerTestList(evenRows(200))
        list.press()

        list.moveTo(trackProportion = 0.95f)
        list.holdStill()
        assertThat(list.canScrollDown, equalTo(true))

        list.moveTo(trackProportion = 1f)
        list.holdStill()
        assertThat(list.canScrollDown, equalTo(false))
    }

    @Test
    fun `releasing after a jump to an offscreen row keeps the list still`() {
        val list = FastScrollerTestList(evenRows(200))
        list.press()
        list.moveTo(trackProportion = 0.251f)
        list.holdStill()
        val scrollBeforeRelease = list.scrollY
        assertThat(scrollBeforeRelease, greaterThan(0))

        list.release()
        list.holdStill()

        assertThat(list.scrollY, equalTo(scrollBeforeRelease))
    }

    @Test
    fun `slow upward drag never moves the list downward`() {
        val list = FastScrollerTestList(evenRows(200))

        val positions = list.slowDragToStartCollecting { list.scrollY }

        assertThat(positions.first(), greaterThan(positions.last()))
        assertThat(positions.last(), equalTo(0))
        assertThat(positions.zipWithNext().firstOrNull { (before, after) -> after > before }, nullValue())
    }

    @Test
    fun `last pixel of a drag over taller final rows does not jump a screen`() {
        val list = FastScrollerTestList(evenRows(100) + List(20) { 4 * ROW_HEIGHT })
        // Keep the finger coordinates fixed even if the thumb resizes during the drag.
        val endOfTrack = list.viewportHeight - list.thumbCenter.toFloat()
        list.press()
        list.moveFingerTo(endOfTrack - 2f)
        list.holdStill()
        list.moveFingerTo(endOfTrack - 1f)
        list.holdStill()
        val scrollBeforeLastPixel = list.scrollY

        list.moveFingerTo(endOfTrack)
        list.holdStill()

        assertThat(list.scrollY - scrollBeforeLastPixel, lessThan(list.viewportHeight))
    }

    @Test
    fun `shorter final rows do not reach the bottom before the end of the track`() {
        val list = FastScrollerTestList(List(20) { 4 * ROW_HEIGHT } + evenRows(100))
        list.press()

        list.moveTo(trackProportion = 0.95f)
        list.holdStill()

        assertThat(list.canScrollDown, equalTo(true))
    }

    @Test
    fun `end of track exposes the bottom of a final row taller than the viewport`() {
        val list = FastScrollerTestList(evenRows(100) + listOf(4000))
        val endOfTrack = list.viewportHeight - list.thumbCenter.toFloat()
        list.press()

        list.moveFingerTo(endOfTrack)
        list.holdStill()

        assertThat(list.canScrollDown, equalTo(false))
    }

    @Test
    fun `slow downward drag over short rows between tall rows never moves the list upward`() {
        val list = FastScrollerTestList(List(2) { 4 * ROW_HEIGHT } + evenRows(4) + List(2) { 4 * ROW_HEIGHT })

        val positions = list.slowDragToEndCollecting { list.scrollY }

        assertThat(positions.first(), lessThan(positions.last()))
        assertThat(list.canScrollDown, equalTo(false))
        assertThat(positions.firstStepBack(), nullValue())
    }

    @Test
    fun `slow upward drag over short rows between tall rows never moves the list downward`() {
        val list = FastScrollerTestList(List(2) { 4 * ROW_HEIGHT } + evenRows(4) + List(2) { 4 * ROW_HEIGHT })

        val positions = list.slowDragToStartCollecting { list.scrollY }

        assertThat(positions.first(), greaterThan(positions.last()))
        assertThat(positions.last(), equalTo(0))
        assertThat(positions.zipWithNext().firstOrNull { (before, after) -> after > before }, nullValue())
    }

    @Test
    fun `small downward move after jumping to a shorter row never moves the list upward`() {
        val list = FastScrollerTestList(List(20) { 4 * ROW_HEIGHT } + evenRows(100))
        // Keep the finger coordinates fixed even if the thumb resizes after the jump.
        val startOfTrack = list.thumbCenter.toFloat()
        val endOfTrack = list.viewportHeight - startOfTrack
        list.press()
        list.moveFingerTo(startOfTrack + 0.505f * (endOfTrack - startOfTrack))
        list.holdStill()
        val scrollBeforeMove = list.scrollY
        assertThat(scrollBeforeMove, greaterThan(0))

        list.moveFingerTo(list.fingerY + 2f)
        list.holdStill()

        assertThat(list.scrollY, greaterThanOrEqualTo(scrollBeforeMove))
    }

    @Test
    fun `two moves before layout reach the same position as the latest move alone`() {
        val singleMove = FastScrollerTestList(evenRows(200))
        singleMove.press()
        singleMove.moveTo(trackProportion = 0.51f)
        singleMove.holdStill()

        val batchedMoves = FastScrollerTestList(evenRows(200))
        batchedMoves.press()
        batchedMoves.advanceFrameAfterTouch = false
        batchedMoves.moveTo(trackProportion = 0.50f)
        batchedMoves.moveTo(trackProportion = 0.51f)
        batchedMoves.holdStill()

        assertThat(batchedMoves.scrollY, equalTo(singleMove.scrollY))
    }

    @Test
    fun `jump from expanded rows to short final rows leaves track travel`() {
        val list = FastScrollerTestList(List(20) { 4000 } + evenRows(100))
        list.press()
        list.moveTo(trackProportion = 0.905f)
        list.holdStill()

        assertThat(list.canScrollDown, equalTo(true))
    }
}

private const val ROW_HEIGHT = 150

private fun evenRows(count: Int) = List(count) { ROW_HEIGHT }

private fun List<Int>.firstStepBack() = zipWithNext().firstOrNull { (before, after) -> after < before }

private class FastScrollerTestList(
    rowHeights: List<Int>,
) {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val recyclerView =
        RecyclerView(activity).also {
            it.layoutManager = LinearLayoutManager(activity)
            it.adapter = FixedHeightRowsAdapter(rowHeights)
        }
    private val scroller = RecyclerFastScroller(activity)
    private val root =
        RelativeLayout(activity).apply {
            addView(recyclerView, RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(
                scroller,
                RelativeLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply { addRule(RelativeLayout.ALIGN_PARENT_END) },
            )
        }
    private val rowTops = rowHeights.runningFold(0, Int::plus)
    private val downTime = SystemClock.uptimeMillis()

    var fingerY = 0f
        private set

    val isThumbPressed: Boolean get() = scroller.handle.isPressed

    val isScrollerHidden: Boolean get() = scroller.translationX == scroller.hiddenTranslationX.toFloat()

    val scrollY: Int
        get() {
            val firstRow = recyclerView.getChildAt(0)
            return rowTops[recyclerView.getChildAdapterPosition(firstRow)] - firstRow.top
        }

    val canScrollDown: Boolean get() = recyclerView.canScrollVertically(1)

    val viewportHeight: Int get() = recyclerView.height

    val thumbCenter: Double get() = (scroller.handle.top + scroller.handle.bottom) / 2.0

    init {
        scroller.attachRecyclerView(recyclerView)
        activity.setContentView(root, ViewGroup.LayoutParams(WIDTH, HEIGHT))
        showNextFrame()
    }

    fun press() = touch(MotionEvent.ACTION_DOWN, (scroller.handle.top + scroller.handle.bottom) / 2f)

    fun moveTo(trackProportion: Float) {
        val thumbHeight = scroller.handle.height
        touch(MotionEvent.ACTION_MOVE, thumbHeight / 2f + trackProportion * (HEIGHT - thumbHeight))
    }

    fun moveFingerTo(y: Float) = touch(MotionEvent.ACTION_MOVE, y)

    fun release() = touch(MotionEvent.ACTION_UP, fingerY)

    fun waitPastAutoHide() = shadowOf(Looper.getMainLooper()).idleFor(AUTO_HIDE_WAIT)

    fun holdStill() = shadowOf(Looper.getMainLooper()).idleFor(SLOW_DRAG_PAUSE)

    fun <T> slowDragToEndCollecting(value: () -> T): List<T> {
        val values = mutableListOf<T>()
        press()
        while (fingerY < HEIGHT) {
            touch(MotionEvent.ACTION_MOVE, fingerY + SLOW_DRAG_STEP)
            values.add(value())
            holdStill()
            values.add(value())
        }
        release()
        values.add(value())
        return values
    }

    fun <T> slowDragToStartCollecting(value: () -> T): List<T> {
        press()
        moveTo(trackProportion = 1f)
        holdStill()
        val values = mutableListOf(value())
        while (fingerY > 0f) {
            touch(MotionEvent.ACTION_MOVE, (fingerY - SLOW_DRAG_STEP).coerceAtLeast(0f))
            values.add(value())
            holdStill()
            values.add(value())
        }
        release()
        values.add(value())
        return values
    }

    var advanceFrameAfterTouch = true

    private fun touch(
        action: Int,
        y: Float,
    ) {
        fingerY = y
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, scroller.width / 2f, y, 0)
        scroller.dispatchTouchEvent(event)
        event.recycle()
        if (advanceFrameAfterTouch) showNextFrame()
    }

    private fun showNextFrame() = shadowOf(Looper.getMainLooper()).idleFor(FRAME_INTERVAL)

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 2000
        const val SLOW_DRAG_STEP = 3
        val FRAME_INTERVAL: Duration = Duration.ofMillis(16)
        val SLOW_DRAG_PAUSE: Duration = Duration.ofMillis(50)
        val AUTO_HIDE_WAIT: Duration = Duration.ofSeconds(2)
    }
}

private class FixedHeightRowsAdapter(
    private val rowHeights: List<Int>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = object : RecyclerView.ViewHolder(View(parent.context)) {}

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        holder.itemView.layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, rowHeights[position])
    }

    override fun getItemCount() = rowHeights.size
}
