// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ichi2.anki.ComposeScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

class MessageDialogScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun confirmation() {
        capture(name = "confirmation", node = isDialog()) {
            MessageDialog(
                title = "Delete item",
                message = "Delete this item and its children? This action cannot be undone.",
                confirmText = "Delete",
                onConfirm = {},
                onDismissRequest = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land-mdpi")
    fun largeTextInLandscape() {
        capture(
            name = "large_text_landscape",
            node = isDialog(),
            fontScale = 2f,
            beforeCapture = {
                composeRule.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                composeRule.onNodeWithText("Delete").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            },
        ) {
            MessageDialog(
                title = "Delete item",
                message = "This item and its children will be permanently deleted. This action cannot be undone. ".repeat(8),
                confirmText = "Delete",
                onConfirm = {},
                onDismissRequest = {},
            )
        }
    }
}
