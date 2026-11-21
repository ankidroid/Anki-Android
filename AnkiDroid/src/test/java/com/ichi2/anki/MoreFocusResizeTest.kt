// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.updateLayoutParams
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
@Config(qualifiers = "w360dp-h760dp")
class MoreFocusResizeTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `shrinking the More viewport around a focused action keeps the bottom tabs visible`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            val more = deckPicker.supportFragmentManager.findFragmentByTag(BottomNavController.NavigationItem.MORE.tag) as MoreFragment
            val privacy = composeRule.onNodeWithText(deckPicker.getString(CommonString.help_title_privacy))
            privacy.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            privacy.assertIsFocused()
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertEquals(0, more.scrollState.value, "The focused action fits before the viewport shrinks")

            val container = checkNotNull(deckPicker.binding.bottomNavFragmentContainer)
            val originalHeight = container.height
            container.updateLayoutParams { height = originalHeight / 3 }
            advanceRobolectricLooper()
            composeRule.waitForIdle()
            advanceRobolectricLooper()
            assertTrue(container.height < originalHeight, "The viewport must shrink")
            privacy.assertIsFocused()
            assertTrue(more.scrollState.value > 0, "The focused action must scroll into the resized viewport")

            assertTrue(HideViewOnScrollBehavior.from(bottomNav).isScrolledIn, "The bottom tabs must remain available")
        }
}
