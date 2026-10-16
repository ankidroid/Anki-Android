// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.CommonString
import com.ichi2.anki.noteeditor.presentation.ToolbarLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolbarLayoutDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `layout choices announce buttons and report the chosen layout`() {
        var chosen: ToolbarLayoutMode? = null
        composeRule.setContent {
            MaterialTheme {
                ToolbarLayoutDialog(selected = ToolbarLayoutMode.SCROLL, onSelect = { chosen = it }, onDismiss = {})
            }
        }

        ToolbarLayoutMode.entries.forEach { layout ->
            composeRule
                .onNodeWithText(label(layout))
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            composeRule.runOnIdle { assertEquals(layout, chosen) }
        }
    }

    @Test
    fun `preview renders the title and all choices inside its host`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme {
                    Box(Modifier.testTag("preview")) {
                        ToolbarLayoutDialog(selected = ToolbarLayoutMode.SCROLL, onSelect = {}, onDismiss = {})
                    }
                }
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = listOf(CommonString.note_editor_toolbar_layout) + ToolbarLayoutMode.entries.map { it.label() }
        labels.forEach { label ->
            composeRule
                .onNodeWithText(context.getString(label))
                .assertIsDisplayed()
                .assert(hasAnyAncestor(hasTestTag("preview")))
        }
        composeRule.onNodeWithText(context.getString(ToolbarLayoutMode.SCROLL.label())).assertIsSelected()
    }

    @Test
    fun `preview choices without selection backgrounds do not shade on hover`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme {
                    ToolbarLayoutDialog(
                        selected = ToolbarLayoutMode.SCROLL,
                        onSelect = {},
                        onDismiss = {},
                        showSelectionBackground = false,
                    )
                }
            }
        }

        val choice = composeRule.onNodeWithText(label(ToolbarLayoutMode.SCROLL))
        val beforeHover = choice.captureToImage().asAndroidBitmap()
        choice.performMouseInput { moveTo(center) }
        composeRule.mainClock.advanceTimeBy(200)
        val afterHover = choice.captureToImage().asAndroidBitmap()
        assertTrue("Hover must not shade the selection or its sample", beforeHover.sameAs(afterHover))
    }

    @Test
    fun `large text keeps all choices visible on a regular phone`() {
        showLargeTextDialog()
        assertChoiceLabelsDisplayed()
        composeRule.onNodeWithText(label(ToolbarLayoutMode.HIDDEN)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun `large text keeps all choices visible on a small phone`() {
        showLargeTextDialog()
        assertChoiceLabelsDisplayed()
        composeRule.onNodeWithText(label(ToolbarLayoutMode.HIDDEN)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w320dp-h240dp-mdpi")
    fun `short windows reveal the selected choice and allow scrolling to other choices`() {
        var selected: ToolbarLayoutMode? = null
        showLargeTextDialog { selected = it }
        composeRule.onNodeWithText(label(ToolbarLayoutMode.HIDDEN), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(label(ToolbarLayoutMode.HIDDEN)).assertIsSelected()
        composeRule.onNodeWithText(label(ToolbarLayoutMode.SCROLL)).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ToolbarLayoutMode.SCROLL, selected) }
    }

    private fun showLargeTextDialog(onSelect: (ToolbarLayoutMode) -> Unit = {}) {
        RuntimeEnvironment.setFontScale(2f)
        composeRule.setContent {
            MaterialTheme {
                ToolbarLayoutDialog(selected = ToolbarLayoutMode.HIDDEN, onSelect = onSelect, onDismiss = {})
            }
        }
    }

    private fun assertChoiceLabelsDisplayed() {
        ToolbarLayoutMode.entries.forEach {
            composeRule.onNodeWithText(label(it), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun label(layout: ToolbarLayoutMode) = ApplicationProvider.getApplicationContext<Context>().getString(layout.label())
}
