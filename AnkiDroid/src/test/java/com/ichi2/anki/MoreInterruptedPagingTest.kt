// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
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

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreInterruptedPagingTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `an unrelated key during End leaves the focused action visible`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            checkNotNull(activity.binding.bottomNavigation).selectedItemId = R.id.nav_more
            advanceRobolectricLooper()
            val settings = composeRule.onNodeWithText(activity.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            composeRule.mainClock.autoAdvance = false
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MOVE_END))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z))
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val feedback = composeRule.onNodeWithText(activity.getString(CommonString.send_feedback))
            feedback.assertIsFocused()
            feedback.assertIsDisplayed()
        }
}
