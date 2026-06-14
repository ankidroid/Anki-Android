// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import android.view.View.MeasureSpec
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge-to-edge inset handling for [CardBrowser].
 *
 * The list draws edge-to-edge under the navigation bar and the fast
 * scroller's track is full-height/edge-to-edge. Only the handle is held inside the safe area
 * (clearing the navigation bar and rounded display corners), so at full scroll the handle's bottom
 * lines up with the last card's resting position.
 */
@RunWith(AndroidJUnit4::class)
class CardBrowserInsetsTest : RobolectricTest() {
    @Test
    fun `track is full-height but the handle is inset to the last card's resting line`() =
        withCardBrowser(noteCount = 1) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            // `clipToPadding=false` lets content scroll under the bar, but the bottom padding is the
            // resting position of the last card: a navigation bar's height above the parent's bottom
            assertThat(
                "last card rests a navigation bar's height above the parent's bottom",
                browser.cardList.restingContentBottomToParent,
                equalTo(navBarBottom),
            )
            // the track stays full-height/edge-to-edge: flush with the parent bottom
            assertThat(
                "fast scroller track spans to the parent's bottom",
                browser.fastScroller.distanceToParentBottom,
                equalTo(0),
            )
            //  but the handle is inset to the same line the last card rests on
            assertThat(
                "fast scroller handle is inset to the last card's resting line",
                browser.fastScroller.handleBottomInset,
                equalTo(browser.cardList.restingContentBottomToParent),
            )
        }

    @Test
    fun `track is full-height while scrollable, then trimmed to the inset at the bottom`() =
        withCardBrowser(noteCount = 50) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            val scroller = browser.fastScroller
            val track = scroller.bar
            // the list must be scrollable for this scenario to be meaningful
            assertThat("list is scrollable", browser.cardList.canScrollVertically(1), equalTo(true))

            // not at the bottom: the track spans the full edge-to-edge height
            assertThat(
                "track is full-height while there is more to scroll",
                track.bottom,
                equalTo(scroller.height),
            )

            // scroll to the end and re-lay-out so the fast scroller re-positions
            browser.cardList.scrollToPosition(49)
            browser.layoutForTest()

            // at the bottom: the track is trimmed by the inset, aligning with the handle & last row
            assertThat("list is fully scrolled", browser.cardList.canScrollVertically(1), equalTo(false))
            assertThat(
                "track is trimmed to the handle inset once fully scrolled",
                track.bottom,
                equalTo(scroller.height - navBarBottom),
            )
        }

    @Test
    fun `track bottom follows the last row's bottom once it is on screen`() =
        withCardBrowser(noteCount = 50) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            val scroller = browser.fastScroller
            val track = scroller.bar
            val list = browser.cardList

            // scroll to the end, then back up by part of the inset so the last row's bottom sits
            // between its resting line and the bottom of the screen
            list.scrollToPosition(49)
            browser.layoutForTest()
            list.scrollBy(0, -navBarBottom / 2)
            browser.layoutForTest()

            val layoutManager = list.layoutManager!!
            val lastRowBottom = layoutManager.getDecoratedBottom(layoutManager.findViewByPosition(49)!!)
            assertThat(
                "the last row's bottom is on screen, below its resting line",
                lastRowBottom,
                allOf(greaterThan(scroller.height - navBarBottom), lessThan(scroller.height)),
            )
            assertThat("the track's bottom matches the last row's bottom", track.bottom, equalTo(lastRowBottom))
        }

    @Test
    fun `handle, track and last row rest on the same line when fully scrolled`() =
        withCardBrowser(noteCount = 50) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            val scroller = browser.fastScroller
            val track = scroller.bar
            val handle = scroller.handle
            val list = browser.cardList

            list.scrollToPosition(49)
            browser.layoutForTest()
            assertThat("list is fully scrolled", list.canScrollVertically(1), equalTo(false))

            val layoutManager = list.layoutManager!!
            val lastRowBottom = layoutManager.getDecoratedBottom(layoutManager.findViewByPosition(49)!!)
            val restingLine = scroller.height - navBarBottom
            assertThat("the last row rests above the safe area", lastRowBottom, equalTo(restingLine))
            assertThat("the track's bottom rests on the same line", track.bottom, equalTo(restingLine))
            assertThat("the handle's bottom rests on the same line", handle.bottom, equalTo(restingLine))
        }

    @Test
    fun `rounded display corners are cleared when larger than the navigation bar`() =
        withCardBrowser(noteCount = 1) { browser ->
            val cornerRadius = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)
            browser.layoutForTest()

            assertThat(
                "the last card rests above the rounded corners",
                browser.cardList.restingContentBottomToParent,
                equalTo(cornerRadius),
            )
            assertThat(
                "the handle is inset to the last card's resting line",
                browser.fastScroller.handleBottomInset,
                equalTo(cornerRadius),
            )
        }

    /** The gap, in pixels, between the bottom of this view and the bottom of its parent. */
    private val View.distanceToParentBottom: Int
        get() = (parent as View).height - bottom

    /**
     * The gap, in pixels, between the parent's bottom and where the last list item comes to rest.
     * The list fills its parent, so with `clipToPadding=false` this is just the bottom padding.
     */
    private val RecyclerView.restingContentBottomToParent: Int
        get() = distanceToParentBottom + paddingBottom

    private val CardBrowser.cardList: RecyclerView
        get() = findViewById(R.id.card_browser_list)

    private val CardBrowser.fastScroller: RecyclerFastScroller
        get() = findViewById(R.id.browser_scroller)

    /** Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero. */
    private fun CardBrowser.dispatchInsets(
        navBarBottom: Dp,
        bottomCornerRadius: Dp = 0.dp,
    ) {
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarBottom))
                    .apply {
                        val radius = bottomCornerRadius.toPx(targetContext)
                        if (radius > 0) {
                            // only the radius is read by the implementation; the center is unused
                            setRoundedCorner(
                                RoundedCornerCompat.POSITION_BOTTOM_LEFT,
                                RoundedCornerCompat(RoundedCornerCompat.POSITION_BOTTOM_LEFT, radius, radius, radius),
                            )
                        }
                    }.build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
    }

    /** Forces a measure/layout pass so view bounds (not just padding/insets) can be asserted. */
    private fun CardBrowser.layoutForTest() {
        val content = findViewById<View>(android.R.id.content)
        val width = 1080
        val height = 2400
        content.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        content.layout(0, 0, width, height)
    }
}
