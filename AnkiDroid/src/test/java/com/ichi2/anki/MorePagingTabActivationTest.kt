// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MorePagingTabActivationTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `Tab during Page Down cannot leave Settings focused offscreen`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            composeRule.mainClock.autoAdvance = false
            assertTrue(deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB))
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val settingsVisible = settings.isDisplayed()

            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            assertTrue(
                settingsVisible || shadowOf(deckPicker).nextStartedActivity?.component?.className != PreferencesActivity::class.java.name,
                "Tab followed by Enter must not open Settings after paging scrolls it offscreen",
            )
        }

    @Test
    fun `Tab after Alt Up advances from Settings to Using AnkiDroid`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            checkNotNull(deckPicker.binding.bottomNavigation).selectedItemId = R.id.nav_more
            advanceRobolectricLooper()
            val feedback = composeRule.onNodeWithText(deckPicker.getString(CommonString.send_feedback))
            feedback.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            feedback.assertIsFocused()
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            composeRule.mainClock.autoAdvance = false
            val time = SystemClock.uptimeMillis()
            val alt = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            deckPicker.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0, alt))
            deckPicker.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP, 0, alt))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB))
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            advanceRobolectricLooper()
            val help =
                deckPicker.supportFragmentManager.fragments
                    .filterIsInstance<HelpDialog>()
                    .singleOrNull()
            val title = help?.arguments?.getInt(HelpDialog.ARG_MENU_TITLE)
            help?.dismissNow()
            advanceRobolectricLooper()
            assertEquals(
                CommonString.help_title_using_ankidroid,
                title,
                "Tab must advance from the paging destination, not select Settings again",
            )
        }
}
