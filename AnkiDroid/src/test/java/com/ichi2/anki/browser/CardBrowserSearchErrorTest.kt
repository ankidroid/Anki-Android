// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.withPref
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class CardBrowserSearchErrorTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var embedded: Boolean = false

    @Test
    fun `search errors are displayed in each browser host - Issue 23301`() =
        withBrowser {
            throwOnShowError = false
            repeat(2) {
                activityViewModel.setQuery("is:now").join()
                awaitSearch()

                assertSearchError("now").dismiss()
            }
        }

    @Test
    fun `closing the search screen does not rerun a rejected search`() =
        withSuccessfulSearch {
            openSearch("is:now")
            submitSearch()
            val dialog = assertSearchError("now").also { it.dismiss() }
            val rejectedSearch = activityViewModel.searchJob

            openSearch("is:no")
            assertNotNull(searchView).hide()
            advanceRobolectricLooper()
            awaitSearch()

            assertSame(dialog, ShadowDialog.getLatestDialog(), "closing the search screen showed another error")
            assertSame(rejectedSearch, activityViewModel.searchJob)
        }

    @Test
    fun `recreating the browser does not rerun a rejected search`() =
        withSuccessfulSearch { recreate ->
            openSearch("re:b[")
            submitSearch()
            val dialog = assertSearchError("regex").also { it.dismiss() }
            val errorMessage = assertNotNull(dialog.findViewById<TextView>(android.R.id.message)).text.toString()
            val rejectedSearch = activityViewModel.searchJob
            val history = SearchHistory().entries

            val restored = recreate()
            restored.awaitSearch()

            assertSame(rejectedSearch, restored.activityViewModel.searchJob)
            assertFalse(
                ShadowDialog.getShownDialogs().any {
                    it.isShowing && it.findViewById<TextView>(android.R.id.message)?.text.toString() == errorMessage
                },
                "recreating the browser showed the search error again",
            )
            assertEquals(history, SearchHistory().entries)
        }

    @Test
    fun `recreating the browser keeps a menu search`() =
        withSuccessfulSearch { recreate ->
            activityViewModel.searchForSuspendedCards().join()
            awaitSearch()
            assertEquals("is:suspended", activityViewModel.searchRequestFlow.value.query)
            val history = SearchHistory().entries

            val restored = recreate()
            restored.awaitSearch()

            assertEquals("is:suspended", restored.activityViewModel.searchRequestFlow.value.query)
            assertEquals(history, SearchHistory().entries)
        }

    @Test
    fun `programmatic query updates preserve history and update the search bar`() =
        withSuccessfulSearch {
            val history = SearchHistory().entries
            activityViewModel.searchForSuspendedCards().join()
            awaitSearch()

            assertEquals(history, SearchHistory().entries)
            assertContains(assertNotNull(searchBar).text.toString(), "is:suspended")
        }

    private fun withSuccessfulSearch(block: suspend CardBrowserFragment.(recreate: () -> CardBrowserFragment) -> Unit) {
        addBasicNote("cat")
        SearchHistory().clear()
        withBrowser(useSearchView = true) { recreate ->
            throwOnShowError = false
            openSearch("cat")
            submitSearch()
            assertEquals("cat", SearchHistory().entries.single().query)
            assertFalse(assertNotNull(searchView).isShowing)
            block(recreate)
        }
    }

    private fun CardBrowserFragment.openSearch(query: String) {
        val search = assertNotNull(searchView)
        search.show()
        advanceRobolectricLooper()
        search.editText.setText(query)
        assertEquals(query, search.editText.text.toString())
    }

    private suspend fun CardBrowserFragment.submitSearch() {
        assertNotNull(searchView).editText.onEditorAction(IME_ACTION_SEARCH)
        awaitSearch()
    }

    private fun assertSearchError(expected: String): AlertDialog {
        val dialog = assertNotNull(ShadowDialog.getLatestDialog(), "search error is shown") as AlertDialog
        assertTrue(dialog.isShowing, "expected a new error dialog containing '$expected'")
        val message = assertNotNull(dialog.findViewById<TextView>(android.R.id.message)).text.toString()
        assertContains(message, expected)
        return dialog
    }

    private suspend fun CardBrowserFragment.awaitSearch() {
        activityViewModel.searchJob?.join()
        advanceRobolectricLooper()
    }

    private fun withBrowser(
        useSearchView: Boolean = false,
        block: suspend CardBrowserFragment.(recreate: () -> CardBrowserFragment) -> Unit,
    ) = withPref(Prefs::devUsingCardBrowserSearchView, useSearchView) {
        if (embedded) {
            withPref(Prefs::devBottomNavEnabled, true) {
                setIntroductionSlidesShown(true)
                runTest {
                    val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
                    val activity = controller.get()
                    activity.binding.bottomNavigation!!.selectedItemId = NavigationItem.BROWSER.id
                    advanceRobolectricLooper()
                    val browser = activity.browseTab()
                    browser.awaitSearch()
                    browser.block {
                        controller.recreate()
                        advanceRobolectricLooper()
                        controller.get().browseTab()
                    }
                }
            }
        } else {
            runTest {
                ActivityScenario.launch(CardBrowser::class.java).use { scenario ->
                    fun fragment(): CardBrowserFragment {
                        lateinit var browser: CardBrowser
                        scenario.onActivity { browser = it }
                        return browser.cardBrowserFragment
                    }

                    val browser = fragment()
                    browser.awaitSearch()
                    browser.block {
                        scenario.recreate()
                        advanceRobolectricLooper()
                        fragment()
                    }
                }
            }
        }
    }

    private fun DeckPicker.browseTab() = supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "embedded={0}")
        @JvmStatic
        fun parameters() = listOf(arrayOf(false), arrayOf(true))
    }
}
