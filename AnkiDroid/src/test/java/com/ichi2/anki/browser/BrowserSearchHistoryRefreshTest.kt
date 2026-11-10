// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel
import com.ichi2.anki.deckPicker
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "normal")
class BrowserSearchHistoryRefreshTest : RobolectricTest() {
    @Test
    fun `card answers do not refresh hidden history`() =
        withHiddenBrowser { searchViewModel ->
            answerCard()

            assertThat(searchViewModel.renderedHistory(), equalTo("bonjour deck:Before"))
        }

    @Test
    fun `returning to Browse refreshes history`() =
        withHiddenBrowser { searchViewModel ->
            answerCard()

            selectTab(NavigationItem.BROWSER)

            assertTrue(searchViewModel.isScreenOpenFlow.value, "Returning to the Browser preserves the open search")
            assertThat(searchViewModel.renderedHistory(), equalTo("bonjour deck:After"))
        }

    private fun withHiddenBrowser(test: suspend DeckPicker.(CardBrowserSearchViewModel) -> Unit) =
        withPref(Prefs::devBottomNavEnabled, true) {
            withPref(Prefs::devUsingCardBrowserSearchView, true) {
                val deckId = addDeck("Before")
                addBasicNote("front", "back")
                SearchHistory().addRecent(SearchHistoryEntry("bonjour", deckIds = listOf(deckId)))
                deckPicker {
                    selectTab(NavigationItem.BROWSER)
                    val browser = supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment
                    requireNotNull(browser.searchView).show()
                    advanceRobolectricLooper()
                    val searchViewModel = browser.searchViewModel
                    assertThat(searchViewModel.renderedHistory(), equalTo("bonjour deck:Before"))

                    selectTab(NavigationItem.HOME)

                    // Make a reload observable: this rename sends no notification.
                    col.decks.rename(deckId, "After")
                    advanceRobolectricLooper()
                    assertThat(searchViewModel.renderedHistory(), equalTo("bonjour deck:Before"))

                    test(searchViewModel)
                }
            }
        }

    private suspend fun answerCard() {
        val card = col.sched.card!!
        val states = col.backend.getSchedulingStates(card.id)
        undoableOp { sched.answerCard(sched.buildAnswer(card, states, Rating.GOOD)) }
        advanceRobolectricLooper()
    }

    private fun DeckPicker.selectTab(tab: NavigationItem) {
        findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = tab.id
        supportFragmentManager.executePendingTransactions()
        advanceRobolectricLooper()
    }

    private fun CardBrowserSearchViewModel.renderedHistory() =
        searchHistoryFlow.value.entryToSearchString
            .single()
            .second
            ?.value
}
