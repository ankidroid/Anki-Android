// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.dialogs.TextInputDialog
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider

@Composable
fun CustomSyncCertificateDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    TextInputDialog(
        title = stringResource(CommonString.custom_sync_certificate_title),
        label = stringResource(CommonString.dialog_hint_certificate),
        helperText = stringResource(CommonString.custom_sync_certificate_summary, stringResource(CommonString.app_name)),
        // TODO: Disable Save when the input is empty.
        confirmText = stringResource(CommonString.save),
        initialText = initialText,
        capitalization = KeyboardCapitalization.None,
        singleLine = false,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        // TODO: Validate the certificate before submitting it.
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Preview(showBackground = false)
@Composable
private fun CustomSyncCertificateDialogPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        CustomSyncCertificateDialog(initialText = "", onConfirm = {}, onDismissRequest = {})
    }
}
