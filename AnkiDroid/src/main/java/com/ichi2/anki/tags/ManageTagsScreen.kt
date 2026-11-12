// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
 *
 * @param onAction returns whether the operation was accepted and the confirmation can close.
 */
@Composable
internal fun ManageTagsScreen(
    state: ManageTagsState,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onToggleCollapsed: (TagName) -> Unit,
    tagExists: (TagName) -> Boolean,
    canRenameTag: suspend (TagName) -> Boolean?,
    onAction: (TagAction, TagName?, String) -> Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: (Throwable) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    validateRenameInput: (suspend (TagName, String) -> TagInputValidation?)? = null,
) {
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogAction by rememberSaveable { mutableStateOf<TagAction?>(null) }
    val content = state as? ManageTagsState.Content
    val actionsEnabled = content != null && !content.isWorking
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val error =
        when (state) {
            is ManageTagsState.Content -> state.error
            is ManageTagsState.Error -> state.error
            ManageTagsState.Loading -> null
        }

    Scaffold(
        modifier =
            Modifier
                // The activity pads system bars and cutouts; consume them before applying the IME inset.
                .consumeWindowInsets(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .imePadding()
                .focusRequester(focusRequester)
                .manageTagsUndoShortcuts(
                    enabled = { actionsEnabled && selectedTag == null && dialogAction == null },
                    undoAvailable = content?.undoLabel != null,
                    redoAvailable = content?.redoLabel != null,
                    onUndo = onUndo,
                    onRedo = onRedo,
                ).focusable(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ManageTagsTopBar(
                content = content,
                onNavigateUp = onNavigateUp,
                onUndo = onUndo,
                onRedo = onRedo,
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
        validateRenameInput = validateRenameInput,
        canRenameTag = canRenameTag,
        onDialogActionChange = { dialogAction = it },
        onAction = confirm@{ action, tag, input ->
            if (!onAction(action, tag, input)) return@confirm
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

/** Handles collection undo/redo shortcuts after focused text fields have handled their own keys. */
private fun Modifier.manageTagsUndoShortcuts(
    enabled: () -> Boolean,
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
): Modifier =
    onKeyEvent { event ->
        // Text fields handle their own undo first; dialogs keep their own keyboard focus.
        if (!enabled() ||
            event.type != KeyEventType.KeyDown || !event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
        ) {
            return@onKeyEvent false
        }
        when {
            event.key == Key.Z && !event.isShiftPressed && undoAvailable -> {
                onUndo()
                true
            }
            ((event.key == Key.Z && event.isShiftPressed) || (event.key == Key.Y && !event.isShiftPressed)) &&
                redoAvailable -> {
                onRedo()
                true
            }
            else -> false
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
            onAction = { _, _, _ -> true },
            onUndo = {},
            onRedo = {},
            onNavigateUp = {},
            onRetry = {},
            onDismissError = {},
        )
    }
}
