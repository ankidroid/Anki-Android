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
@Config(qualifiers = "w360dp-h410dp")
class MoreRepeatedPagingTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `two quick Page Down presses advance two pages in More`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            fun pressPageDown() {
                deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN))
                deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN))
            }

            val more = deckPicker.supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment
            val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            val scroll = more.scrollState
            assertEquals(0, scroll.value)
            val onePage = scroll.viewportSize
            val twoPages = minOf(onePage * 2, scroll.maxValue)
            assertTrue(twoPages > onePage)

            composeRule.mainClock.autoAdvance = false
            pressPageDown()
            composeRule.mainClock.advanceTimeBy(64)
            pressPageDown()
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            assertEquals(twoPages, scroll.value, "Both Page Down presses should contribute a page")
        }
}
