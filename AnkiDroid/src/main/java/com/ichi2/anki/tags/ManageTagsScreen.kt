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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.launch

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
    prepareRename: suspend (TagName) -> Boolean?,
    onAction: (TagAction, TagName?, String) -> Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: (Throwable) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    validateRenameInput: (suspend (TagName, String) -> TagInputValidation?)? = null,
    hasAnkiWebAccount: Boolean = false,
) {
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogAction by rememberSaveable { mutableStateOf<TagAction?>(null) }
    // TODO: Reconsider persisting the dismissal across visits to Manage tags.
    var syncWarningDismissed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val content = state as? ManageTagsState.Content
    val actionsEnabled = content != null && !content.isWorking
    val showSyncWarning = hasAnkiWebAccount && content?.hasAppliedTagDeletion == true && !syncWarningDismissed
    val focusRequester = remember { FocusRequester() }
    var hasFocusedChild by remember { mutableStateOf(false) }
    var focusedTag by remember { mutableStateOf<TagName?>(null) }
    var shortcutFocus by remember { mutableStateOf<TagName?>(null) }
    val restoreShortcutFocus = actionsEnabled && selectedTag == null && dialogAction == null
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(shortcutFocus, restoreShortcutFocus, hasFocusedChild, content?.visibleNodes) {
        val tag = shortcutFocus ?: return@LaunchedEffect
        // Keep a new focus choice, and discard tags removed or hidden by the operation.
        if (restoreShortcutFocus && (hasFocusedChild || content.visibleNodes.none { it.fullTag == tag })) {
            shortcutFocus = null
        }
    }
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
                .onFocusChanged { hasFocusedChild = it.hasFocus && !it.isFocused }
                .manageTagsUndoShortcuts(
                    enabled = { actionsEnabled && selectedTag == null && dialogAction == null },
                    undoAvailable = content?.undoLabel != null,
                    redoAvailable = content?.redoLabel != null,
                    onUndo = {
                        shortcutFocus = focusedTag
                        onUndo()
                    },
                    onRedo = {
                        shortcutFocus = focusedTag
                        onRedo()
                    },
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
            onDismissSyncWarning = { syncWarningDismissed = true }.takeIf { showSyncWarning },
            searchQuery = searchQuery,
            onSearch = onSearch,
            onToggleCollapsed = onToggleCollapsed,
            onSelectTag = { selectedTag = it.value },
            focusTag = shortcutFocus.takeIf { restoreShortcutFocus && !hasFocusedChild },
            onFocusRestored = { shortcutFocus = null },
            onTagFocusChanged = { tag, hasFocus ->
                if (hasFocus) {
                    focusedTag = tag
                } else if (focusedTag == tag) {
                    focusedTag = null
                }
            },
            onTagShortcut = shortcut@{ action, tag ->
                if (!actionsEnabled || selectedTag != null || dialogAction != null || error != null) return@shortcut false
                if (action == TagAction.RENAME) {
                    scope.launch {
                        val canRename = prepareRename(tag)
                        shortcutFocus = tag
                        if (canRename != true) return@launch
                        if (selectedTag != null || dialogAction != null) return@launch
                        // Select only after the check, so no actions sheet opens while waiting.
                        dialogAction = TagAction.RENAME
                        selectedTag = tag.value
                    }
                } else {
                    selectedTag = tag.value
                    dialogAction = action
                }
                true
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    ManageTagsDialogs(
        hasAnkiWebAccount = hasAnkiWebAccount,
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
            if (action == TagAction.RENAME && shortcutFocus == tag) {
                // Renaming changes the lazy-list key, so restore focus through the new row.
                shortcutFocus = TagName.build(input)
            }
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
            prepareRename = { true },
            onAction = { _, _, _ -> true },
            onUndo = {},
            onRedo = {},
            onNavigateUp = {},
            onRetry = {},
            onDismissError = {},
        )
    }
}
