// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.windowInsetsOf
import com.ichi2.testutils.withPref
import io.mockk.every
import io.mockk.mockkStatic
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationViewportTest : RobolectricTest() {
    @Test
    fun `hidden navigation lets browser rows draw behind the system gesture bar`() =
        runTest {
            repeat(80) { addBasicNote("Card $it", "Answer") }
            withPref(Prefs::devBottomNavEnabled, true) {
                withPref(Prefs::devUsingCardBrowserSearchView, true) {
                    setIntroductionSlidesShown(true)
                    val activity = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get()
                    val nav = activity.binding.bottomNavigation!!
                    nav.selectedItemId = NavigationItem.BROWSER.id
                    advanceRobolectricLooper()
                    val list = activity.findViewById<RecyclerView>(R.id.card_browser_list)
                    list.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    list.scrollToPosition(40)
                    advanceRobolectricLooper()
                    activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)
                    val insets = with(activity) { windowInsetsOf(navBarBottom = 24.dp, bottomCornerRadius = 48.dp) }
                    // Robolectric does not retain manually dispatched insets as the window's root insets.
                    mockkStatic(ViewCompat::class) {
                        every { ViewCompat.getRootWindowInsets(nav) } returns insets
                        HideViewOnScrollBehavior.from(nav).slideOut(nav)
                        advanceRobolectricLooper()
                        val container = activity.binding.bottomNavFragmentContainer!!
                        val root = activity.binding.root
                        assertThat(container.topOnScreen + container.height, equalTo(root.topOnScreen + root.height))
                        assertThat("resting rows still clear the corners", list.paddingBottom, equalTo(48.dp.toPx(activity)))
                    }
                }
            }
        }

    private val View.topOnScreen: Int
        get() = IntArray(2).also(::getLocationOnScreen)[1]
}
