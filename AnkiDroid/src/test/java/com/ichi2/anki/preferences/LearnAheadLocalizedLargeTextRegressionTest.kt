// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "be-w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LearnAheadLocalizedLargeTextRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Belarusian many suffix leaves the complete three digit limit readable`() = checkLimit(999)

    @Test
    fun `Belarusian singular suffix leaves the complete three digit limit readable`() = checkLimit(101)

    @Test
    fun `Belarusian few suffix leaves the complete three digit limit readable`() = checkLimit(102)

    private fun checkLimit(minutes: Int) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                AnkiDroidTheme {
                    LearnAheadSettingsScreen(
                        isLoading = false,
                        isEnabled = true,
                        limit = LearnAheadLimit.fromMinutes(minutes)!!,
                        onToggleEnabled = {},
                        onSetMinutes = {},
                        onNavigateUp = {},
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.minus_sign)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        val inputBounds = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val suffixString = targetContext.resources.getQuantityString(CommonPlurals.timebox_minutes_suffix, minutes)
        val suffixBounds =
            composeRule
                .onNodeWithText(suffixString, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        // At mdpi, Material's 2dp suffix gap takes 2px; the remaining field padding is symmetric.
        val horizontalInset = inputBounds.right - suffixBounds.right
        val availableWidth = suffixBounds.left - inputBounds.left - horizontalInset - 2f
        val numberWidth = layout.getLineRight(0) - layout.getLineLeft(0)
        assertTrue("The complete number needs $numberWidth px, but only $availableWidth px are visible", numberWidth <= availableWidth)
    }
}
