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
@Config(qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LearnAheadLargeTextRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `three digit limits remain readable at large font sizes`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                AnkiDroidTheme {
                    LearnAheadSettingsScreen(
                        isLoading = false,
                        isEnabled = true,
                        limit = LearnAheadLimit.fromMinutes(999)!!,
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
        val suffixBounds = composeRule.onNodeWithText("mins", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // The field has matching start/end padding; the suffix marks the numeric viewport's end.
        val horizontalInset = inputBounds.right - suffixBounds.right
        val availableWidth = suffixBounds.left - inputBounds.left - horizontalInset
        val numberWidth = layout.getLineRight(0) - layout.getLineLeft(0)
        assertTrue("The complete number needs $numberWidth px, but only $availableWidth px are visible", numberWidth <= availableWidth)
    }
}
