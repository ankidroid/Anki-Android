// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreArrowUpBottomTabsTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `D-pad Up brings the hidden bottom tabs back`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            val more = deckPicker.supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment
            val feedback = composeRule.onNodeWithText(deckPicker.getString(CommonString.send_feedback))
            feedback.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            feedback.assertIsFocused()
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val behavior = HideViewOnScrollBehavior.from(bottomNav)
            behavior.slideOut(bottomNav, false)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertTrue(behavior.isScrolledOut, "The bottom tabs start hidden")
            val initialScroll = more.scrollState.value
            assertTrue(initialScroll > 0, "The list starts away from the top")

            repeat(12) {
                deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
                composeRule.waitForIdle()
                advanceRobolectricLooper()
            }

            assertTrue(more.scrollState.value < initialScroll, "D-pad Up must scroll towards the top")
            assertTrue(behavior.isScrolledIn, "Navigating upwards must make the bottom tabs available to the keyboard")
        }
}
