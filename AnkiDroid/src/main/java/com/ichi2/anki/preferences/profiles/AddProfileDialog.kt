// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.ichi2.anki.R
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileName.ValidationResult
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.dialogs.TextInputDialog
import com.ichi2.compose.ui.preview.ThemePreviews

/**
 * Dialog asking for the display name of a new profile.
 *
 * Validation goes through [ProfileName.validate], so the UI enforces the same
 * rules as the domain layer. [onConfirm] receives the validated name.
 */
@Composable
fun AddProfileDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (ProfileName) -> Unit,
) {
    val emptyError = stringResource(R.string.profile_name_empty_error)
    val tooLongError = stringResource(R.string.profile_name_too_long_error, ProfileName.MAX_LENGTH)

    TextInputDialog(
        title = stringResource(R.string.add_profile),
        label = stringResource(R.string.profile_name),
        confirmText = stringResource(R.string.dialog_add),
        onConfirm = { text ->
            (ProfileName.validate(text) as? ValidationResult.Valid)?.let { onConfirm(it.name) }
        },
        onDismissRequest = onDismissRequest,
        maxLengthCounter = ProfileName.MAX_LENGTH,
        capitalization = KeyboardCapitalization.Words,
        errorMessageFor = { text ->
            when (ProfileName.validate(text)) {
                is ValidationResult.Valid -> null
                is ValidationResult.Empty -> emptyError
                is ValidationResult.TooLong -> tooLongError
            }
        },
    )
}

@ThemePreviews
@Composable
private fun AddProfileDialogPreview() {
    AnkiDroidTheme {
        AddProfileDialog(onDismissRequest = {}, onConfirm = {})
    }
}
