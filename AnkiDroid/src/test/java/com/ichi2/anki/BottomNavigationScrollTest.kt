// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.withBooleanPreference
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationScrollTest : RobolectricTest() {
    @Test
    fun `scrolling down hides the bar and scrolling up restores it`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            val behavior = HideViewOnScrollBehavior.from(nav)
            val deckList = deckPicker.deckPickerBinding.decks

            assertThat(behavior.isScrolledIn, equalTo(true))
            deckPicker.scrollFrom(deckList, 40)
            assertThat(behavior.isScrolledOut, equalTo(true))
            deckPicker.scrollFrom(deckList, -40)
            assertThat(behavior.isScrolledIn, equalTo(true))
        }

    @Test
    fun `switching tabs restores a hidden bar`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            val behavior = HideViewOnScrollBehavior.from(nav)

            deckPicker.scrollFrom(deckPicker.deckPickerBinding.decks, 40)
            assertThat(behavior.isScrolledOut, equalTo(true))

            nav.selectedItemId = NavigationItem.BROWSER.id
            advanceRobolectricLooper()
            assertThat(behavior.isScrolledIn, equalTo(true))
        }

    @Test
    fun `scrolling up in the browser restores the hidden bar`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.BROWSER.id
            advanceRobolectricLooper()
            val list = deckPicker.findViewById<RecyclerView>(R.id.card_browser_list)
            list.adapter =
                object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun getItemCount() = 80

                    override fun onCreateViewHolder(
                        parent: ViewGroup,
                        viewType: Int,
                    ) = object : RecyclerView.ViewHolder(
                        TextView(parent.context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100)
                        },
                    ) {}

                    override fun onBindViewHolder(
                        holder: RecyclerView.ViewHolder,
                        position: Int,
                    ) = Unit
                }
            list.scrollToPosition(40)
            deckPicker.scrollFrom(list, 40)
            assertThat(HideViewOnScrollBehavior.from(nav).isScrolledOut, equalTo(true))

            list.scrollBy(0, -100)
            assertThat(HideViewOnScrollBehavior.from(nav).isScrolledIn, equalTo(true))
        }

    @Test
    fun `scrolling More content hides the bar and scrolling back restores it`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            val scrollView = deckPicker.findViewById<View>(R.id.more_scroll_view)
            val behavior = HideViewOnScrollBehavior.from(nav)

            deckPicker.scrollFrom(scrollView, 40)
            assertThat(behavior.isScrolledOut, equalTo(true))
            deckPicker.scrollFrom(scrollView, -40)
            assertThat(behavior.isScrolledIn, equalTo(true))
        }

    /** The hidden bar gives More extra height, which pulls a bottom scroll position back up */
    @Test
    fun `More stays at the bottom with the bar hidden after the content grows`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            val scrollView = deckPicker.findViewById<ViewGroup>(R.id.more_scroll_view)
            val behavior = HideViewOnScrollBehavior.from(nav)
            scrollView.scrollTo(0, scrollView.getChildAt(0).height)

            deckPicker.scrollFrom(scrollView, 40)
            assertThat(behavior.isScrolledOut, equalTo(true))
            // the container grows into the bar's space
            deckPicker.binding.root.requestLayout()
            advanceRobolectricLooper()

            assertThat("the bar must not come back without an upward scroll", behavior.isScrolledOut, equalTo(true))
        }

    /** Content which fits once the bar is hidden cannot scroll, so only the gesture can restore the bar */
    @Test
    fun `dragging down restores the hidden bar when the content cannot scroll`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            val behavior = HideViewOnScrollBehavior.from(nav)
            behavior.slideOut(nav)
            assertThat(behavior.isScrolledOut, equalTo(true))

            // More is at the top: dragging the finger down cannot scroll it any further
            deckPicker.dragFinger(fromY = 200f, toY = 400f)

            assertThat(behavior.isScrolledIn, equalTo(true))
        }

    @Test
    fun `scrolling Statistics content hides the bar`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val webView = deckPicker.findViewById<ViewGroup>(R.id.webview_layout).getChildAt(0) as WebView

            val behavior = HideViewOnScrollBehavior.from(nav)
            webView.scrollTo(0, 100)
            assertThat(behavior.isScrolledOut, equalTo(true))
            webView.scrollTo(0, 0)
            assertThat(behavior.isScrolledIn, equalTo(true))
        }

    /** The hidden bar makes the page taller, so the WebView pulls a bottom scroll position back up */
    @Test
    fun `Statistics ignores the scroll caused by growing into the bar's space`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val webView = deckPicker.findViewById<ViewGroup>(R.id.webview_layout).getChildAt(0) as WebView
            val behavior = HideViewOnScrollBehavior.from(nav)
            webView.scrollTo(0, 100)
            assertThat(behavior.isScrolledOut, equalTo(true))

            val webViewLayout = deckPicker.findViewById<ViewGroup>(R.id.webview_layout)
            webViewLayout.layout(webViewLayout.left, webViewLayout.top, webViewLayout.right, webViewLayout.bottom + 150)
            webView.scrollTo(0, 50)

            assertThat("the bar must not come back without an upward scroll", behavior.isScrolledOut, equalTo(true))
        }

    @Test
    fun `Studied summary sits above the visible nav bar`() =
        withBottomNavigation { deckPicker ->
            val summary = deckPicker.deckPickerBinding.reviewSummaryTextView
            val nav = deckPicker.bottomNav
            deckPicker.deckPickerBinding.deckPickerContent.visibility = View.VISIBLE
            summary.text = "Studied 10 cards today"
            summary.visibility = View.VISIBLE
            deckPicker.binding.root.requestLayout()
            advanceRobolectricLooper()

            assertThat(summary.height > 0, equalTo(true))
            assertThat(summary.contentBottomOnScreen, lessThanOrEqualTo(nav.topOnScreen))
        }

    @Test
    fun `Studied summary stays visible above the system navigation bar when the bar hides`() =
        withBottomNavigation { deckPicker ->
            val summary = deckPicker.deckPickerBinding.reviewSummaryTextView
            val nav = deckPicker.bottomNav
            deckPicker.deckPickerBinding.deckPickerContent.visibility = View.VISIBLE
            summary.text = "Studied 10 cards today"
            summary.visibility = View.VISIBLE
            deckPicker.binding.root.requestLayout()
            advanceRobolectricLooper()

            assertThat(summary.height > 0, equalTo(true))
            deckPicker.scrollFrom(deckPicker.deckPickerBinding.decks, 40)
            advanceRobolectricLooper()
            // Robolectric re-dispatches zero insets when the bar's state changes: apply a real nav bar after
            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            deckPicker.binding.root.requestLayout()
            advanceRobolectricLooper()

            assertThat(HideViewOnScrollBehavior.from(nav).isScrolledOut, equalTo(true))
            assertThat(summary.alpha, equalTo(1f))
            // back at its resting position, which pads it above the system navigation bar
            assertThat(summary.translationY, equalTo(0f))
            assertThat(summary.paddingBottom, equalTo(48.dp.toPx(deckPicker)))

            deckPicker.scrollFrom(deckPicker.deckPickerBinding.decks, -40)
            advanceRobolectricLooper()
            assertThat(summary.alpha, equalTo(1f))
            assertThat(summary.contentBottomOnScreen, lessThanOrEqualTo(nav.topOnScreen))
        }

    @Test
    fun `deck list fade stays anchored to the summary while the bottom bar is hidden`() =
        withBottomNavigation { deckPicker ->
            val deckList = deckPicker.deckPickerBinding.decks
            val fade = deckPicker.deckPickerBinding.decksFadeWrapper
            val summary = deckPicker.deckPickerBinding.reviewSummaryTextView

            assertThat(fade.anchorView, equalTo(summary))
            deckPicker.scrollFrom(deckList, 40)
            assertThat(fade.anchorView, equalTo(summary))
            deckPicker.scrollFrom(deckList, -40)
            assertThat(fade.anchorView, equalTo(summary))
        }

    @Test
    fun `hidden bar releases space for fragment content`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            val container = deckPicker.binding.bottomNavFragmentContainer!!
            deckPicker.scrollFrom(deckPicker.deckPickerBinding.decks, 40)

            assertThat(HideViewOnScrollBehavior.from(nav).isScrolledOut, equalTo(true))
            assertThat((container.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin < nav.height, equalTo(true))
        }

    private fun withBottomNavigation(test: (DeckPicker) -> Unit) =
        withBooleanPreference(R.string.dev_bottom_nav_key, true) {
            setIntroductionSlidesShown(true)
            test(startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get())
        }

    private fun DeckPicker.scrollFrom(
        source: View,
        dy: Int,
    ) {
        val root = findViewById<CoordinatorLayout>(R.id.root_layout)
        if (root.onStartNestedScroll(source, source, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)) {
            root.onNestedScroll(source, 0, dy, 0, 0, ViewCompat.TYPE_TOUCH)
            root.onStopNestedScroll(source, ViewCompat.TYPE_TOUCH)
        }
    }

    private fun DeckPicker.dragFinger(
        fromY: Float,
        toY: Float,
    ) {
        val time = SystemClock.uptimeMillis()
        val steps = listOf(MotionEvent.ACTION_DOWN to fromY, MotionEvent.ACTION_MOVE to toY, MotionEvent.ACTION_UP to toY)
        for ((action, y) in steps) {
            val event = MotionEvent.obtain(time, time, action, 100f, y, 0)
            dispatchTouchEvent(event)
            event.recycle()
        }
        advanceRobolectricLooper()
    }

    private val View.topOnScreen: Int
        get() = IntArray(2).also { getLocationOnScreen(it) }[1]

    private val View.contentBottomOnScreen: Int
        get() = topOnScreen + height - paddingBottom

    private val DeckPicker.bottomNav: BottomNavigationView
        get() = binding.bottomNavigation!!
}
