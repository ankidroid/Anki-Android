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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreCtrlPagingTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `Ctrl End and Ctrl Home still page More`() = assertPaging(KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_MOVE_HOME)

    @Test
    fun `Ctrl Page Down and Ctrl Page Up still scroll More`() = assertPaging(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_PAGE_UP)

    private fun assertPaging(
        down: Int,
        up: Int,
    ) = withPref(Prefs::devBottomNavEnabled, true) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        setIntroductionSlidesShown(true)
        val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        checkNotNull(activity.binding.bottomNavigation).selectedItemId = R.id.nav_more
        advanceRobolectricLooper()
        val settings = composeRule.onNodeWithText(activity.getString(CommonString.settings))
        settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        settings.assertIsFocused()

        val scroll =
            (
                activity.supportFragmentManager.findFragmentByTag(
                    BottomNavController.NavigationItem.MORE.tag,
                ) as MoreFragment
            ).scrollState
        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

        fun press(keyCode: Int) {
            activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, ctrl))
            activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, ctrl))
            composeRule.waitForIdle()
            advanceRobolectricLooper()
        }
        press(down)
        assertTrue(scroll.value > 0, "Holding Ctrl must not disable paging down")
        press(up)
        assertEquals(0, scroll.value, "Holding Ctrl must not disable paging back up")
    }
}
