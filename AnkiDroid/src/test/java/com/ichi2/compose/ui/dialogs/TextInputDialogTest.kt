// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.profiles.RenameProfileDialog
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TextInputDialogTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `field semantics retain the label and editable value`() {
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename tag",
                    label = "Tag name",
                    confirmText = "Rename",
                    initialText = "History",
                    initialSelection = TextRange(1, 3),
                    errorMessageFor = { if (it.isBlank()) "Name required" else null },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }

        val field = compose.onNode(hasSetTextAction())
        field.assertTextContains("Tag name")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("History")))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(1, 3)))
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("text_input_label", useUnmergedTree = true).assertTextEquals("Tag name")

        field.performTextReplacement("")

        field.assertTextContains("Tag name")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithText("Name required").assertIsDisplayed()
    }

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
    fun `input transformation runs only for text edits`() {
        var transformations = 0
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    initialText = "Initial",
                    transformInput = {
                        transformations++
                        it.copy(text = it.text.uppercase())
                    },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInputSelection(TextRange.Zero)
        assertEquals(0, transformations)

        field.performTextReplacement("mixed")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("MIXED")))
        assertEquals(1, transformations)

        field.performTextInputSelection(TextRange(1, 3))
        assertEquals(1, transformations)
    }

    @Test
    fun `deferred validation rejects invalid submission and allows retry`() {
        var validations = 0
        var submitted: String? = null
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    initialText = "Invalid",
                    validateOnInput = false,
                    errorMessageFor = {
                        validations++
                        if (it == "Allowed") null else "Invalid name"
                    },
                    onConfirm = { submitted = it },
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText("Confirm").assertIsEnabled()
        compose.onNodeWithText("Invalid name").assertDoesNotExist()
        field.performTextReplacement("Still invalid")
        assertEquals(0, validations)

        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Invalid name").assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsEnabled()
        assertNull(submitted)

        val validationsAfterSubmission = validations
        field.performTextReplacement("Allowed")
        compose.onNodeWithText("Invalid name").assertDoesNotExist()
        assertEquals(validationsAfterSubmission, validations)
        field.performImeAction()
        assertEquals("Allowed", submitted)
    }

    @Test
    fun `disabled confirmation also blocks the keyboard action`() {
        var confirmations = 0
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    initialText = "Valid",
                    enabled = false,
                    onConfirm = { confirmations++ },
                    onDismissRequest = {},
                )
            }
        }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(0, confirmations)
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

    @Test
    @Config(qualifiers = "w320dp-h320dp-port")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `large text can scroll the input without losing the action buttons`() {
        RuntimeEnvironment.setFontScale(2f)
        var confirmed = false
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename tag",
                    description = "Source tag: parent::Science",
                    label = "Tag name",
                    confirmText = "Rename",
                    initialText = "History",
                    onConfirm = { confirmed = true },
                    onDismissRequest = {},
                    validateOnInput = false,
                )
            }
        }
        val move = compose.onNodeWithText("Rename").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val originalBounds = move.fetchSemanticsNode().boundsInWindow
        compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
        assertEquals(originalBounds, move.fetchSemanticsNode().boundsInWindow)
        move.performTouchInput { click() }
        assertTrue(confirmed)
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
