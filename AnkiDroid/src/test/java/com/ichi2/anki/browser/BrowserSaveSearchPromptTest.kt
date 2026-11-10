// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.search.SavedSearches
import com.ichi2.anki.browser.search.StandardSearchFragment
import com.ichi2.anki.databinding.ViewSearchHistoryItemBinding
import com.ichi2.anki.deckPicker
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import kotlinx.coroutines.asCoroutineDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "normal")
class BrowserSaveSearchPromptTest : RobolectricTest() {
    @Test
    fun `Decks defers the prompt`() =
        withDeferredPrompt { standardSearch ->
            val prompts = standardSearch.childFragmentManager.fragments.filterIsInstance<SaveBrowserSearchDialogFragment>()

            assertThat(prompts, empty())
        }

    @Test
    fun `Browse resumes the pending save`() =
        withDeferredPrompt { standardSearch ->
            selectTab(NavigationItem.BROWSER)
            advanceRobolectricLooper()
            standardSearch.childFragmentManager.executePendingTransactions()

            val prompt =
                standardSearch.childFragmentManager.fragments
                    .filterIsInstance<SaveBrowserSearchDialogFragment>()
                    .single()
            val dialog = prompt.requireDialog() as AlertDialog
            dialog.input = "French search"
            dialog.performPositiveClick()

            assertThat(SavedSearches.loadFromConfig().single().query, equalTo("bonjour flag:1"))
        }

    @Test
    fun `a deck renamed while the prompt is deferred still finds its cards`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            withPref(Prefs::devUsingCardBrowserSearchView, true) {
                val deckId = addDeck("Before")
                val card = addBasicNote("bonjour", "hello").firstCard()
                card.did = deckId
                col.updateCards(listOf(card))
                addBasicNote("bonjour", "outside the deck")
                SearchHistory().addRecent(SearchHistoryEntry("bonjour", deckIds = listOf(deckId)))
                deckPicker {
                    selectTab(NavigationItem.BROWSER)
                    advanceRobolectricLooper()
                    val browser = supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment
                    requireNotNull(browser.searchView).show()
                    advanceRobolectricLooper()
                    val standardSearch =
                        browser.childFragmentManager.findFragmentByTag(StandardSearchFragment.TAG) as StandardSearchFragment
                    val history = standardSearch.binding.searchHistory
                    val row = ViewSearchHistoryItemBinding.bind(history.adapter.getView(0, null, history))

                    val collectionBusy = CountDownLatch(1)
                    val queue = Executors.newSingleThreadExecutor()
                    val previousQueue = CollectionManager.setTestDispatcher(queue.asCoroutineDispatcher(), useReentrantLock = false)
                    try {
                        // A slow collection operation is still running when the star is tapped.
                        queue.execute { collectionBusy.await() }
                        assertTrue(row.favorite.performClick())

                        // The user moves to Decks before the query is resolved.
                        selectTab(NavigationItem.HOME)
                        collectionBusy.countDown()
                        queue.submit {}.get()
                        advanceRobolectricLooper()
                    } finally {
                        collectionBusy.countDown()
                        CollectionManager.setTestDispatcher(previousQueue)
                        queue.shutdown()
                    }
                    advanceRobolectricLooper()

                    // The deck is renamed on the Decks tab before the user returns.
                    undoableOp { decks.rename(deckId, "After") }
                    advanceRobolectricLooper()

                    selectTab(NavigationItem.BROWSER)
                    advanceRobolectricLooper()
                    standardSearch.childFragmentManager.executePendingTransactions()
                    val prompt =
                        standardSearch.childFragmentManager.fragments
                            .filterIsInstance<SaveBrowserSearchDialogFragment>()
                            .single()
                    val dialog = prompt.requireDialog() as AlertDialog
                    dialog.input = "French search"
                    dialog.performPositiveClick()

                    val savedQuery = SavedSearches.loadFromConfig().single().query
                    assertThat("Saved query: $savedQuery", col.findCards(savedQuery), equalTo(listOf(card.id)))
                }
            }
        }

    private fun withDeferredPrompt(test: suspend DeckPicker.(StandardSearchFragment) -> Unit) =
        withPref(Prefs::devBottomNavEnabled, true) {
            withPref(Prefs::devUsingCardBrowserSearchView, true) {
                SearchHistory().addRecent(SearchHistoryEntry("bonjour", flags = listOf(Flag.RED)))
                deckPicker {
                    selectTab(NavigationItem.BROWSER)
                    advanceRobolectricLooper()
                    val browser = supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment
                    requireNotNull(browser.searchView).show()
                    advanceRobolectricLooper()
                    val standardSearch =
                        browser.childFragmentManager.findFragmentByTag(StandardSearchFragment.TAG) as StandardSearchFragment
                    val history = standardSearch.binding.searchHistory
                    val row = ViewSearchHistoryItemBinding.bind(history.adapter.getView(0, null, history))

                    val collectionBusy = CountDownLatch(1)
                    val queue = Executors.newSingleThreadExecutor()
                    val previousQueue = CollectionManager.setTestDispatcher(queue.asCoroutineDispatcher(), useReentrantLock = false)
                    try {
                        // A slow collection operation is still running when the star is tapped.
                        queue.execute { collectionBusy.await() }
                        assertTrue(row.favorite.performClick())

                        // The user moves to Decks before the query is resolved.
                        selectTab(NavigationItem.HOME)
                        collectionBusy.countDown()
                        queue.submit {}.get()
                        advanceRobolectricLooper()
                    } finally {
                        collectionBusy.countDown()
                        CollectionManager.setTestDispatcher(previousQueue)
                        queue.shutdown()
                    }
                    standardSearch.childFragmentManager.executePendingTransactions()

                    test(standardSearch)
                }
            }
        }

    private fun DeckPicker.selectTab(tab: NavigationItem) {
        findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = tab.id
        supportFragmentManager.executePendingTransactions()
    }
}
