// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.os.Bundle
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Browser actions which open a dialog and then act on the result it reports back.
 *
 * The result listeners must belong to [CardBrowserFragment], so that the actions keep working
 * wherever the fragment is hosted: both the standalone [com.ichi2.anki.CardBrowser] and the
 * bottom navigation's Browse tab.
 *
 * https://github.com/ankidroid/Anki-Android/issues/23277
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardBrowserActionResultTest : RobolectricTest() {
    /** Whether the browser is the bottom navigation's Browse tab, rather than its own activity */
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var embedded: Boolean = false

    @Test
    @Config(qualifiers = "en")
    fun `reset progress applies to the selected card`() {
        val cardId =
            addBasicNote("Hello", "World")
                .firstCard()
                .update {
                    due = 5
                    queue = QueueType.Rev
                    type = CardType.Rev
                }.id

        withBrowser {
            selectRowAt(0)

            // ForgetCardsDialog sends exactly this once the user confirms 'Reset'
            parentFragmentManager.setFragmentResult(
                ForgetCardsDialog.REQUEST_KEY_FORGET,
                Bundle().apply {
                    putBoolean(ForgetCardsDialog.RESULT_RESTORE_ORIGINAL, true)
                    putBoolean(ForgetCardsDialog.RESULT_RESET_REPETITION, false)
                },
            )
            advanceRobolectricLooper()
        }

        assertEquals(CardType.New, col.getCard(cardId).type, "the card is reset to new")
    }

    private suspend fun CardBrowserFragment.selectRowAt(position: Int) {
        activityViewModel.toggleRowSelection(activityViewModel.cards[position].toRowSelection()).join()
        advanceRobolectricLooper()
        assertNotEquals(0, activityViewModel.selectedRowCount(), "a row is selected")
    }

    private fun withBrowser(block: suspend CardBrowserFragment.() -> Unit) =
        if (embedded) {
            withPref(Prefs::devBottomNavEnabled, true) {
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
        } else {
            withCardBrowserFragment {
                activityViewModel.searchJob?.join()
                advanceRobolectricLooper()
                block()
            }
        }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "embedded={0}")
        @JvmStatic
        fun parameters() = listOf(arrayOf(false), arrayOf(true))
    }
}
