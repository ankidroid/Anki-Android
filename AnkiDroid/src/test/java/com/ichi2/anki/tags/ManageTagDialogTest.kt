// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ManageTagDialogTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `rename label stays visible through focus and editing`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AnkiDroidTheme {
                ManageTagDialog(
                    action = TagAction.RENAME,
                    tag = requireNotNull(TagName.build("source")),
                    enabled = true,
                    tagExists = { false },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        val label = compose.onNodeWithTag("text_input_label", useUnmergedTree = true)
        val input = compose.onNode(hasSetTextAction())
        val positions =
            (0..20).map {
                compose.mainClock.advanceTimeByFrame()
                label.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
        assertEquals(1, positions.distinct().size, "Filled label moved during focus: $positions")
        input.assertIsFocused()
        compose.mainClock.autoAdvance = true
        input.performTextReplacement("destination")
        assertEquals(positions.first(), label.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        input.performTextReplacement("")
        assertEquals(positions.first(), label.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `keyboard confirmation rechecks collisions and busy state before submitting case-only spelling`() {
        val source = requireNotNull(TagName.build("Straße"))
        val existing = mutableSetOf(source)
        val enabled = mutableStateOf(true)
        val confirmed = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                ManageTagDialog(
                    action = TagAction.RENAME,
                    tag = source,
                    enabled = enabled.value,
                    tagExists = existing::contains,
                    onConfirm = { confirmed.add(it) },
                    onDismissRequest = {},
                )
            }
        }
        val input = compose.onNode(hasSetTextAction())
        val confirm = compose.onNodeWithText(targetContext.getString(CommonString.rename))
        input.performTextReplacement("renamed")
        confirm.assertIsEnabled()
        // The tag list can change after rendering; submission must validate again.
        compose.runOnIdle { existing.add(requireNotNull(TagName.build("renamed"))) }
        input.performImeAction()
        assertTrue(confirmed.isEmpty())

        input.performTextReplacement("STRASSE")
        confirm.assertIsEnabled()
        compose.runOnIdle { enabled.value = false }
        confirm.assertIsNotEnabled()
        input.performImeAction()
        assertTrue(confirmed.isEmpty())

        compose.runOnIdle { enabled.value = true }
        input.performImeAction()
        assertEquals(listOf("STRASSE"), confirmed)
    }

    @Test
    fun `rename converts spaces to hierarchy separators when typing and pasting`() {
        val confirmed = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                ManageTagDialog(
                    action = TagAction.RENAME,
                    tag = requireNotNull(TagName.build("source")),
                    enabled = true,
                    tagExists = { false },
                    onConfirm = { confirmed.add(it) },
                    onDismissRequest = {},
                )
            }
        }
        val input = compose.onNode(hasSetTextAction())
        for (prefix in listOf("parent", "parent:", "parent::")) {
            input.performTextReplacement(prefix)
            input.performTextInput(" ")
            input.assertTextContains("parent::")
            input.performTextInput("child")
            input.assertTextContains("parent::child")
        }
        input.performTextReplacement("parent child leaf")
        input.assertTextContains("parent::child::leaf")
        // Replacing a selection must leave subsequent typing after the inserted separator.
        input.performTextInputSelection(TextRange(8, 13))
        input.performTextInput("new ")
        input.performTextInput("child")
        input.assertTextContains("parent::new::child::leaf")
        input.performImeAction()
        assertEquals(listOf("parent::new::child::leaf"), confirmed)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `rename validates edits immediately`() {
        val existing = requireNotNull(TagName.build("existing"))
        compose.setContent {
            AnkiDroidTheme {
                ManageTagDialog(
                    action = TagAction.RENAME,
                    tag = requireNotNull(TagName.build("source")),
                    enabled = true,
                    tagExists = { it == existing },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        val input = compose.onNode(hasSetTextAction())
        val confirm = compose.onNodeWithText(targetContext.getString(CommonString.rename))
        val collision = compose.onNodeWithText(targetContext.getString(CommonString.tag_already_exists))
        val invalid = compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_rename_invalid))
        val originalBounds = input.fetchSemanticsNode().boundsInRoot
        // Opening with the unchanged name disables Rename without showing an error.
        confirm.assertIsNotEnabled()
        invalid.assertDoesNotExist()
        collision.assertDoesNotExist()
        input.performTextReplacement("EXISTING")
        collision.assertIsDisplayed()
        confirm.assertIsNotEnabled()
        assertEquals(originalBounds, input.fetchSemanticsNode().boundsInRoot, "Collision feedback shifted the field")
        input.performTextReplacement("renamed")
        collision.assertDoesNotExist()
        confirm.assertIsEnabled()
        assertEquals(originalBounds, input.fetchSemanticsNode().boundsInRoot, "Clearing feedback shifted the field")
        for (rejected in listOf("", "source")) {
            input.performTextReplacement(rejected)
            if (rejected.isEmpty()) compose.onNodeWithText("Enter a tag name").assertIsDisplayed() else invalid.assertIsDisplayed()
            confirm.assertIsNotEnabled()
            assertEquals(originalBounds, input.fetchSemanticsNode().boundsInRoot, "Invalid input feedback shifted the field")
        }
    }
}
