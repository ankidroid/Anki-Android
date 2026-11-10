// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

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

class CardBrowserSearchViewScreenshotTest : ScreenshotTest() {
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
    ) {
        val orientation = setOrientation(landscape)
        withCardBrowserFragment(useSearchView = true) {
            val browser = requireActivity() as CardBrowser
            requireView().findViewById<SearchView>(R.id.search_view).show()
            advanceRobolectricLooper()
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
            captureScreen("${orientation}_search_keyboard")
        }
    }
}
