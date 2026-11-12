// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.search.AdvancedSearchFragment
import com.ichi2.anki.browser.search.AdvancedSearchFragment.SelectAdvancedSearchFragment
import com.ichi2.anki.browser.search.StandardSearchFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardBrowserBottomNavigationSearchTest : RobolectricTest() {
    @Test
    fun `last advanced search option clears the visible bottom navigation`() =
        runTest {
            withPref(Prefs::devBottomNavEnabled, true) {
                withPref(Prefs::devUsingCardBrowserSearchView, true) {
                    setIntroductionSlidesShown(true)
                    val picker = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get()
                    val navigation = assertNotNull(picker.binding.bottomNavigation)
                    navigation.selectedItemId = NavigationItem.BROWSER.id
                    advanceRobolectricLooper()
                    val browser = picker.supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment
                    assertNotNull(browser.searchView).show()
                    advanceRobolectricLooper()
                    val standard = browser.childFragmentManager.findFragmentByTag(StandardSearchFragment.TAG) as StandardSearchFragment
                    standard.requireView().findViewById<View>(R.id.toggle_advanced_search).performClick()
                    advanceRobolectricLooper()
                    val advanced = browser.childFragmentManager.findFragmentByTag(AdvancedSearchFragment.TAG) as AdvancedSearchFragment
                    advanced.requireView().findViewById<ViewPager2>(R.id.view_pager).setCurrentItem(8, false)
                    advanceRobolectricLooper()
                    val options = advanced.childFragmentManager.findFragmentByTag("f8") as SelectAdvancedSearchFragment
                    val list = options.requireView() as RecyclerView
                    val lastPosition = assertNotNull(list.adapter).itemCount - 1
                    list.scrollToPosition(lastPosition)
                    advanceRobolectricLooper()
                    list.scrollBy(0, list.height)
                    advanceRobolectricLooper()
                    val lastOption = assertNotNull(list.findViewHolderForAdapterPosition(lastPosition)).itemView

                    assertTrue(lastOption.isShown, "The final production search option must be displayed")
                    assertFalse(list.canScrollVertically(1), "The options must be scrolled to their end")
                    assertTrue(HideViewOnScrollBehavior.from(navigation).isScrolledIn, "The bottom navigation must remain visible")
                    assertTrue(
                        lastOption.bottomInWindow <= navigation.topInWindow,
                        "The last search option ends at ${lastOption.bottomInWindow}, below navigation at ${navigation.topInWindow}",
                    )
                }
            }
        }

    private val View.topInWindow: Int
        get() = IntArray(2).also(::getLocationInWindow)[1]

    private val View.bottomInWindow: Int
        get() = topInWindow + height
}
