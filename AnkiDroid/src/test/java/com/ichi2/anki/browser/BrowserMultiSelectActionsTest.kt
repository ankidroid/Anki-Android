// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.view.Menu
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
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

    private val CardBrowserFragment.menu: Menu
        get() = searchBar!!.menu

    private suspend fun CardBrowserFragment.toggleRowAt(position: Int) {
        activityViewModel.toggleRowSelection(activityViewModel.cards[position].toRowSelection()).join()
        advanceRobolectricLooper()
    }

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
