// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    ) {
        AnkiDroidTheme {
            ManageTagsScreen(
                state = state,
                searchQuery = searchQuery,
                onSearch = onSearch,
                onToggleCollapsed = {},
                tagExists = { false },
                canRenameTag = { true },
                prepareRename = prepareRename,
                onAction = { _, _, _ -> },
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
