// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.settings.enums.Theme
import org.junit.Test
import org.robolectric.annotation.Config

class CustomSyncCertificateDialogScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun editor(
        @TestParameter(valuesProvider = ThemeProvider::class) theme: Theme,
        @TestParameter populated: Boolean,
    ) {
        capture(
            name = if (populated) "populated" else "empty",
            theme = theme,
            node = isDialog(),
            beforeCapture = ::assertActionsVisible,
        ) {
            CustomSyncCertificateDialog(
                initialText = if (populated) SAMPLE_PEM else "",
                onConfirm = {},
                onDismissRequest = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun largeTextInLandscape() {
        capture(
            name = "large_text_landscape",
            node = isDialog(),
            fontScale = 2f,
            beforeCapture = {
                composeRule.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
                assertActionsVisible()
            },
        ) {
            CustomSyncCertificateDialog(initialText = SAMPLE_PEM, onConfirm = {}, onDismissRequest = {})
        }
    }

    private fun assertActionsVisible() {
        composeRule.onNodeWithText("Save").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    companion object {
        // Representative multiline content for layout tests; backend validation is covered separately.
        private val SAMPLE_PEM = "-----BEGIN CERTIFICATE-----\n${"VGhpcyBpcyBhIHRlc3Q=\n".repeat(8)}-----END CERTIFICATE-----"
    }
}
