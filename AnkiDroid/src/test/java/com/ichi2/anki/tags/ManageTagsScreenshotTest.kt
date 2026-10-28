// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import android.os.Looper
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.testutils.simulateSystemBars
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.time.Duration
import kotlin.test.assertEquals
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.utils.dp as viewDp

class ManageTagsScreenshotTest : ScreenshotTest() {
    @get:Rule
    val compose = createComposeRule()
    private val screen = ManageTagsTestHelper(this, compose)

    @Test
    fun tree_and_search() {
        addBasicNote().update {
            listOf("Science::Biology::Cells", "Science::Chemistry", "History", "Languages::French").forEach(::addTag)
        }
        openScreen()
        screen.viewModel.toggleCollapsed("Science")
        compose.waitUntil(timeoutMillis = 10_000) {
            (screen.viewModel.state.value as? ManageTagsState.Content)?.visibleNodes?.size == 5
        }
        captureSettled("tree")
        val clearQuery = compose.onNodeWithContentDescription(targetContext.getString(AppCompatR.string.abc_searchview_description_clear))
        clearQuery.assertDoesNotExist()
        screen.search("Cells")
        captureSettled("search")
        clearQuery.assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        clearQuery.performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNodeWithTag("tag:History").assertIsDisplayed()
        clearQuery.assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun actions() {
        val tag = "Science::Biology"
        addBasicNote().update { addTag(tag) }
        openScreen()
        compose.onNodeWithTag("tag:Science").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            (screen.viewModel.state.value as? ManageTagsState.Content)?.visibleTagNames?.contains(tag) == true
        }
        compose.onNodeWithTag("tag:$tag").assertIsDisplayed()
        screen.showActions(tag)
        compose.onNodeWithText(tag).assertIsDisplayed()
        for (label in listOf(
            CommonString.rename,
            CommonString.dialog_positive_delete,
        )) {
            compose.onNodeWithText(targetContext.getString(label)).assertIsDisplayed()
        }
        captureSettled("actions")
    }

    @Test
    fun long_tag_paths() {
        RuntimeEnvironment.setQualifiers("+w320dp-h640dp-port")
        val parent = "AnKing::Step_1::Biochemistry::Molecular_biology"
        val source = "$parent::Original"
        val siblings = listOf("$parent::DNA_replication", "$parent::RNA_replication")
        addBasicNote().update { (siblings + source).forEach(::addTag) }
        openScreen()
        screen.search("Original")
        screen.showActions(source)
        compose.onNodeWithText(source).assertIsDisplayed()
        captureSettled("long_path_actions")
    }

    @Test
    fun deep_hierarchy() {
        RuntimeEnvironment.setQualifiers("+w320dp-h640dp-port")
        val fullTag = (List(15) { "Level$it" } + "Leaf").joinToString("::")
        addBasicNote().update { addTag(fullTag) }
        openScreen()
        screen.search("Leaf")
        compose.onNodeWithTag("tag_list").performScrollToNode(hasTestTag("tag:$fullTag"))
        compose.onNode(hasText("Leaf") and hasAnyAncestor(hasTestTag("tag:$fullTag"))).assertIsDisplayed()
        compose
            .onNode(
                hasContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, fullTag)) and
                    hasAnyAncestor(hasTestTag("tag:$fullTag")),
            ).assertIsDisplayed()
            .assertTouchWidthIsEqualTo(48.dp)
        captureSettled("deep_hierarchy")
    }

    @Test
    fun rename() = captureAction(TagAction.RENAME)

    @Test
    fun rename_collision() {
        // Match the other input screenshots to avoid Robolectric's wide-dialog layout loop.
        RuntimeEnvironment.setQualifiers("+w320dp-h640dp-port")
        addBasicNote().update {
            listOf("Science", "History").forEach(::addTag)
        }
        openScreen()
        screen.action(TagAction.RENAME, "Science")
        screen.enterText("History")
        compose.onNodeWithText(targetContext.getString(CommonString.tag_already_exists)).assertIsDisplayed()
        screen.confirm(TagAction.RENAME).assertIsNotEnabled()
        captureSettled("rename_collision")
    }

    @Test
    fun clear_unused_menu() {
        addBasicNote().update { addTag("Science") }
        openScreen()
        compose.onNodeWithContentDescription(targetContext.getString(AppCompatR.string.abc_action_menu_overflow_description)).performClick()
        compose.onNodeWithText(with(targetContext) { TR.sentenceCase.clearUnusedTags }).assertIsDisplayed()
        captureSettled("clear_unused_menu")
    }

    @Test
    fun clear_unused_confirmation() {
        addBasicNote().update { addTag("Science") }
        openScreen()
        screen.clearUnused()
        captureSettled("clear_unused_confirmation")
    }

    @Test
    fun delete() = captureAction(TagAction.DELETE)

    private fun captureAction(
        action: TagAction,
        name: String = action.name.lowercase(),
    ) {
        addBasicNote().update { addTag("Science::Biology") }
        openScreen()
        screen.action(action, "Science")
        captureSettled(name)
    }

    @Test
    fun empty_collection() {
        openScreen()
        captureSettled("empty")
    }

    @Test
    fun search_with_keyboard() {
        RuntimeEnvironment.setQualifiers("+w320dp-h640dp-port")
        addBasicNote().update {
            (0..29).forEach { addTag("Tag${it.toString().padStart(2, '0')}") }
        }
        openScreen()
        screen.search("Tag")
        val keyboard = screen.controller.get().simulateKeyboard(keyboardHeight = 260.viewDp)
        compose.onNodeWithTag("tag_list").performScrollToNode(hasTestTag("tag:Tag29"))
        captureSettled("search_with_keyboard")
        val keyboardLocation = IntArray(2)
        keyboard.getLocationInWindow(keyboardLocation)
        val lastRowBottom =
            compose
                .onNodeWithTag("tag:Tag29")
                .fetchSemanticsNode()
                .boundsInWindow.bottom
        assertEquals(keyboardLocation[1].toDouble(), lastRowBottom.toDouble(), absoluteTolerance = 1.0)
    }

    @Test
    fun landscape_large_font_delete() {
        RuntimeEnvironment.setQualifiers("+w640dp-h320dp-land")
        RuntimeEnvironment.setFontScale(2f)
        val tag = "AnKing::Step_1::Biochemistry::Molecular_biology::DNA_structure_and_replication"
        addBasicNote().update { addTag(tag) }
        openScreen()
        screen.search("DNA_structure")
        screen.action(TagAction.DELETE, tag)
        captureSettled("landscape_large_font_delete")
        compose.onNodeWithText(targetContext.getString(CommonString.dialog_cancel)).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        screen.confirm(TagAction.DELETE).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
    }

    @Test
    fun landscape_actions() {
        RuntimeEnvironment.setQualifiers("+land")
        addBasicNote().update { addTag("Science::Biology") }
        openScreen()
        screen.showActions("Science")
        compose.onNodeWithText(targetContext.getString(CommonString.rename)).assertIsDisplayed()
        captureSettled("landscape_actions")
    }

    private fun captureSettled(name: String) {
        // Capture input dialogs without a blinking cursor or an active IME connection.
        ShadowDialog
            .getLatestDialog()
            ?.window
            ?.decorView
            ?.findFocus()
            ?.clearFocus()
        // Advance a bounded interval for ripples and sheet animations.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        captureScreen(name)
    }

    @After
    fun closeScreen() = screen.close()

    private fun openScreen() {
        screen.open()
        compose.waitUntil(timeoutMillis = 10_000) { screen.viewModel.state.value is ManageTagsState.Content }
        screen.controller.get().simulateSystemBars()
    }
}
