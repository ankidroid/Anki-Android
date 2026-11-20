// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.withCardBrowserFragment
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class StandardSearchFragmentScreenshotTest : ScreenshotTest() {
    @Test
    fun searchRows(
        @TestParameter rtl: Boolean,
        @TestParameter("1.0", "1.5") fontScale: Float,
    ) {
        if (rtl) RuntimeEnvironment.setQualifiers("+ldrtl")
        RuntimeEnvironment.setFontScale(fontScale)
        SearchHistory().apply {
            clear()
            addRecent(SearchHistoryEntry("tag:language ".repeat(8).trim()))
            addRecent(SearchHistoryEntry("is:due"))
        }
        runTest {
            SavedSearches.saveToConfig(
                listOf(
                    SavedSearch("Languages", "tag:language"),
                    SavedSearch("hello world", ""),
                    SavedSearch("A saved search with a name that wraps onto a second line", "is:due"),
                ),
            )
        }

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()
            val direction = if (rtl) "rtl" else "ltr"
            captureScreen("${direction}_search_rows_font_$fontScale")
        }
    }

    @Test
    fun `expanded history leaves room for saved searches - Issue 23297`() {
        SearchHistory().apply {
            clear()
            repeat(SearchHistory.MAX_ENTRIES) { addRecent(SearchHistoryEntry("prop:ivl>$it")) }
            addRecent(SearchHistoryEntry("tag:language ".repeat(200).trim()))
        }
        runTest {
            SavedSearches.saveToConfig(
                listOf(
                    SavedSearch("Languages", "tag:language"),
                    SavedSearch("hello world", ""),
                ),
            )
        }

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()
            requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG).viewModel.toggleHistoryExpanded()
            advanceRobolectricLooper()

            captureScreen("expanded_search_history")
        }
    }

    @Test
    fun `large saved searches have bounded previews - Issue 22820`() {
        SearchHistory().clear()
        runTest {
            SavedSearches.saveToConfig(
                listOf(
                    SavedSearch("Long name ".repeat(10_000), "nid:" + (1..10_000).joinToString(",")),
                    SavedSearch("Short name", "tag:short"),
                ),
            )
        }

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()

            captureScreen("large_saved_search_previews")
        }
    }
}
