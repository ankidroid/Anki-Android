// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MorePageDownActivationTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `Enter during Page Down cannot open the offscreen Settings action`() =
        withPref(Prefs::devBottomNavEnabled, true) {
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

            composeRule.mainClock.autoAdvance = false
            assertTrue(deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN))
            composeRule.mainClock.advanceTimeUntil(1_000) {
                more.scrollState.value >= 100.dp.toPx(deckPicker)
            }
            settings.assertIsNotDisplayed()

            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            deckPicker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            assertNotEquals(
                PreferencesActivity::class.java.name,
                shadowOf(deckPicker).nextStartedActivity?.component?.className,
                "Enter must not open Settings after it has scrolled offscreen",
            )
        }
}
