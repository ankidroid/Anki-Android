// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagActionsBottomSheetTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h480dp-port")
    fun `long names keep both ends visible and leave actions reachable at large font sizes`() {
        RuntimeEnvironment.setFontScale(2f)
        val cases =
            listOf(
                "prefix_${"a".repeat(2_000)}_suffix" to true,
                (List(100) { "Parent$it" } + "Leaf").joinToString("::") to true,
                "prefix::${"📚".repeat(1_000)}::Leaf" to true,
                "Short" to false,
            )
        val name = mutableStateOf(cases.first().first)
        val selected = mutableListOf<TagAction>()
        compose.setContent {
            AnkiDroidTheme {
                TagActionsBottomSheet(
                    tag = requireNotNull(TagName.build(name.value)),
                    enabled = true,
                    onAction = { selected.add(it) },
                    onDismissRequest = {},
                )
            }
        }

        for ((text, ellipsized) in cases) {
            compose.runOnIdle { name.value = text }
            val rename = compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsDisplayed()
            val delete = compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).assertIsDisplayed()
            val title = compose.onNodeWithText(text).assertIsDisplayed().assertTextEquals(text)
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals(1, layout.lineCount)
            assertEquals(ellipsized, layout.isLineEllipsized(0), "Tag length: ${text.length}")
            for (offset in listOf(0, text.lastIndex)) {
                val bounds = layout.getBoundingBox(offset)
                assertTrue(bounds.width > 0f, "The first and last characters must remain visible")
                assertTrue(bounds.left >= 0f && bounds.right <= layout.size.width + 1f)
            }

            selected.clear()
            rename.performClick()
            delete.performClick()
            assertEquals(listOf(TagAction.RENAME, TagAction.DELETE), selected)
        }
    }
}
