// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.getVisibleRows
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.runner.RunWith

/** Test of [CardBrowserFragment] */
@RunWith(AndroidJUnit4::class)
class CardBrowserFragmentTest : RobolectricTest() {
    @Test
    fun `search results completed while stopped are displayed on restart - Issue 17759`() =
        runTest {
            val removedNote = addBasicNote("dog", "barks")
            val matchingRow = CardOrNoteId(addBasicNote("cat", "meows").firstCard().id)

            withStartedBrowser { browser ->
                val adapter = browser.cardBrowserFragment.cardsAdapter
                assertThat("initial search results", adapter.itemCount, equalTo(2))

                // The browser stops collecting search updates while the note editor is open.
                stopBrowser()
                col.removeNotes(noteIds = listOf(removedNote.id))
                browser.refreshSearchAndWait()

                assertThat(browser.viewModel.cards.toList(), equalTo(listOf(matchingRow)))
                assertThat("the stopped adapter has not received the new results", adapter.itemCount, equalTo(2))

                val completedSearch = browser.viewModel.searchJob
                resumeBrowser { resumedBrowser ->
                    assertThat(resumedBrowser.cardBrowserFragment.cardsAdapter, sameInstance(adapter))
                    assertThat("resuming does not run another search", resumedBrowser.viewModel.searchJob, sameInstance(completedSearch))
                    assertThat("the adapter displays the completed search", adapter.itemCount, equalTo(1))
                    assertThat(resumedBrowser.getVisibleRows().map { it.id }, equalTo(listOf(matchingRow)))
                }
            }
        }

    @Test
    fun `searchView EditText submit via IME_ACTION_SEARCH`() =
        withCardBrowserFragment(useSearchView = true) {
            searchViewModel.submittedSearchFlow.test {
                searchViewModel.isScreenOpenFlow.value = true
                searchView!!.editText.onEditorAction(IME_ACTION_SEARCH)
                expectMostRecentItem()
            }
        }

    @Test
    fun `searchView EditText submit via KEYCODE_ENTER`() =
        withCardBrowserFragment(useSearchView = true) {
            searchViewModel.submittedSearchFlow.test {
                searchViewModel.isScreenOpenFlow.value = true
                searchView!!.editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                expectMostRecentItem()
            }
        }

    @Test
    fun `shortcut labels use Sentence case`() =
        withCardBrowserFragment {
            val offenders = shortcuts.shortcuts.map { it.label }.filterNot { it.isSentenceCase }
            assertThat("shortcut labels should be Sentence case, not Title Case", offenders, empty())
        }

    private suspend fun withStartedBrowser(block: suspend ActivityScenario<CardBrowser>.(CardBrowser) -> Unit) {
        ActivityScenario.launch<CardBrowser>(Intent(targetContext, CardBrowser::class.java)).use { scenario ->
            lateinit var browser: CardBrowser
            scenario.onActivity { browser = it }
            browser.viewModel.searchJob?.join()
            advanceRobolectricLooper()
            scenario.block(browser)
        }
    }

    private fun ActivityScenario<CardBrowser>.stopBrowser() {
        moveToState(Lifecycle.State.CREATED)
        onActivity { browser ->
            assertThat(browser.cardBrowserFragment.lifecycle.currentState, equalTo(Lifecycle.State.CREATED))
        }
    }

    private suspend fun CardBrowser.refreshSearchAndWait() {
        viewModel.launchSearchForCards()
        requireNotNull(viewModel.searchJob).join()
        advanceRobolectricLooper()
    }

    private fun ActivityScenario<CardBrowser>.resumeBrowser(block: (CardBrowser) -> Unit) {
        moveToState(Lifecycle.State.RESUMED)
        advanceRobolectricLooper()
        onActivity(block)
    }
}

private val capitalizedFollowingWord = Regex("""\s\p{Lu}\p{Ll}""")

// Material Design sentence case: the first word is capitalized; later words are not
private val String.isSentenceCase: Boolean
    get() = first().isUpperCase() && !capitalizedFollowingWord.containsMatchIn(this)

context(test: RobolectricTest)
fun withCardBrowserFragment(
    useSearchView: Boolean = false,
    block: suspend CardBrowserFragment.() -> Unit,
) = test.runTest {
    try {
        Prefs.devUsingCardBrowserSearchView = useSearchView

        val cardBrowserIntent = Intent(test.targetContext, CardBrowser::class.java)

        ActivityScenario.launch<CardBrowser>(cardBrowserIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { browser ->
                assertThat("Activity is not finishing", !browser.isFinishing)
                assertThat(browser.useSearchView, equalTo(useSearchView))

                runBlocking { block(browser.cardBrowserFragment) }
            }
        }
    } finally {
        Prefs.devUsingCardBrowserSearchView = false
    }
}
