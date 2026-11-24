// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreReducedMotionPagingTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `Remove animations makes keyboard paging immediate`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            editPreferences { putBoolean(targetContext.getString(R.string.safe_display_key), true) }
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            val more = deckPicker.supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()
            assertTrue(Animations.areSystemAnimationsEnabled(deckPicker))
            assertTrue(!Animations.areAnimationsEnabled(deckPicker))
            composeRule.mainClock.autoAdvance = false
            val time = SystemClock.uptimeMillis()
            val alt = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            deckPicker.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0, alt))
            deckPicker.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN, 0, alt))
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals(more.scrollState.maxValue, more.scrollState.value, "Remove animations must not show intermediate paging frames")
            composeRule.mainClock.autoAdvance = true
        }

    @Test
    fun `Remove animations makes focus scrolling immediate`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            editPreferences { putBoolean(targetContext.getString(R.string.safe_display_key), true) }
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            checkNotNull(deckPicker.binding.bottomNavigation).selectedItemId = R.id.nav_more
            advanceRobolectricLooper()
            assertTrue(Animations.areSystemAnimationsEnabled(deckPicker))
            assertTrue(!Animations.areAnimationsEnabled(deckPicker))
            val more = deckPicker.supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()
            composeRule.mainClock.autoAdvance = false
            val feedback = composeRule.onNodeWithText(deckPicker.getString(CommonString.send_feedback))
            feedback.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            feedback.assertIsFocused()
            composeRule.mainClock.advanceTimeByFrame()
            val firstFrame = more.scrollState.value
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertTrue(more.scrollState.value > 0)
            assertEquals(more.scrollState.value, firstFrame, "Remove animations must also disable focus-driven scrolling")
        }
}
