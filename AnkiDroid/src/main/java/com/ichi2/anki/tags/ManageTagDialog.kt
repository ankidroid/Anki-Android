// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ichi2.anki.CommonString
import com.ichi2.compose.ui.dialogs.MessageDialog
import com.ichi2.compose.ui.dialogs.TextInputDialog
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews

/** Validates a rename or asks for confirmation before deleting a tag. */
@Composable
internal fun ManageTagDialog(
    action: TagAction,
    tag: TagName?,
    enabled: Boolean,
    tagExists: (TagName) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val title = stringResource(action.dialogTitleRes())
    if (action == TagAction.DELETE) {
        MessageDialog(
            title = title,
            message = stringResource(CommonString.manage_tags_delete_confirm, requireNotNull(tag).value),
            confirmText = stringResource(action.confirmTextRes()),
            onConfirm = { if (enabled) onConfirm("") },
            onDismissRequest = onDismissRequest,
            enabled = enabled,
        )
        return
    }
    require(action == TagAction.RENAME)
    val source = requireNotNull(tag)
    val alreadyExists = stringResource(CommonString.tag_already_exists)
    val emptyName = stringResource(CommonString.manage_tags_rename_empty)
    val unchanged = stringResource(CommonString.manage_tags_rename_invalid)
    TextInputDialog(
        title = title,
        label = stringResource(CommonString.manage_tags_tag_name_hint),
        confirmText = stringResource(CommonString.rename),
        initialText = source.value,
        capitalization = KeyboardCapitalization.None,
        transformInput = { it.filterTagInput() },
        helperText = "",
        enabled = enabled,
        errorMessageFor = { text ->
            when (validateTagRename(source, text, tagExists)) {
                TagInputValidation.VALID -> null
                TagInputValidation.EMPTY -> emptyName
                TagInputValidation.ALREADY_EXISTS -> alreadyExists
                else -> unchanged
            }
        },
        onConfirm = { if (enabled) onConfirm(it.trim()) },
        onDismissRequest = onDismissRequest,
    )
}

internal class TagActionPreviewProvider : PreviewParameterProvider<TagAction> {
    private val actions = listOf(TagAction.RENAME, TagAction.DELETE)
    override val values = actions.asSequence()

    override fun getDisplayName(index: Int): String = actions[index].name.lowercase()
}

/** @see ManageTagDialog */
@DialogPreviews
@Composable
private fun ManageTagDialogPreview(
    @PreviewParameter(TagActionPreviewProvider::class) action: TagAction,
) {
    AnkiDroidPreview {
        ManageTagDialog(action, TagName.build("Science"), true, { it == TagName.build("History") }, {}, {})
    }
}
