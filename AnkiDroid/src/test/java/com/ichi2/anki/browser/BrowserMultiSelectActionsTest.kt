// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ALL_FIELDS_AS_FIELD
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.REQUEST_FIND_AND_REPLACE
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.RESULT_FIELD
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.RESULT_MATCH_CASE
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.RESULT_ONLY_SELECTED_NOTES
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.RESULT_REGEX
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.RESULT_REPLACEMENT
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.RESULT_SEARCH
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMultiSelectActionsTest : RobolectricTest() {
    @Test
    fun `flag in the Browse tab`() {
        val card = addBasicNote("one").firstCard()
        withBrowseTab {
            toggleRowAt(0)

            val flags = menu.findItem(R.id.action_flag).subMenu!!
            advanceRobolectricLooper()
            assertThat("the red flag is in the menu", flags.findItem(Flag.RED.code), notNullValue())
            flags.performIdentifierAction(Flag.RED.code, 0)
            advanceRobolectricLooper()

            assertThat(col.getCard(card.id).userFlag(), equalTo(Flag.RED.code))
        }
    }

    @Test
    fun `reset progress in the Browse tab`() {
        val card =
            addBasicNote("one").firstCard().update {
                due = 5
                queue = QueueType.Rev
                type = CardType.Rev
            }
        withBrowseTab {
            toggleRowAt(0)

            menu.performIdentifierAction(R.id.action_reset_cards_progress, 0)
            advanceRobolectricLooper()
            requireActivity().supportFragmentManager.setFragmentResult(
                ForgetCardsDialog.REQUEST_KEY_FORGET,
                Bundle().apply {
                    putBoolean(ForgetCardsDialog.RESULT_RESTORE_ORIGINAL, true)
                    putBoolean(ForgetCardsDialog.RESULT_RESET_REPETITION, false)
                },
            )
            advanceRobolectricLooper()

            assertThat(col.getCard(card.id).type, equalTo(CardType.New))
        }
    }

    @Test
    fun `find and replace in the Browse tab`() {
        val note = addBasicNote("one", "two")
        withBrowseTab {
            toggleRowAt(0)

            menu.performIdentifierAction(R.id.action_find_replace, 0)
            advanceRobolectricLooper()
            requireActivity().setFindAndReplaceResult(search = "one", replacement = "three")
            advanceRobolectricLooper()

            assertThat(col.getNote(note.id).fields[0], equalTo("three"))
        }
    }

    @Test
    fun `find and replace in the standalone browser`() {
        val note = addBasicNote("one", "two")
        withCardBrowserFragment(useSearchView = true) {
            activityViewModel.searchJob?.join()
            advanceRobolectricLooper()
            toggleRowAt(0)

            menu.performIdentifierAction(R.id.action_find_replace, 0)
            advanceRobolectricLooper()
            requireActivity().setFindAndReplaceResult(search = "one", replacement = "three")
            advanceRobolectricLooper()

            assertThat(col.getNote(note.id).fields[0], equalTo("three"))
        }
    }

    private val CardBrowserFragment.menu: Menu
        get() = searchBar!!.menu

    private suspend fun CardBrowserFragment.toggleRowAt(position: Int) {
        activityViewModel.toggleRowSelection(activityViewModel.cards[position].toRowSelection()).join()
        advanceRobolectricLooper()
    }

    private fun FragmentActivity.setFindAndReplaceResult(
        search: String,
        replacement: String,
    ) = supportFragmentManager.setFragmentResult(
        REQUEST_FIND_AND_REPLACE,
        Bundle().apply {
            putString(RESULT_SEARCH, search)
            putString(RESULT_REPLACEMENT, replacement)
            putString(RESULT_FIELD, ALL_FIELDS_AS_FIELD)
            putBoolean(RESULT_ONLY_SELECTED_NOTES, true)
            putBoolean(RESULT_MATCH_CASE, false)
            putBoolean(RESULT_REGEX, false)
        },
    )

    private fun withBrowseTab(block: suspend CardBrowserFragment.() -> Unit) =
        withPref(Prefs::devBottomNavEnabled, true) {
            withPref(Prefs::devUsingCardBrowserSearchView, true) {
                setIntroductionSlidesShown(true)
                runTest {
                    val activity = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get()
                    activity.binding.bottomNavigation!!.selectedItemId = NavigationItem.BROWSER.id
                    advanceRobolectricLooper()
                    val browser = activity.supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment
                    browser.activityViewModel.searchJob?.join()
                    advanceRobolectricLooper()
                    block(browser)
                }
            }
        }
}
