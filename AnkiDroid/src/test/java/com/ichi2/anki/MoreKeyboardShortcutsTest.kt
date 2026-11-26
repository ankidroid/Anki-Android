// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreKeyboardShortcutsTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun launchMore(): DeckPicker {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        setIntroductionSlidesShown(true)
        val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        checkNotNull(deckPicker.binding.bottomNavigation).selectedItemId = R.id.nav_more
        advanceRobolectricLooper()
        return deckPicker
    }

    private fun DeckPicker.press(
        keyCode: Int,
        metaState: Int = 0,
    ) {
        val time = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private val DeckPicker.moreScrollState
        get() = (supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment).scrollState

    @Test
    fun `Alt+Down and Alt+Up jump to the ends of More`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            val deckPicker = launchMore()
            val scroll = deckPicker.moreScrollState
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            deckPicker.press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertEquals(scroll.maxValue, scroll.value, "Alt+Down scrolls to the end")
            composeRule.onNodeWithText(deckPicker.getString(CommonString.send_feedback)).assertIsFocused()

            deckPicker.press(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertEquals(0, scroll.value, "Alt+Up scrolls to the top")
            settings.assertIsFocused()
        }

    @Test
    fun `Ctrl+Space and Alt+Space page down`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            val deckPicker = launchMore()
            val scroll = deckPicker.moreScrollState
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            deckPicker.press(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val afterCtrl = scroll.value
            assertTrue(afterCtrl > 0, "Ctrl+Space pages down")

            deckPicker.press(KeyEvent.KEYCODE_SPACE, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertTrue(scroll.value > afterCtrl, "Alt+Space pages down")
            assertNull(shadowOf(deckPicker).nextStartedActivity, "modified Space must not open the focused action")
        }

    @Test
    fun `Tab during Page Down shows the row it moves to`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            val deckPicker = launchMore()
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            composeRule.mainClock.autoAdvance = false
            deckPicker.press(KeyEvent.KEYCODE_PAGE_DOWN)
            deckPicker.press(KeyEvent.KEYCODE_TAB)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            val list = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val row = composeRule.onNode(isFocused()).fetchSemanticsNode()
            val top = row.positionInRoot.y
            assertTrue(top >= list.top && top + row.size.height <= list.bottom, "The row Tab moved to must be fully shown")
        }

    @Test
    fun `Ctrl+Enter during Page Down cannot open the offscreen Settings action`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            val deckPicker = launchMore()
            val scroll = deckPicker.moreScrollState
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            composeRule.mainClock.autoAdvance = false
            deckPicker.press(KeyEvent.KEYCODE_PAGE_DOWN)
            composeRule.mainClock.advanceTimeUntil(1_000) { scroll.value >= 100.dp.toPx(deckPicker) }
            settings.assertIsNotDisplayed()

            deckPicker.press(KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            assertNotEquals(
                PreferencesActivity::class.java.name,
                shadowOf(deckPicker).nextStartedActivity?.component?.className,
                "Ctrl+Enter must not open Settings after it has scrolled offscreen",
            )
        }

    @Test
    @Config(qualifiers = "w360dp-h300dp")
    fun `Shift+Tab during Page Down keeps its row shown when the tabs return`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            val deckPicker = launchMore()
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            val behavior = HideViewOnScrollBehavior.from(bottomNav)
            behavior.slideOut(bottomNav, false)
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            composeRule.mainClock.autoAdvance = false
            deckPicker.press(KeyEvent.KEYCODE_PAGE_DOWN)
            deckPicker.press(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            assertTrue(behavior.isScrolledIn)
            composeRule.onNodeWithText(deckPicker.getString(CommonString.help_title_community)).assertIsFocused()
            val list = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val row = composeRule.onNode(isFocused()).fetchSemanticsNode()
            val top = row.positionInRoot.y
            assertTrue(top >= list.top && top + row.size.height <= list.bottom, "The row Shift+Tab moved to must stay fully shown")
        }

    @Test
    @SuppressLint("InlinedApi")
    fun `Tab right after a rotary scroll shows its row`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            val deckPicker = launchMore()
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            val time = SystemClock.uptimeMillis()
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val coordinates = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_SCROLL, -4f) }
            val event =
                MotionEvent.obtain(
                    time,
                    time,
                    MotionEvent.ACTION_SCROLL,
                    1,
                    arrayOf(properties),
                    arrayOf(coordinates),
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    InputDevice.SOURCE_ROTARY_ENCODER,
                    0,
                )
            composeRule.mainClock.autoAdvance = false
            try {
                deckPicker.dispatchGenericMotionEvent(event)
            } finally {
                event.recycle()
            }
            deckPicker.press(KeyEvent.KEYCODE_TAB)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            composeRule.onNodeWithText(deckPicker.getString(CommonString.help_title_using_ankidroid)).assertIsFocused()
            val list = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val row = composeRule.onNode(isFocused()).fetchSemanticsNode()
            val top = row.positionInRoot.y
            assertTrue(top >= list.top && top + row.size.height <= list.bottom, "The row Tab moved to must be fully shown")
        }
}
