// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isFocused
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreEndAfterHiddenTabsTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `End reaches the bottom after restoring hidden tabs`() = assertDestination(KeyEvent.KEYCODE_MOVE_END)

    @Test
    fun `Page Down leaves its focused destination visible after restoring hidden tabs`() = assertDestination(KeyEvent.KEYCODE_PAGE_DOWN)

    @Test
    fun `an unrelated key during End keeps the focused row visible after the tabs return`() =
        assertDestination(KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_Z)

    private fun assertDestination(vararg keyCodes: Int) =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            checkNotNull(activity.binding.bottomNavigation).selectedItemId = R.id.nav_more
            advanceRobolectricLooper()
            val settings = composeRule.onNodeWithText(activity.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            val bottomNav = checkNotNull(activity.binding.bottomNavigation)
            val behavior = HideViewOnScrollBehavior.from(bottomNav)
            behavior.slideOut(bottomNav, false)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertTrue(behavior.isScrolledOut)
            val more = activity.supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment
            val hiddenMaximum = more.scrollState.maxValue
            val interrupted = keyCodes.size > 1
            if (interrupted) composeRule.mainClock.autoAdvance = false
            for (keyCode in keyCodes) {
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            if (interrupted) composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            val viewport = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            assertTrue(behavior.isScrolledIn)
            val focused = composeRule.onNode(isFocused()).fetchSemanticsNode()
            if (interrupted) composeRule.onNodeWithText(activity.getString(CommonString.send_feedback)).assertIsFocused()
            val top = focused.positionInRoot.y
            assertTrue(
                top >= viewport.top && top + focused.size.height <= viewport.bottom,
                "The paging destination must fit in the restored viewport",
            )
            assertTrue(more.scrollState.maxValue > hiddenMaximum)
            if (keyCodes.first() == KeyEvent.KEYCODE_MOVE_END) {
                assertEquals(more.scrollState.maxValue, more.scrollState.value, "End must reach the end using the restored viewport")
            }
        }

    @Test
    @Config(qualifiers = "w360dp-h208dp")
    fun `Page Down keeps its focused row visible if the viewport is shorter than the row`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            checkNotNull(activity.binding.bottomNavigation).selectedItemId = R.id.nav_more
            advanceRobolectricLooper()
            val settings = composeRule.onNodeWithText(activity.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            val bottomNav = checkNotNull(activity.binding.bottomNavigation)
            val behavior = HideViewOnScrollBehavior.from(bottomNav)
            behavior.slideOut(bottomNav, false)
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertTrue(behavior.isScrolledOut)
            val body = composeRule.onNode(hasScrollAction())
            val expanded = body.fetchSemanticsNode().boundsInRoot.height
            assertTrue(expanded in 135f..149f, "The larger viewport was $expanded")
            settings.assertIsFocused()

            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN))
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            assertTrue(behavior.isScrolledIn)
            val destination = composeRule.onNodeWithText(activity.getString(CommonString.help_title_get_help))
            destination.assertIsFocused()
            val smaller = body.fetchSemanticsNode().boundsInRoot.height
            assertTrue(smaller < destination.fetchSemanticsNode().size.height, "The smaller viewport was $smaller")
            destination.assertIsDisplayed()
        }
}
