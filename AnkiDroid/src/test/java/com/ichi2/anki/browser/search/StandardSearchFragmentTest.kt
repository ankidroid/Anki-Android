// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.search.SearchView
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.Flag
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.withCardBrowserFragment
import com.ichi2.anki.databinding.ViewSearchHistoryItemBinding
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
    fun `favoriting a history entry preserves its filters - Issue 23114`() {
        SearchHistory().addRecent(SearchHistoryEntry("bonjour", flags = listOf(Flag.RED)))

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

            assertEquals(listOf(SavedSearch("French search", "bonjour flag:1")), SavedSearches.loadFromConfig())
        }
    }

    @Test
    fun `favoriting history still prompts for a name if the screen stops before the query resolves`() {
        SearchHistory().addRecent(SearchHistoryEntry("bonjour", flags = listOf(Flag.RED)))

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()

            val fragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            val history = fragment.binding.searchHistory
            val row = ViewSearchHistoryItemBinding.bind(history.adapter.getView(0, null, history))

            val collectionBusy = CountDownLatch(1)
            val queue = Executors.newSingleThreadExecutor()
            val previousQueue = CollectionManager.setTestDispatcher(queue.asCoroutineDispatcher(), useReentrantLock = false)
            try {
                // A slow collection operation is still running when the star is tapped.
                queue.execute { collectionBusy.await() }
                assertTrue(row.favorite.performClick())

                // The user leaves the screen before the query is resolved.
                childFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.CREATED) }
                collectionBusy.countDown()
                queue.submit {}.get()
                advanceRobolectricLooper()
            } finally {
                collectionBusy.countDown()
                CollectionManager.setTestDispatcher(previousQueue)
                queue.shutdown()
            }

            childFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.RESUMED) }
            advanceRobolectricLooper()
            fragment.childFragmentManager.executePendingTransactions()

            val prompts = fragment.childFragmentManager.fragments.filterIsInstance<SaveBrowserSearchDialogFragment>()
            assertEquals(1, prompts.size, "the 'save search' prompt was lost")

            prompts.single().dismissNow()
            childFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.CREATED) }
            childFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.RESUMED) }
            advanceRobolectricLooper()
            fragment.childFragmentManager.executePendingTransactions()

            assertTrue(
                fragment.childFragmentManager.fragments
                    .filterIsInstance<SaveBrowserSearchDialogFragment>()
                    .isEmpty(),
                "An acknowledged prompt must not reappear after resuming",
            )
        }
    }

    @Test
    fun `closing search before a favorite resolves does not prompt when search reopens`() {
        SearchHistory().addRecent(SearchHistoryEntry("bonjour", flags = listOf(Flag.RED)))

        withCardBrowserFragment(useSearchView = true) {
            val searchView = requireNotNull(searchView)
            searchView.show()
            advanceRobolectricLooper()

            val fragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            val history = fragment.binding.searchHistory
            val row = ViewSearchHistoryItemBinding.bind(history.adapter.getView(0, null, history))

            val collectionBusy = CountDownLatch(1)
            val queue = Executors.newSingleThreadExecutor()
            val previousQueue = CollectionManager.setTestDispatcher(queue.asCoroutineDispatcher(), useReentrantLock = false)
            try {
                // A slow collection operation is still running when the star is tapped.
                queue.execute { collectionBusy.await() }
                assertTrue(row.favorite.performClick())

                // The user closes search before the query is resolved.
                searchView.hide()
                advanceRobolectricLooper()
                assertEquals(SearchView.TransitionState.HIDDEN, searchView.currentTransitionState)
                collectionBusy.countDown()
                queue.submit {}.get()
                advanceRobolectricLooper()
            } finally {
                collectionBusy.countDown()
                CollectionManager.setTestDispatcher(previousQueue)
                queue.shutdown()
            }

            searchView.show()
            advanceRobolectricLooper()
            val reopened = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            reopened.childFragmentManager.executePendingTransactions()

            val prompts = reopened.childFragmentManager.fragments.filterIsInstance<SaveBrowserSearchDialogFragment>()
            assertTrue(prompts.isEmpty(), "reopening search showed an unrequested 'save search' prompt")
        }
    }

    @Test
    fun `tapping a history star again while the collection is busy prompts once`() {
        SearchHistory().addRecent(SearchHistoryEntry("bonjour", flags = listOf(Flag.RED)))

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()

            val fragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            val history = fragment.binding.searchHistory
            val row = ViewSearchHistoryItemBinding.bind(history.adapter.getView(0, null, history))

            fun prompts() = fragment.childFragmentManager.fragments.filterIsInstance<SaveBrowserSearchDialogFragment>()

            val collectionBusy = CountDownLatch(1)
            val queue = Executors.newSingleThreadExecutor()
            val previousQueue = CollectionManager.setTestDispatcher(queue.asCoroutineDispatcher(), useReentrantLock = false)
            try {
                // A slow collection operation is still running when the star is tapped.
                queue.execute { collectionBusy.await() }
                assertTrue(row.favorite.performClick())
                advanceRobolectricLooper()
                fragment.childFragmentManager.executePendingTransactions()

                // Nothing appeared, so the user taps the star again.
                if (prompts().isEmpty()) assertTrue(row.favorite.performClick())
                collectionBusy.countDown()
                queue.submit {}.get()
                advanceRobolectricLooper()
            } finally {
                collectionBusy.countDown()
                CollectionManager.setTestDispatcher(previousQueue)
                queue.shutdown()
            }
            advanceRobolectricLooper()
            fragment.childFragmentManager.executePendingTransactions()

            assertEquals(1, prompts().size, "'save search' prompts were stacked")
        }
    }

    @Test
    fun `favoriting history after a deck rename still finds its cards`() {
        val deckId = addDeck("Before")
        val card = addBasicNote("bonjour", "hello").firstCard()
        card.did = deckId
        col.updateCards(listOf(card))
        addBasicNote("bonjour", "outside the deck")
        SearchHistory().addRecent(SearchHistoryEntry("bonjour", deckIds = listOf(deckId)))

        withCardBrowserFragment(useSearchView = true) {
            requireNotNull(searchView).show()
            advanceRobolectricLooper()

            // The activity-scoped search ViewModel survives edits on the Decks tab.
            col.decks.rename(deckId, "After")
            advanceRobolectricLooper()

            val fragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            val history = fragment.binding.searchHistory
            val row = ViewSearchHistoryItemBinding.bind(history.adapter.getView(0, null, history))
            assertTrue(row.favorite.performClick())
            fragment.childFragmentManager.executePendingTransactions()

            val dialogFragment =
                fragment.childFragmentManager.fragments
                    .filterIsInstance<SaveBrowserSearchDialogFragment>()
                    .single()
            val dialog = dialogFragment.requireDialog() as AlertDialog
            dialog.input = "Renamed deck"
            dialog.performPositiveClick()

            val savedQuery = SavedSearches.loadFromConfig().single().query
            assertEquals(listOf(card.id), col.findCards(savedQuery), "Saved query: $savedQuery")
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
