// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ichi2.compose.ui.components.SwipeableSnackbarHost
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews

/**
 * Displays tag search, the hierarchy and action dialogs, including loading and error states.
 *
 * * Rename tag
 * * Delete tag
 * * Clear unused tags (global)
 * * TODO: Move tag.
 * * TODO: 'Merge into'.
 */
@Composable
internal fun ManageTagsScreen(
    state: ManageTagsState,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onToggleCollapsed: (TagName) -> Unit,
    tagExists: (TagName) -> Boolean,
    canRenameTag: suspend (TagName) -> Boolean?,
    onAction: (TagAction, TagName?, String) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: (Throwable) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogAction by rememberSaveable { mutableStateOf<TagAction?>(null) }
    val content = state as? ManageTagsState.Content
    val actionsEnabled = content != null && !content.isWorking
    val error =
        when (state) {
            is ManageTagsState.Content -> state.error
            is ManageTagsState.Error -> state.error
            ManageTagsState.Loading -> null
        }

    Scaffold(
        // The activity pads system bars and cutouts. Consume those before applying the IME inset.
        modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ManageTagsTopBar(
                content = content,
                onNavigateUp = onNavigateUp,
                onClearUnused = {
                    selectedTag = null
                    dialogAction = TagAction.CLEAR_UNUSED
                },
            )
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarHostState, Modifier.testTag("tag_snackbar")) },
    ) { padding ->
        ManageTagsContent(
            state = state,
            searchQuery = searchQuery,
            onSearch = onSearch,
            onToggleCollapsed = onToggleCollapsed,
            onSelectTag = { selectedTag = it.value },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    ManageTagsDialogs(
        selectedTag = selectedTag,
        dialogAction = dialogAction,
        tagRevision = content?.tagRevision,
        actionsEnabled = actionsEnabled,
        tagExists = tagExists,
        canRenameTag = canRenameTag,
        onDialogActionChange = { dialogAction = it },
        onAction = { action, tag, input ->
            onAction(action, tag, input)
            dialogAction = null
            selectedTag = null
        },
        onDismissRequest = {
            // A closing sheet can finish dismissing after its action has opened a dialog.
            if (dialogAction == null) selectedTag = null
        },
    )
    if (error != null) {
        ManageTagsErrorDialog(error = error, onRetry = onRetry, onDismiss = { onDismissError(error) })
    }
}

/** Supplies populated, empty, loading, and busy states for [ManageTagsScreen] previews. */
class ManageTagsPreviewProvider : PreviewParameterProvider<ManageTagsState> {
    private val nodes =
        listOf(
            TagListItemState("History", "History", 0, hasChildren = false, collapsed = false),
            TagListItemState("Science", "Science", 0, hasChildren = true, collapsed = false),
            TagListItemState("Science::Biology", "Biology", 1, hasChildren = false, collapsed = false),
        )
    override val values =
        sequenceOf(
            ManageTagsState.Content(nodes, totalTagCount = nodes.size),
            ManageTagsState.Content(emptyList()),
            ManageTagsState.Loading,
            ManageTagsState.Content(nodes, isWorking = true, totalTagCount = nodes.size),
        )

    override fun getDisplayName(index: Int): String = listOf("Tags", "Empty", "Loading", "Busy")[index]
}

/** @see ManageTagsScreen */
@ThemePreviews
@Composable
private fun ManageTagsScreenPreview(
    @PreviewParameter(ManageTagsPreviewProvider::class) state: ManageTagsState,
) {
    AnkiDroidPreview {
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
        )
    }
}
