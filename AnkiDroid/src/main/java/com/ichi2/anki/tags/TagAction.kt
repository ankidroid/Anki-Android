// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.CommonString
import com.ichi2.anki.ui.internationalization.trCase

internal enum class TagAction { RENAME, DELETE, CLEAR_UNUSED }

/** Shortcuts for a focused tag or its actions sheet; text fields retain their own key handling. */
internal fun KeyEvent.tagActionShortcut(): TagAction? {
    if (type != KeyEventType.KeyDown || isCtrlPressed || isAltPressed || isMetaPressed || isShiftPressed) return null
    return when (key) {
        Key.R -> TagAction.RENAME
        Key.Delete, Key.Backspace -> TagAction.DELETE
        else -> null
    }
}

@Composable
internal fun TagAction.dialogTitle(): String =
    when (this) {
        TagAction.RENAME -> trCase().renameTag()
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
