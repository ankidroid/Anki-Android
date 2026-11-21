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
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreHomeEndPagingTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `Home and End page through More`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            fun press(keyCode: Int) {
                deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }

            val more = deckPicker.supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()
            val top = more.scrollState.value

            press(KeyEvent.KEYCODE_MOVE_END)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val endScrolled = more.scrollState.value > top

            val feedback = composeRule.onNodeWithText(deckPicker.getString(CommonString.send_feedback))
            feedback.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            feedback.assertIsFocused()
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val bottom = more.scrollState.value
            assertTrue(bottom > top)

            press(KeyEvent.KEYCODE_MOVE_HOME)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val homeScrolled = more.scrollState.value < bottom

            assertTrue(endScrolled && homeScrolled, "End paged down: $endScrolled. Home paged up: $homeScrolled.")
        }
}
