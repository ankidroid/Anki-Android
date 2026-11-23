// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.compose.ui.dialogs.MessageDialog
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews

/** Shows the selected tag's actions or confirmation dialog and refreshes rename eligibility. */
@Composable
internal fun ManageTagsDialogs(
    hasAnkiWebAccount: Boolean,
    selectedTag: String?,
    dialogAction: TagAction?,
    tagRevision: Long?,
    actionsEnabled: Boolean,
    tagExists: (TagName) -> Boolean,
    canRenameTag: suspend (TagName) -> Boolean?,
    onDialogActionChange: (TagAction?) -> Unit,
    onAction: (TagAction, TagName?, String) -> Unit,
    onDismissRequest: () -> Unit,
    validateRenameInput: (suspend (TagName, String) -> TagInputValidation?)? = null,
) {
    var renameAvailable by remember(selectedTag, tagRevision) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(selectedTag, tagRevision) {
        selectedTag?.let(TagName::build)?.let {
            renameAvailable = canRenameTag(it)
        }
    }
    val tag = selectedTag?.let(TagName::build)
    val action = dialogAction
    if (tag != null && action == null) {
        TagActionsBottomSheet(
            tag = tag,
            enabled = actionsEnabled,
            canRename = renameAvailable,
            onAction = onDialogActionChange,
            onDismissRequest = onDismissRequest,
        )
    }
    if (action != null) {
        ManageTagDialog(
            action = action,
            hasAnkiWebAccount = hasAnkiWebAccount,
            tag = tag,
            enabled = actionsEnabled,
            tagExists = tagExists,
            validateRenameInput = validateRenameInput,
            tagRevision = tagRevision,
            onConfirm = { input ->
                if (actionsEnabled) {
                    onAction(action, tag, input)
                }
            },
            onDismissRequest = {
                onDialogActionChange(null)
                onDismissRequest()
            },
        )
    }
}

/** Displays a tag error with actions to reload the list or dismiss the dialog. */
@Composable
internal fun ManageTagsErrorDialog(
    error: Throwable,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    MessageDialog(
        title = stringResource(CommonString.vague_error),
        message = error.localizedMessage ?: error.javaClass.simpleName,
        confirmText = stringResource(CommonString.dialog_ok),
        onConfirm = onRetry,
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    )
}

/** @see ManageTagsErrorDialog */
@DialogPreviews
@Composable
private fun ManageTagsErrorDialogPreview() {
    AnkiDroidPreview {
        ManageTagsErrorDialog(IllegalStateException("Unable to load tags"), onRetry = {}, onDismiss = {})
    }
}
