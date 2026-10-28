// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.annotation.StringRes
import com.ichi2.anki.CommonString

internal enum class TagAction { RENAME, DELETE }

@StringRes
internal fun TagAction.dialogTitleRes(): Int =
    when (this) {
        TagAction.RENAME -> CommonString.manage_tags_rename_title
        TagAction.DELETE -> CommonString.manage_tags_delete_title
    }

@StringRes
internal fun TagAction.confirmTextRes(): Int =
    when (this) {
        TagAction.RENAME -> CommonString.rename
        TagAction.DELETE -> CommonString.dialog_positive_delete
    }
