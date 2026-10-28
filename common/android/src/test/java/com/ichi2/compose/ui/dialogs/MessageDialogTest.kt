// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun `long confirmation scrolls without shrinking the buttons at large font sizes`() {
        RuntimeEnvironment.setFontScale(2f)
        var confirmed = false
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog(
                    title = "Delete item",
                    message = "This item and its children will be permanently deleted. This action cannot be undone. ".repeat(8),
                    confirmText = "Delete",
                    onConfirm = { confirmed = true },
                    onDismissRequest = {},
                )
            }
        }
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val confirm = compose.onNodeWithText("Delete").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val message = compose.onNode(hasScrollAction())
        val scroll = message.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.maxValue() > 0)
        message.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(scroll.value() > 0)
        confirm.performTouchInput { click() }
        assertTrue(confirmed)
    }

    @Test
    fun `custom dismiss action stays available while confirmation is disabled`() {
        val enabled = mutableStateOf(false)
        var confirmed = false
        var dismissed = 0
        compose.setContent {
            AnkiDroidPreview {
                MessageDialog(
                    title = "Could not load items",
                    message = "Check your connection and try again.",
                    confirmText = "Retry",
                    dismissText = "Close",
                    onConfirm = { confirmed = true },
                    onDismissRequest = { dismissed++ },
                    enabled = enabled.value,
                )
            }
        }

        val retry = compose.onNodeWithText("Retry").assertIsNotEnabled()
        retry.performClick()
        assertFalse(confirmed)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        assertEquals(1, dismissed)
        assertFalse(confirmed)

        compose.runOnIdle { enabled.value = true }
        retry.assertIsEnabled().performClick()
        assertTrue(confirmed)
        assertEquals(1, dismissed)
    }
}
