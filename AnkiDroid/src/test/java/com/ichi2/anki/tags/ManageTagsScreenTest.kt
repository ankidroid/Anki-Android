// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
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
    ) {
        AnkiDroidTheme {
            ManageTagsScreen(
                state = state,
                searchQuery = "",
                onSearch = {},
                onToggleCollapsed = {},
                tagExists = { false },
                canRenameTag = { true },
                onAction = { _, _, _ -> },
                onNavigateUp = {},
                onRetry = {},
                onDismissError = {},
                snackbarHostState = snackbarHostState,
            )
        }
    }
}
