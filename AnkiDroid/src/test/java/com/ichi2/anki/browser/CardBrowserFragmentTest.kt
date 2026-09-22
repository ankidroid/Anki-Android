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
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.runner.RunWith

/** Test of [CardBrowserFragment] */
@RunWith(AndroidJUnit4::class)
class CardBrowserFragmentTest : RobolectricTest() {
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
