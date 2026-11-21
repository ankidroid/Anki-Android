// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MorePagingFocusChangeTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `moving focus to the bottom tabs during Page Up keeps focus there`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            val feedback = composeRule.onNodeWithText(deckPicker.getString(CommonString.send_feedback))
            feedback.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            feedback.assertIsFocused()
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            composeRule.mainClock.autoAdvance = false
            val browse = bottomNav.findViewById<View>(R.id.nav_browser)
            assertTrue(deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP)))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_UP))
            assertTrue(browse.requestFocus())
            assertTrue(browse.isFocused)
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            assertTrue(browse.isFocused, "Finishing Page Up must preserve the newer keyboard focus on Browse")
        }
}
