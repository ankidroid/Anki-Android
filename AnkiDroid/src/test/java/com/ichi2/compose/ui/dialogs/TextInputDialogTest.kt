// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.profiles.RenameProfileDialog
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextInputDialogTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the length counter uses the given length function`() {
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Title",
                    label = "Label",
                    confirmText = "OK",
                    onConfirm = {},
                    onDismissRequest = {},
                    maxLengthCounter = 3,
                    lengthOf = { it.trim().length },
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextReplacement(" abc ")

        compose.onNodeWithText("3/3").assertIsDisplayed()
    }

    @Test
    fun `initial selection can place the cursor at the start`() {
        assertInitialSelection("Existing", TextRange.Zero)
    }

    @Test
    fun `initial selection can select all text`() {
        val text = "Existing"
        assertInitialSelection(text, TextRange(0, text.length))
    }

    @Test
    fun `the cursor starts at the end of the profile name`() {
        val name = "Work"
        compose.setContent {
            AnkiDroidTheme {
                RenameProfileDialog(currentName = name, existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
            }
        }

        val field = compose.onNode(hasSetTextAction())
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(name.length)))
    }

    @Test
    fun `edited text and cursor survive state restoration`() {
        assertSelectionRestored(TextRange(3))
    }

    @Test
    fun `edited text and selected range survive state restoration`() {
        assertSelectionRestored(TextRange(1, 5))
    }

    @Test
    fun `changing selection does not reveal validation errors before editing`() {
        val validationError = "Name already exists"
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Rename",
                    initialText = "Existing",
                    errorMessageFor = { validationError },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText(validationError).assertDoesNotExist()

        for (selection in listOf(TextRange(2), TextRange(1, 4))) {
            field.performTextInputSelection(selection)
            field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
            compose.onNodeWithText(validationError).assertDoesNotExist()
        }

        field.performTextReplacement("Changed")
        compose.onNodeWithText(validationError).assertIsDisplayed()
    }

    private fun assertInitialSelection(
        text: String,
        selection: TextRange,
    ) {
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Rename",
                    initialText = text,
                    initialSelection = selection,
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }

    private fun assertSelectionRestored(selection: TextRange) {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AnkiDroidTheme {
                RenameProfileDialog(currentName = "Work", existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("Personal")
        field.performTextInputSelection(selection)

        restoration.emulateSavedInstanceStateRestore()

        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Personal")))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }
}
