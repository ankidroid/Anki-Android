// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ichi2.anki.common.android.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview

/**
 * A Material 3 [AlertDialog] with a text message and two actions.
 *
 * Changes to the standard M3 dialog:
 * * 16dp vertical padding.
 * * fade-only animations allow an IME to open when the dialog opens.
 * * The title and actions are always visible in short windows.
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
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        properties = properties,
        title = {
            // Read the window from inside AlertDialog's composition.
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                if (window != null && window.attributes.windowAnimations != R.style.Animation_AnkiDroidDialog) {
                    window.setWindowAnimations(R.style.Animation_AnkiDroidDialog)
                }
            }
            Text(title)
        },
        text = {
            Text(message, modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(dismissText) }
        },
    )
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
