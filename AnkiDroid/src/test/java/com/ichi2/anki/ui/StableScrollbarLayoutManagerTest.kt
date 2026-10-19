// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class StableScrollbarLayoutManagerTest {
    @Test
    fun `thumb keeps its size while rows of different heights scroll past`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        val sizes = list.scrollToEndCollecting { list.thumb.height }

        assertThat(sizes, everyItem(equalTo(initialSize)))
    }

    @Test
    fun `thumb reaches the end of the track at the end of the list`() {
        val list = ScrollbarTestList(shortThenTallRows)
        assertThat(list.thumb.top, equalTo(0))

        val bottoms = list.scrollToEndCollecting { list.thumb.bottom }

        assertThat(bottoms.last(), equalTo(list.trackHeight))
    }

    @Test
    fun `thumb never moves against the scroll`() {
        val list = ScrollbarTestList(shortThenTallRows)

        val topsScrollingDown = list.scrollToEndCollecting { list.thumb.top }
        val topsScrollingUp = list.scrollToStartCollecting { list.thumb.top }

        assertThat(topsScrollingDown, equalTo(topsScrollingDown.sorted()))
        assertThat(topsScrollingUp, equalTo(topsScrollingUp.sortedDescending()))
        assertThat(topsScrollingUp.last(), equalTo(0))
    }

    @Test
    fun `thumb carries on from where the list jumped to`() {
        val list = ScrollbarTestList(shortThenTallRows)
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        list.jumpToLastRow()
        list.scrollBy(-SHORT_ROW)

        assertThat(list.thumb.bottom.toDouble(), closeTo(list.trackHeight.toDouble(), list.trackHeight * 0.05))
    }

    @Test
    fun `refreshing rows without changing their heights keeps the thumb size`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        list.refreshRows()

        assertThat(list.thumb.height, equalTo(initialSize))
    }

    @Test
    fun `scrolling right after a row off screen changes keeps the thumb size`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        list.notifyRowChanged(shortThenTallRows.lastIndex)
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        assertThat(list.thumb.height, equalTo(initialSize))
    }

    @Test
    fun `rows changing height resize the thumb`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        list.refreshRows(List(shortThenTallRows.size) { SHORT_ROW / 2 })

        assertThat(list.thumb.height, not(equalTo(initialSize)))
    }

    @Test
    fun `a different number of rows resizes the thumb`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        list.refreshRows(shortThenTallRows.take(40))

        assertThat(list.thumb.height, not(equalTo(initialSize)))
    }
}

private const val SHORT_ROW = 100
private const val TALL_ROW = 400
private const val DISTANCE_TO_TALL_ROWS = 30 * SHORT_ROW + 5 * TALL_ROW

private val shortThenTallRows = List(30) { SHORT_ROW } + List(30) { TALL_ROW } + List(30) { SHORT_ROW }

private class ScrollbarTestList(
    rowHeights: List<Int>,
) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val adapter = FixedHeightRowsAdapter(rowHeights)
    private val recyclerView =
        RecyclerView(context).also {
            it.layoutManager = StableScrollbarLayoutManager(context)
            it.adapter = adapter
        }
    private val scroller = RecyclerFastScroller(context)
    private val root =
        FrameLayout(context).apply {
            addView(recyclerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(scroller, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, Gravity.END))
        }

    val thumb: View get() = scroller.handle
    val trackHeight: Int get() = scroller.height - scroller.handleBottomInset

    init {
        scroller.attachRecyclerView(recyclerView)
        layout()
    }

    fun scrollBy(dy: Int) {
        recyclerView.scrollBy(0, dy)
        layout()
    }

    fun <T> scrollToEndCollecting(value: () -> T): List<T> {
        val values = mutableListOf<T>()
        while (recyclerView.canScrollVertically(1)) {
            scrollBy(SCROLL_STEP)
            values.add(value())
        }
        return values
    }

    fun <T> scrollToStartCollecting(value: () -> T): List<T> {
        val values = mutableListOf<T>()
        while (recyclerView.canScrollVertically(-1)) {
            scrollBy(-SCROLL_STEP)
            values.add(value())
        }
        return values
    }

    fun jumpToLastRow() {
        recyclerView.scrollToPosition(adapter.itemCount - 1)
        layout()
    }

    fun notifyRowChanged(position: Int) = adapter.notifyItemChanged(position)

    fun refreshRows(rowHeights: List<Int> = adapter.rowHeights) {
        adapter.rowHeights = rowHeights
        adapter.notifyDataSetChanged()
        layout()
    }

    private fun layout() {
        root.measure(
            MeasureSpec.makeMeasureSpec(WIDTH, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(HEIGHT, MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, WIDTH, HEIGHT)
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 2000
        const val SCROLL_STEP = 50
    }
}

private class FixedHeightRowsAdapter(
    var rowHeights: List<Int>,
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
