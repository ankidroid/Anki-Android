// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.os.Looper
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.ext.snackbarAction
import com.ichi2.testutils.ext.snackbarText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** User actions which refresh the browser count (Issue 22384), in both browser UIs. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardBrowserUserActionFeedbackTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var useSearchView: Boolean = false

    @Test
    fun `show marked menu shows the refreshed count`() {
        addBasicNote("cat", "meows").apply {
            addTag("marked")
            flush()
        }
        addBasicNote("dog", "barks")
        withBrowser {
            activityViewModel.searchForMarkedNotes().join()
            awaitSearch()
            assertCount(1)
        }
    }

    @Test
    fun `show suspended menu shows the refreshed count`() {
        val note = addBasicNote("cat", "meows")
        col.sched.suspendCards(listOf(note.firstCard().id))
        addBasicNote("dog", "barks")
        withBrowser {
            activityViewModel.searchForSuspendedCards().join()
            awaitSearch()
            assertCount(1)
        }
    }

    @Test
    fun `tag filter shows the refreshed count`() {
        addBasicNote("cat", "meows").apply {
            addTag("audit")
            flush()
        }
        addBasicNote("dog", "barks")
        withBrowser {
            filterByTag("audit")
            awaitSearch()
            assertCount(1)
        }
    }

    @Test
    fun `legacy flag filter shows the refreshed count`() {
        addBasicNote("cat", "meows")
        withBrowser {
            activityViewModel.setFlagFilter(Flag.RED)
            awaitSearch()
            assertCount(0)
        }
    }

    @Test
    fun `switching to notes shows the refreshed count`() {
        addBasicAndReversedNote("cat", "meows")
        withBrowser {
            assertEquals(2, activityViewModel.rowCount)
            activityViewModel.setCardsOrNotes(CardsOrNotes.NOTES).join()
            awaitSearch()
            assertEquals(1, activityViewModel.rowCount)
            assertEquals("1 note shown", snackbarText)
            assertEquals(CardsOrNotes.NOTES, activityViewModel.flowOfLastCompletedSearch.value?.cardsOrNotes)
        }
    }

    @Test
    fun `mark and flag changes remain silent without rerunning search`() {
        val note = addBasicNote("cat", "meows")
        withBrowser {
            val completed = activityViewModel.flowOfLastCompletedSearch.value
            activityViewModel.selectRowAtPosition(0)
            activityViewModel.toggleMark()
            activityViewModel.updateSelectedCardsFlag(Flag.RED)
            advanceRobolectricLooper()
            assertEquals(completed, activityViewModel.flowOfLastCompletedSearch.value)
            assertNull(snackbarText)
            assertEquals(true, withCol { getNote(note.id).hasTag(this, "marked") })
        }
    }

    @Test
    fun `search all decks action shows the new count`() {
        addBasicNote("cat", "meows")
        val emptyDeck = addDeck("Empty")
        withBrowser {
            activityViewModel.setSelectedDeck(emptyDeck)
            awaitSearch()
            activityViewModel.setQuery("cat", feedbackMode = BrowserSearchFeedbackMode.SEARCH_RESULTS).join()
            awaitSearch()
            assertEquals(0, activityViewModel.rowCount)
            val action = assertNotNull(snackbarAction)
            assertEquals("Search all decks", action.text.toString())
            action.performClick()
            awaitSearch()
            assertEquals(true, activityViewModel.hasSelectedAllDecks())
            assertEquals("cat", activityViewModel.searchTerms)
            assertCount(1)
        }
    }

    @Test
    fun `explicit deep link query shows search feedback`() =
        runTest {
            addBasicNote("cat", "meows")
            addBasicNote("dog", "barks")
            try {
                Prefs.devUsingCardBrowserSearchView = useSearchView
                val intent =
                    Intent(targetContext, CardBrowser::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = "anki://x-callback-url/browser?search=cat".toUri()
                    }
                ActivityScenario.launch<CardBrowser>(intent).use { scenario ->
                    lateinit var browser: CardBrowser
                    scenario.onActivity { browser = it }
                    browser.viewModel.searchJob?.join()
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEquals("cat", browser.viewModel.searchTerms)
                    assertEquals(1, browser.viewModel.rowCount)
                    assertTrue(browser.snackbarText?.contains("1 card") == true)
                }
            } finally {
                Prefs.devUsingCardBrowserSearchView = false
            }
        }

    private fun withBrowser(block: suspend CardBrowserFragment.() -> Unit) =
        withCardBrowserFragment(useSearchView = useSearchView) {
            awaitSearch()
            activityViewModel.setSelectedDeck(SelectableDeck.AllDecks)
            awaitSearch()
            searchItem?.expandActionView()
            advanceRobolectricLooper()
            assertNull(snackbarText)
            block()
        }

    private suspend fun CardBrowserFragment.awaitSearch() {
        activityViewModel.searchJob?.join()
        advanceRobolectricLooper()
    }

    private fun CardBrowserFragment.assertCount(count: Int) {
        assertEquals(count, activityViewModel.rowCount)
        assertEquals("$count ${if (count == 1) "card" else "cards"} shown", snackbarText)
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "useSearchView={0}")
        @JvmStatic
        fun parameters() = listOf(arrayOf(false), arrayOf(true))
    }
}
