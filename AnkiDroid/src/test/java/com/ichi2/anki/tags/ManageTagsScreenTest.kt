// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import android.view.WindowManager
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.appcompat.R as AppCompatR

@RunWith(AndroidJUnit4::class)
class ManageTagsScreenTest : RobolectricTest() {
    @get:Rule
    val compose = createComposeRule()

    private val content =
        ManageTagsState.Content(
            visibleNodes = listOf(tagListItem("parent", "parent", 0, hasChildren = true, collapsed = true)),
            totalTagCount = 2,
        )

    @Test
    fun `selecting search text does not repeat the filter`() {
        val query = mutableStateOf("parent")
        val searches = mutableListOf<String>()
        compose.setContent {
            Screen(searchQuery = query.value, onSearch = {
                searches.add(it)
                query.value = it
            })
        }
        val search = compose.onNode(hasSetTextAction())
        search.performTextInputSelection(TextRange(0, 3))
        search.performTextInputSelection(TextRange(6))
        assertEquals(emptyList(), searches)
        search.performTextInput(" child")
        assertEquals(listOf("parent::child"), searches)
    }

    @Test
    fun `search clear button has space before the field edge`() {
        compose.setContent { Screen(searchQuery = "parent") }
        val search = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val clear =
            compose
                .onNodeWithContentDescription(
                    targetContext.getString(AppCompatR.string.abc_searchview_description_clear),
                ).fetchSemanticsNode()
                .boundsInRoot
        assertTrue(search.right - clear.right >= with(compose.density) { 8.dp.toPx() })
    }

    @Test
    fun `focusing rows and opening delete directly do not check rename eligibility`() {
        var checks = 0
        compose.setContent {
            Screen(canRenameTag = {
                checks++
                true
            })
        }
        val row = compose.onNodeWithTag("tag:parent")
        row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        assertEquals(0, checks)
        row.performKeyInput { pressKey(Key.Delete) }
        compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_title)).assertIsDisplayed()
        assertEquals(0, checks)
    }

    @Test
    fun `direct rename uses its preparation without a second eligibility query`() {
        var checks = 0
        compose.setContent {
            Screen(canRenameTag = {
                checks++
                true
            })
        }
        val row = compose.onNodeWithTag("tag:parent")
        row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        row.performKeyInput { pressKey(Key.R) }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertEquals(0, checks)
    }

    @Test
    fun `selecting a tag action animates the sheet closed`() {
        compose.setContent { Screen() }
        compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "parent")).performClick()
        val sheetTitle = compose.onNodeWithTag("selected_tag_name").assertIsDisplayed()
        val initialTop = sheetTitle.fetchSemanticsNode().positionInWindow.y
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).performClick()
            val positions =
                List(6) {
                    compose.mainClock.advanceTimeByFrame()
                    sheetTitle.fetchSemanticsNode().positionInWindow.y
                }
            assertTrue(positions.any { it > initialTop }, "Sheet stayed at $initialTop during dismissal: $positions")
            compose.mainClock.advanceTimeBy(1_000)
            sheetTitle.assertDoesNotExist()
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_title)).assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `the delete dialog dims the activity as the sheet starts closing`() {
        compose.setContent { Screen() }
        compose.onNodeWithContentDescription(targetContext.getString(CommonString.manage_tags_more_actions, "parent")).performClick()
        compose.onNodeWithTag("selected_tag_name").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText(targetContext.getString(CommonString.dialog_positive_delete)).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(targetContext.getString(CommonString.manage_tags_delete_title)).assertExists()
            compose.onNodeWithTag("selected_tag_name").assertExists()
            val window = requireNotNull(ShadowDialog.getLatestDialog().window)
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
            assertTrue(window.attributes.dimAmount > 0f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `search converts typed and pasted spaces and retains the insertion point`() {
        val query = mutableStateOf("")
        compose.setContent {
            Screen(searchQuery = query.value, onSearch = { query.value = it })
        }
        val search = compose.onNode(hasSetTextAction())
        for (prefix in listOf("parent", "parent:", "parent::")) {
            search.performTextReplacement(prefix)
            search.performTextInput(" ")
            search.assertTextEquals("parent::")
            search.performTextInput("child")
            search.assertTextEquals("parent::child")
            assertEquals("parent::child", query.value)
        }
        search.performTextReplacement("parent child leaf")
        search.assertTextEquals("parent::child::leaf")
        search.performTextInputSelection(TextRange(8, 13))
        search.performTextInput("new ")
        search.performTextInput("child")
        search.assertTextEquals("parent::new::child::leaf")
        assertEquals("parent::new::child::leaf", query.value)

        compose.runOnIdle { query.value = "" }
        assertEquals("", search.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        search.performTextInput("another child")
        search.assertTextEquals("another::child")
        assertEquals("another::child", query.value)
    }

    @Test
    fun `undo shortcut in the search field does not undo collection changes`() {
        val query = mutableStateOf("")
        var undos = 0
        compose.setContent {
            Screen(
                content.copy(undoLabel = "Undo remove tag"),
                onUndo = { undos++ },
                searchQuery = query.value,
                onSearch = { query.value = it },
            )
        }
        val search = compose.onNode(hasSetTextAction())
        search.performTextInput("parent")
        assertEquals("parent", query.value)
        search.performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        assertEquals(0, undos)
    }

    @Test
    fun `undo and redo shortcuts respect availability and busy state`() {
        val state = mutableStateOf(content.copy(undoLabel = "Undo remove tag", redoLabel = "Redo remove tag"))
        var undos = 0
        var redos = 0
        compose.setContent { Screen(state.value, onUndo = { undos++ }, onRedo = { redos++ }) }

        fun shortcuts() {
            compose.onRoot().performKeyInput {
                pressKey(Key.Z)
                keyDown(Key.CtrlLeft)
                pressKey(Key.Z)
                keyDown(Key.ShiftLeft)
                pressKey(Key.Z)
                keyUp(Key.ShiftLeft)
                pressKey(Key.Y)
                keyUp(Key.CtrlLeft)
            }
        }

        shortcuts()
        assertEquals(1, undos)
        assertEquals(2, redos)

        compose.runOnIdle { state.value = state.value.copy(isWorking = true) }
        compose.onNodeWithTag("tag_undo").assertIsNotEnabled()
        compose.onNodeWithTag("tag_redo").assertIsNotEnabled()
        shortcuts()
        assertEquals(1, undos)
        assertEquals(2, redos)

        compose.runOnIdle { state.value = content }
        shortcuts()
        assertEquals(1, undos)
        assertEquals(2, redos)
    }

    @Test
    fun `tag action shortcuts require a focused row without modifiers and stay inactive while busy`() {
        val state = mutableStateOf(content)
        compose.setContent { Screen(state.value) }
        compose.onRoot().performKeyInput {
            pressKey(Key.R)
            pressKey(Key.Delete)
        }
        compose.onNode(isDialog()).assertDoesNotExist()

        val row = compose.onNodeWithTag("tag:parent")
        row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        row.performKeyInput {
            for (modifier in listOf(Key.CtrlLeft, Key.AltLeft, Key.ShiftLeft, Key.MetaLeft)) {
                keyDown(modifier)
                pressKey(Key.R)
                pressKey(Key.Delete)
                keyUp(modifier)
            }
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { state.value = content.copy(isWorking = true) }
        row.performKeyInput {
            pressKey(Key.R)
            pressKey(Key.Delete)
        }
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `rename shortcut shows no sheet or dialog until eligibility is known`() {
        val eligibility = CompletableDeferred<Boolean?>()
        compose.setContent { Screen(prepareRename = { eligibility.await() }) }
        val row = compose.onNodeWithTag("tag:parent")
        row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        row.performKeyInput { pressKey(Key.R) }
        compose.onNodeWithTag("selected_tag_name").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()

        compose.runOnIdle { eligibility.complete(true) }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithTag("selected_tag_name").assertDoesNotExist()
    }

    @Test
    fun `rename shortcut opens no sheet or dialog when the check fails`() {
        compose.setContent { Screen(prepareRename = { null }) }
        val row = compose.onNodeWithTag("tag:parent")
        row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        row.performKeyInput { pressKey(Key.R) }
        compose.onNodeWithTag("selected_tag_name").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `progress appears only for slow operations without moving the list`() {
        val state = mutableStateOf(content)
        compose.setContent { Screen(state.value) }
        val row = compose.onNodeWithTag("tag:parent")
        val bounds = row.fetchSemanticsNode().boundsInRoot
        val progress = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        compose.mainClock.autoAdvance = false

        fun setWorking(working: Boolean) {
            compose.runOnIdle { state.value = content.copy(isWorking = working) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(48)
        }

        setWorking(true)
        row.assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(300)
        progress.assertDoesNotExist()
        assertEquals(bounds, row.fetchSemanticsNode().boundsInRoot)
        // A fast operation must not leave a delayed indicator behind.
        setWorking(false)
        compose.mainClock.advanceTimeBy(1_000)
        progress.assertDoesNotExist()
        row.assertIsEnabled()

        setWorking(true)
        compose.mainClock.advanceTimeBy(300)
        progress.assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400)
        progress.assertIsDisplayed()
        assertEquals(bounds, row.fetchSemanticsNode().boundsInRoot)
        setWorking(false)
        progress.assertDoesNotExist()
        assertEquals(bounds, row.fetchSemanticsNode().boundsInRoot)
        row.assertIsEnabled()
    }

    @Test
    fun `swiping snackbars in either direction dismisses each queued message`() {
        val host = SnackbarHostState()
        val results = mutableListOf<SnackbarResult>()
        compose.setContent {
            LaunchedEffect(host) {
                for (message in listOf("First message", "Second message")) {
                    results.add(host.showSnackbar(message, duration = SnackbarDuration.Indefinite))
                }
            }
            Screen(snackbarHostState = host)
        }
        compose.onNodeWithText("First message").assertIsDisplayed()
        compose.onNodeWithTag("tag_snackbar").performTouchInput { swipeLeft() }
        compose.onNodeWithText("First message").assertDoesNotExist()
        compose.onNodeWithText("Second message").assertIsDisplayed()
        assertEquals(listOf(SnackbarResult.Dismissed), results)
        compose.onNodeWithTag("tag_snackbar").performTouchInput { swipeRight() }
        compose.onNodeWithText("Second message").assertDoesNotExist()
        assertEquals(listOf(SnackbarResult.Dismissed, SnackbarResult.Dismissed), results)
        assertNull(host.currentSnackbarData)
    }

    @Composable
    private fun Screen(
        state: ManageTagsState = content,
        snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
        onUndo: () -> Unit = {},
        onRedo: () -> Unit = {},
        searchQuery: String = "",
        onSearch: (String) -> Unit = {},
        prepareRename: suspend (TagName) -> Boolean? = { true },
        canRenameTag: suspend (TagName) -> Boolean? = { true },
    ) {
        AnkiDroidTheme {
            ManageTagsScreen(
                state = state,
                searchQuery = searchQuery,
                onSearch = onSearch,
                onToggleCollapsed = {},
                tagExists = { false },
                canRenameTag = canRenameTag,
                prepareRename = prepareRename,
                onAction = { _, _, _ -> true },
                onUndo = onUndo,
                onRedo = onRedo,
                onNavigateUp = {},
                onRetry = {},
                onDismissError = {},
                snackbarHostState = snackbarHostState,
            )
        }
    }
}
