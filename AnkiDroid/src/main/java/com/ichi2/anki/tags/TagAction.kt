// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.CommonString
import com.ichi2.anki.ui.internationalization.trCase

internal enum class TagAction { RENAME, DELETE, CLEAR_UNUSED }

@Composable
internal fun TagAction.dialogTitle(): String =
    when (this) {
        TagAction.RENAME -> stringResource(CommonString.manage_tags_rename_title)
        TagAction.DELETE -> stringResource(CommonString.manage_tags_delete_title)
        TagAction.CLEAR_UNUSED -> trCase().clearUnusedTags()
    }

@StringRes
internal fun TagAction.confirmTextRes(): Int =
    when (this) {
        TagAction.RENAME -> CommonString.rename
        TagAction.DELETE -> CommonString.dialog_positive_delete
        TagAction.CLEAR_UNUSED -> CommonString.manage_tags_clear_unused_action
    }
