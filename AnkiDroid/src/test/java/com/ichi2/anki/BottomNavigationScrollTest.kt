// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.FragmentMoreBinding
import com.ichi2.anki.databinding.PageStatisticsBinding
import com.ichi2.anki.pages.scrolledUpToBottom
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.withPref
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

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
            val scrollView = deckPicker.moreBinding.moreScrollView
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
            val scrollView = deckPicker.moreBinding.moreScrollView
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
    fun `upward reversal does not repeatedly restore the bar in More`() =
        withBottomNavigation { activity ->
            val nav = activity.bottomNav
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            val scroll = activity.moreBinding.moreScrollView
            scroll.scrollTo(0, 200)
            advanceRobolectricLooper()
            assertThat("More must be scrolled away from the top", scroll.scrollY > 70, equalTo(true))
            val behavior = HideViewOnScrollBehavior.from(nav)
            val states = mutableListOf<Int>()
            behavior.addOnScrollStateChangedListener { _, state -> states.add(state) }
            val origin = IntArray(2).also(scroll::getLocationInWindow)
            val start = SystemClock.uptimeMillis()

            fun touch(
                action: Int,
                offset: Float,
                delta: Long,
            ) {
                val event = MotionEvent.obtain(start, start + delta, action, 100f, origin[1] + offset, 0)
                activity.dispatchTouchEvent(event)
                event.recycle()
                advanceRobolectricLooper()
            }
            // Keep the finger down throughout the reversal. Vertical coordinates are relative to More.
            touch(MotionEvent.ACTION_DOWN, 60f, 0)
            touch(MotionEvent.ACTION_MOVE, 130f, 100)
            touch(MotionEvent.ACTION_MOVE, 110f, 200)
            assertThat("Reversing upwards must hide the bar", behavior.isScrolledOut, equalTo(true))
            // Continue upwards while still below the initial touch position.
            states.clear()
            touch(MotionEvent.ACTION_MOVE, 90f, 300)
            touch(MotionEvent.ACTION_UP, 90f, 400)
            assertThat(
                "Continuing upwards must not show the bar; observed states: $states",
                states.contains(HideViewOnScrollBehavior.STATE_SCROLLED_IN),
                equalTo(false),
            )
            assertThat(behavior.isScrolledOut, equalTo(true))
        }

    @Test
    fun `scrolling Statistics content hides the bar`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val webView = deckPicker.statisticsWebView

            val behavior = HideViewOnScrollBehavior.from(nav)
            webView.scrollTo(0, 100)
            assertThat(behavior.isScrolledOut, equalTo(true))
            webView.scrollTo(0, 0)
            assertThat(behavior.isScrolledIn, equalTo(true))
        }

    @Test
    fun `first upward Statistics scroll after layout restores the bar`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val webView = deckPicker.statisticsWebView
            val behavior = HideViewOnScrollBehavior.from(nav)

            webView.scrollTo(0, 1000)
            assertThat(behavior.isScrolledOut, equalTo(true))
            advanceRobolectricLooper()

            // Away from the bottom, resizing need not scroll the page. The next scroll is user input.
            webView.scrollTo(0, 950)
            assertThat("an upward non-touch scroll must restore the bar", behavior.isScrolledIn, equalTo(true))
        }

    /**
     * The hidden bar makes Statistics taller, so a page at the bottom scrolls itself up to stay in
     * range. Robolectric's WebView has no scroll range, so the rule is checked on a [ScrollView].
     */
    @Test
    fun `an upward scroll which ends at the bottom is not user input`() {
        val scrollView =
            ScrollView(targetContext).apply {
                addView(View(targetContext).apply { minimumHeight = 1000 })
                measure(
                    View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                )
                layout(0, 0, 100, 100)
            }

        scrollView.scrollTo(0, 900)
        assertThat("pulled back to the bottom by a resize", scrollView.scrolledUpToBottom(dy = -50), equalTo(true))
        assertThat("scrolling down is always user input", scrollView.scrolledUpToBottom(dy = 50), equalTo(false))

        scrollView.scrollTo(0, 500)
        assertThat("the user scrolled up, away from the bottom", scrollView.scrolledUpToBottom(dy = -50), equalTo(false))
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
            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            deckPicker.binding.root.requestLayout()
            advanceRobolectricLooper()
            // Robolectric re-dispatches zero insets whenever the bar's state or height changes:
            // apply the real nav bar again once layout has settled
            deckPicker.dispatchInsets(navBarBottom = 48.dp)

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

    /** Tabs draw edge-to-edge: their content scrolls beneath the bottom navigation and the system bar */
    @Test
    fun `the card browser scrolls beneath the bottom navigation`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.BROWSER.id
            advanceRobolectricLooper()
            val container = deckPicker.binding.bottomNavFragmentContainer!!
            val list = deckPicker.findViewById<RecyclerView>(R.id.card_browser_list)

            assertThat("the tab fills the screen", container.bottom, equalTo(deckPicker.binding.root.height))
            assertThat("the last card can scroll above the bar", list.paddingBottom, equalTo(nav.height))
            assertThat(list.clipToPadding, equalTo(false))
        }

    /** A hidden bar leaves no gap at the end of a tab: only the system bar is kept clear */
    @Test
    fun `tabs end above the system bar while the bottom bar is hidden`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            val behavior = HideViewOnScrollBehavior.from(nav)
            val systemBar = 48.dp.toPx(deckPicker)
            for (tab in listOf(NavigationItem.BROWSER, NavigationItem.MORE, NavigationItem.STATS)) {
                nav.selectedItemId = tab.id
                advanceRobolectricLooper()

                behavior.slideOut(nav, false)
                advanceRobolectricLooper()
                // Robolectric re-dispatches zero insets when the bar's state changes
                deckPicker.dispatchInsets(navBarBottom = 48.dp)
                assertThat("$tab: hidden bar", deckPicker.endPadding(tab), equalTo(systemBar + deckPicker.belowScreen(tab)))

                behavior.slideIn(nav, false)
                advanceRobolectricLooper()
                deckPicker.dispatchInsets(navBarBottom = 48.dp)
                assertThat("$tab: shown bar", deckPicker.endPadding(tab), greaterThan(systemBar))
            }
        }

    /** As in the deck list, a tab's bar continues beneath a display cutout: only its content is inset */
    @Test
    fun `tab bars continue beneath a display cutout`() =
        withBottomNavigation { deckPicker ->
            val cutout = 30.dp.toPx(deckPicker)
            for (tab in listOf(NavigationItem.BROWSER, NavigationItem.MORE, NavigationItem.STATS)) {
                deckPicker.bottomNav.selectedItemId = tab.id
                advanceRobolectricLooper()
                deckPicker.dispatchInsets(cutoutLeft = 30.dp)
                deckPicker.binding.root.requestLayout()
                advanceRobolectricLooper()

                val view = deckPicker.supportFragmentManager.findFragmentByTag(tab.tag)!!.requireView()
                val toolbar = view.findViewById<View>(R.id.toolbar)
                val (bar, content) =
                    when (tab) {
                        NavigationItem.BROWSER ->
                            view.findViewById<View>(R.id.toolbar_container) to
                                view.findViewById(R.id.card_browser_list)
                        NavigationItem.MORE -> toolbar.parent as View to deckPicker.moreBinding.moreScrollView.getChildAt(0)
                        // Robolectric does not lay out the WebView: use the area its layout gives it
                        NavigationItem.STATS -> deckPicker.statisticsBinding.appBar to deckPicker.statisticsBinding.webviewLayout
                        NavigationItem.HOME -> error("not a tab with its own content")
                    }
                assertThat("$tab: bar reaches the edge", bar.leftOnScreen, equalTo(0))
                assertThat("$tab: toolbar clears the cutout", toolbar.leftOnScreen + toolbar.paddingLeft, greaterThanOrEqualTo(cutout))
                assertThat("$tab: content clears the cutout", content.leftOnScreen + content.paddingLeft, greaterThanOrEqualTo(cutout))
            }
        }

    /** TalkBack asks the view to show itself, without moving the keyboard focus */
    @Test
    fun `More shows an accessibility-focused item above the bottom navigation`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            val binding = deckPicker.moreBinding
            binding.moreScrollView.scrollTo(0, 0)
            val item = binding.moreSupportFeedback

            item.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id, null)
            advanceRobolectricLooper()

            assertThat("the bottom navigation is still visible", HideViewOnScrollBehavior.from(nav).isScrolledIn, equalTo(true))
            assertThat(item.topOnScreen + item.height, lessThanOrEqualTo(nav.topOnScreen))
        }

    @Test
    fun `More scrolls beneath the bottom navigation`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            val scrollView = deckPicker.moreBinding.moreScrollView

            assertThat("the last item can scroll above the bar", scrollView.paddingBottom, equalTo(nav.height))
            assertThat(scrollView.clipToPadding, equalTo(false))
        }

    /** Insets work differently below API 30: issue 21562 was reported on API 29 */
    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `tabs scroll beneath the bottom navigation on API 29`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            assertThat("More clears the bar", deckPicker.moreBinding.moreScrollView.paddingBottom, equalTo(nav.height))

            nav.selectedItemId = NavigationItem.BROWSER.id
            advanceRobolectricLooper()
            val list = deckPicker.findViewById<RecyclerView>(R.id.card_browser_list)
            assertThat("the card browser clears the bar", list.paddingBottom, equalTo(nav.height))
        }

    @Test
    fun `Statistics pads its page beneath the bottom navigation`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()

            deckPicker.dispatchInsets()

            assertThat(
                deckPicker.endPadding(NavigationItem.STATS),
                equalTo(nav.height + deckPicker.belowScreen(NavigationItem.STATS)),
            )
            assertThat("the page is not resized", deckPicker.statisticsBinding.webviewLayout.paddingBottom, equalTo(0))
        }

    @Test
    fun `Statistics keeps clear of the status bar and display cutout`() =
        withBottomNavigation { deckPicker ->
            deckPicker.bottomNav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            deckPicker.dispatchInsets(cutoutLeft = 30.dp)
            deckPicker.binding.root.requestLayout()
            advanceRobolectricLooper()

            val statistics = deckPicker.statisticsBinding
            assertThat("toolbar is below the status bar", statistics.toolbar.topOnScreen, greaterThanOrEqualTo(24.dp.toPx(deckPicker)))
            assertThat("toolbar clears the cutout", statistics.deckName.leftOnScreen, greaterThanOrEqualTo(30.dp.toPx(deckPicker)))
            val page = statistics.webviewLayout
            assertThat("page clears the cutout", page.leftOnScreen + page.paddingLeft, greaterThanOrEqualTo(30.dp.toPx(deckPicker)))
        }

    /** While its toolbar is expanded, the Statistics page extends below the screen by the toolbar's height */
    @Test
    fun `Statistics pads its page for the part the expanded toolbar pushes off screen`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val statistics = deckPicker.statisticsBinding
            statistics.appBar.setExpanded(true, false)
            advanceRobolectricLooper()
            deckPicker.dispatchInsets()

            val offScreen = deckPicker.belowScreen(NavigationItem.STATS)
            assertThat("the expanded toolbar pushes the page down", offScreen, equalTo(statistics.toolbar.height))
            assertThat("expanded", deckPicker.endPadding(NavigationItem.STATS), equalTo(nav.height + offScreen))

            statistics.appBar.setExpanded(false, false)
            advanceRobolectricLooper()
            assertThat("collapsed", deckPicker.endPadding(NavigationItem.STATS), equalTo(nav.height))
        }

    /** TalkBack moves through the page without its scroll padding: the page instead ends above the bar */
    @Test
    fun `with TalkBack, the Statistics page ends above the bottom navigation`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            shadowOf(deckPicker.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
            advanceRobolectricLooper()
            deckPicker.dispatchInsets()

            val statistics = deckPicker.statisticsBinding
            assertThat("the toolbar no longer scrolls the page down", statistics.appBar.totalScrollRange, equalTo(0))
            assertThat("the page ends above the bar", statistics.webviewLayout.paddingBottom, equalTo(nav.height))
            assertThat("so it needs no padding of its own", deckPicker.endPadding(NavigationItem.STATS), equalTo(0))
        }

    /** Resizing on every slide relayouts the tab: Statistics' WebView visibly stutters */
    @Test
    fun `hiding the bar does not resize the tab`() =
        withBottomNavigation { deckPicker ->
            val nav = deckPicker.bottomNav
            nav.selectedItemId = NavigationItem.STATS.id
            advanceRobolectricLooper()
            val page = deckPicker.statisticsBinding.webviewLayout
            val shownHeight = page.height

            HideViewOnScrollBehavior.from(nav).slideOut(nav)
            deckPicker.binding.root.requestLayout()
            advanceRobolectricLooper()

            assertThat(page.height, equalTo(shownHeight))
        }

    private fun withBottomNavigation(test: (DeckPicker) -> Unit) =
        withPref(Prefs::devBottomNavEnabled, true) {
            setIntroductionSlidesShown(true)
            test(startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get())
        }

    private fun DeckPicker.scrollFrom(
        source: View,
        dy: Int,
    ) {
        val root = binding.root
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

    /** How far a tab's content extends below the screen: Statistics' expanded toolbar pushes its page down */
    private fun DeckPicker.belowScreen(tab: NavigationItem): Int =
        if (tab != NavigationItem.STATS) {
            0
        } else {
            statisticsBinding.webviewLayout.let { it.bottom - (it.parent as View).height }.coerceAtLeast(0)
        }

    /** The space a tab leaves beneath its last item, in pixels */
    private fun DeckPicker.endPadding(tab: NavigationItem): Int =
        when (tab) {
            NavigationItem.BROWSER -> findViewById<RecyclerView>(R.id.card_browser_list).paddingBottom
            NavigationItem.MORE -> moreBinding.moreScrollView.paddingBottom
            NavigationItem.STATS -> {
                val css = shadowOf(statisticsWebView).lastEvaluatedJavascript
                val cssPixels = Regex("""bottomInset = ([0-9.]+);""").find(css)!!.groupValues[1].toFloat()
                (cssPixels * resources.displayMetrics.density).roundToInt()
            }
            NavigationItem.HOME -> error("not a tab with its own content")
        }

    private val View.topOnScreen: Int
        get() = IntArray(2).also { getLocationOnScreen(it) }[1]

    private val View.leftOnScreen: Int
        get() = IntArray(2).also { getLocationOnScreen(it) }[0]

    private val View.contentBottomOnScreen: Int
        get() = topOnScreen + height - paddingBottom

    private val DeckPicker.moreBinding: FragmentMoreBinding
        get() = FragmentMoreBinding.bind(supportFragmentManager.findFragmentByTag(NavigationItem.MORE.tag)!!.requireView())

    private val DeckPicker.statisticsBinding: PageStatisticsBinding
        get() = PageStatisticsBinding.bind(supportFragmentManager.findFragmentByTag(NavigationItem.STATS.tag)!!.requireView())

    /** The [WebView] wrapped by the Statistics page's [com.ichi2.anki.workarounds.SafeWebViewLayout] */
    private val DeckPicker.statisticsWebView: WebView
        get() = statisticsBinding.webviewLayout.getChildAt(0) as WebView

    private val DeckPicker.bottomNav: BottomNavigationView
        get() = binding.bottomNavigation!!
}
