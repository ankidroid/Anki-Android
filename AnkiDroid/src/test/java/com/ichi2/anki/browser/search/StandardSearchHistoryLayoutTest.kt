// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.withCardBrowserFragment
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.simulateKeyboard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w891dp-h411dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandardSearchHistoryLayoutTest : RobolectricTest() {
    @Test
    fun `history remains selectable with the landscape keyboard open`() {
        SearchHistory().apply {
            clear()
            addRecent(SearchHistoryEntry("is:due"))
        }

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()
            requireActivity().simulateKeyboard(keyboardHeight = 200.dp)

            val fragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            val history = fragment.binding.searchHistory
            assertEquals(1, history.count)
            assertTrue(history.height > 0, "The keyboard must leave a viewport for history")
            val row = requireNotNull(history.getChildAt(0))
            val visible = Rect()
            assertTrue(row.getGlobalVisibleRect(visible), "A history entry must remain on screen")
            assertTrue(visible.height() > 0, "A history entry must remain selectable")
        }
    }
}
