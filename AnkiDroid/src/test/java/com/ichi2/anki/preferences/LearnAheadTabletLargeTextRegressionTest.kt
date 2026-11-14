// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "sw600dp-w600dp-h960dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LearnAheadTabletLargeTextRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `three digit limits fit the actual tablet settings pane at large font sizes`() = checkTablet()

    @Test
    @Config(qualifiers = "be-sw600dp-w600dp-h960dp-mdpi")
    fun `translated units leave all digits visible in the narrow tablet pane`() = checkTablet()

    private fun checkTablet() =
        runTest {
            RuntimeEnvironment.setFontScale(2f)
            CollectionPreferences.setLearnAheadLimit(999.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, LearnAheadSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    val paneWidth = activity.findViewById<android.view.View>(R.id.settings_container).width
                    assertTrue("The real split settings pane must be narrower than 320dp", paneWidth in 1..319)
                }
                composeRule.waitForIdle()
                val input = composeRule.onNode(hasSetTextAction())
                val layouts = mutableListOf<TextLayoutResult>()
                input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                val inputBounds = input.fetchSemanticsNode().boundsInRoot
                val suffix = targetContext.resources.getQuantityString(CommonPlurals.timebox_minutes_suffix, 999)
                val suffixBounds = composeRule.onNodeWithText(suffix, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val suffixIsInline = suffixBounds.top < inputBounds.bottom && suffixBounds.bottom > inputBounds.top
                val availableWidth =
                    if (suffixIsInline) {
                        val horizontalInset = inputBounds.right - suffixBounds.right
                        suffixBounds.left - inputBounds.left - horizontalInset - 2f
                    } else {
                        // Material's text field reserves 16dp on each side at mdpi.
                        inputBounds.width - 32f
                    }
                val numberWidth = layout.getLineRight(0) - layout.getLineLeft(0)
                assertTrue(
                    "The complete number needs $numberWidth px, but only $availableWidth px are visible",
                    numberWidth <= availableWidth,
                )
            }
        }
}
