// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.withCardBrowserFragment
import org.junit.Test

class StandardSearchFragmentScreenshotTest : ScreenshotTest() {
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
