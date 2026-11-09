// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.withCardBrowserFragment
import com.ichi2.anki.databinding.ViewSearchHistoryItemBinding
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test of [StandardSearchFragment] */
@RunWith(AndroidJUnit4::class)
class StandardSearchFragmentTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        SearchHistory().clear()
    }

    @Test
    fun `history entries are loaded`() {
        SearchHistory().addRecent(SearchHistoryEntry("aa"))

        withFragment {
            assertEquals(1, binding.searchHistory.count)
        }
    }

    @Test
    fun `favoriting a history entry opens a dialog and saves the search`() {
        SearchHistory().addRecent(SearchHistoryEntry("bonjour"))

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()

            val fragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            val history = fragment.binding.searchHistory
            val row = ViewSearchHistoryItemBinding.bind(history.adapter.getView(0, null, history))
            row.favorite.performClick()
            fragment.childFragmentManager.executePendingTransactions()

            val dialogFragment =
                fragment.childFragmentManager.fragments
                    .filterIsInstance<SaveBrowserSearchDialogFragment>()
                    .single()
            val dialog = dialogFragment.requireDialog() as AlertDialog
            dialog.input = "French search"
            dialog.performPositiveClick()

            assertEquals(listOf(SavedSearch("French search", "bonjour")), SavedSearches.loadFromConfig())
        }
    }

    @Test
    fun `history entries are truncated`() {
        val expectedMaxEntries = 5
        assertEquals(expectedMaxEntries, CardBrowserSearchViewModel.MAX_SEARCH_HISTORY_ENTRIES)

        val history = SearchHistory()
        repeat(expectedMaxEntries + 1) {
            history.addRecent(SearchHistoryEntry(it.toString()))
        }

        withFragment {
            assertEquals(expectedMaxEntries, binding.searchHistory.count)
            assertEquals(expectedMaxEntries + 1, viewModel.searchHistory.size)
        }
    }

    @Test
    fun `see more button is hidden when entries do not exceed limit`() {
        val history = SearchHistory()
        repeat(3) { history.addRecent(SearchHistoryEntry(it.toString())) }

        withFragment {
            assertFalse(binding.toggleSearchHistory.isVisible)
        }
    }

    @Test
    fun `see more button is visible when entries exceed limit`() {
        val history = SearchHistory()
        repeat(CardBrowserSearchViewModel.MAX_SEARCH_HISTORY_ENTRIES + 1) {
            history.addRecent(SearchHistoryEntry(it.toString()))
        }

        withFragment {
            assertTrue(binding.toggleSearchHistory.isVisible)
        }
    }

    fun withFragment(block: StandardSearchFragment.() -> Unit) =
        withCardBrowserFragment(useSearchView = true) {
            val targetFragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            block(targetFragment)
        }
}

fun <T : Fragment> Fragment.requireChildFragment(tag: String): T {
    @Suppress("UNCHECKED_CAST")
    return requireNotNull(this.childFragmentManager.findFragmentByTag(tag) as? T?)
    { "can't find tag $tag" }
}
