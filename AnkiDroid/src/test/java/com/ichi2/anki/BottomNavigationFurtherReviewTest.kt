// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.databinding.FragmentMoreBinding
import com.ichi2.testutils.withBooleanPreference
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationFurtherReviewTest : RobolectricTest() {
    @Test
    fun `More focused action stays above the visible bottom bar`() =
        withNavigation { picker ->
            val nav = picker.binding.bottomNavigation!!
            nav.selectedItemId = NavigationItem.MORE.id
            advanceRobolectricLooper()
            val view = picker.supportFragmentManager.findFragmentByTag(NavigationItem.MORE.tag)!!.requireView()
            val binding = FragmentMoreBinding.bind(view)
            val scroll = binding.moreScrollView
            scroll.scrollTo(0, 0)
            val action = binding.moreSupportFeedback
            assertThat("The production action must accept keyboard focus", action.requestFocus(), equalTo(true))
            assertThat("The target must now have focus", action.hasFocus(), equalTo(true))
            advanceRobolectricLooper()
            assertThat("The bottom navigation is still visible", HideViewOnScrollBehavior.from(nav).isScrolledIn, equalTo(true))
            val actionBottom = action.yInWindow + action.height
            assertThat(
                "Focused action ends at $actionBottom while the bar starts at ${nav.yInWindow}; " +
                    "scrollY=${scroll.scrollY}, padding=${scroll.paddingBottom}",
                actionBottom <= nav.yInWindow,
                equalTo(true),
            )
        }

    private val View.yInWindow: Int
        get() = IntArray(2).also(::getLocationInWindow)[1]

    private fun withNavigation(test: (DeckPicker) -> Unit) =
        withBooleanPreference(R.string.dev_bottom_nav_key, true) {
            setIntroductionSlidesShown(true)
            test(startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get())
        }
}
