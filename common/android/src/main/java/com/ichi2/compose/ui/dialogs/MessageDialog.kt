// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.common.android.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview

/**
 * Shows a message with confirm and dismiss actions, for example a deletion warning or a retryable error.
 * Only the message scrolls, keeping the title and actions visible in short windows.
 *
 * @param enabled whether the confirm action is enabled; dismissal remains available
 */
@Composable
fun MessageDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String = stringResource(R.string.dialog_cancel),
    enabled: Boolean = true,
    properties: DialogProperties = DialogProperties(),
) {
    AnkiDroidDialog(onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Text(
                    message,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertDialogDefaults.textContentColor,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismissRequest) { Text(dismissText) }
                    TextButton(onClick = onConfirm, enabled = enabled) { Text(confirmText) }
                }
            }
        }
    }
}

/** @see MessageDialog */
@Preview(name = "Confirm deletion", widthDp = 320)
@Composable
private fun DeleteMessageDialogPreview() {
    AnkiDroidPreview {
        MessageDialog(
            title = "Delete item",
            message = "Delete this item and its children? This action cannot be undone.",
            confirmText = "Delete",
            onConfirm = {},
            onDismissRequest = {},
        )
    }
}

/** @see MessageDialog */
@Preview(name = "Retry loading", widthDp = 320)
@Composable
private fun RetryMessageDialogPreview() {
    AnkiDroidPreview {
        MessageDialog(
            title = "Could not load items",
            message = "Check your connection and try again.",
            confirmText = "Retry",
            dismissText = "Close",
            onConfirm = {},
            onDismissRequest = {},
        )
    }
}
