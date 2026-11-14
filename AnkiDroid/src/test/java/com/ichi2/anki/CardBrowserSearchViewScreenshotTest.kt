// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import android.widget.HorizontalScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.search.SearchView
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.browser.withCardBrowserFragment
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.windowInsetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class CardBrowserSearchViewScreenshotTest : ScreenshotTest() {
    @Test
    fun filterChips(
        @TestParameter rtl: Boolean,
    ) {
        if (rtl) RuntimeEnvironment.setQualifiers("+ldrtl")
        withCardBrowserFragment(useSearchView = true) {
            advanceRobolectricLooper()
            val scroller = requireView().findViewById<HorizontalScrollView>(R.id.chip_group)
            val direction = if (rtl) "rtl" else "ltr"
            captureScreen("${direction}_chips_start")

            scroller.scrollTo(scroller.getChildAt(0).width, 0)
            scroller.scrollTo(scroller.scrollX / 2, 0)
            captureScreen("${direction}_chips_scrolled")
        }
    }

    @Test
    fun browser(
        @TestParameter landscape: Boolean,
    ) {
        val orientation = setOrientation(landscape)
        repeat(50) { addBasicNote("Card $it", "Answer $it") }
        withCardBrowserFragment(useSearchView = true) {
            val browser = requireActivity() as CardBrowser
            browser.waitForSearchResults()
            browser.simulateSystemBars(navBarBottom = 24.dp, bottomCornerRadius = 34.dp)
            // Match a user scrolling down: the app bar collapses before the list reaches its end.
            requireView().findViewById<AppBarLayout>(R.id.browser_app_bar).setExpanded(false, false)
            advanceRobolectricLooper()
            requireView().findViewById<RecyclerView>(R.id.card_browser_list).scrollToEnd()
            requireView().findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()
            captureScreen("${orientation}_scrolled_to_bottom")
        }
    }

    @Test
    fun search(
        @TestParameter landscape: Boolean,
        @TestParameter searchTools: Boolean,
    ) {
        val orientation = setOrientation(landscape)
        withCardBrowserFragment(useSearchView = true, useSearchTools = searchTools) {
            val browser = requireActivity() as CardBrowser
            requireView().findViewById<SearchView>(R.id.search_view).show()
            advanceRobolectricLooper()
            if (searchTools) {
                requireView().findViewById<View>(R.id.toggle_advanced_search).performClick()
                advanceRobolectricLooper()
            }
            browser.simulateKeyboard(keyboardHeight = 200.dp)
            with(browser) {
                window.simulateSystemBars(
                    windowInsetsOf(
                        navBarBottom = 48.dp,
                        cutoutLeft = if (landscape) 32.dp else 0.dp,
                        imeBottom = 200.dp,
                    ),
                )
            }
            val mode = if (searchTools) "search_tools" else "search"
            captureScreen("${orientation}_${mode}_keyboard")
        }
    }
}
