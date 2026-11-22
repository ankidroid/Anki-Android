// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.common.android.R
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.google.android.material.R as MaterialR

/**
 * A Material 3 message dialog with two actions.
 *
 * Changes to the standard M3 dialog:
 * * Primary body text and the theme's widget outline, matching AnkiDroid's View dialogs.
 * * The View dialog theme's width, including its phone/tablet and orientation variants.
 * * The View dialog title, body and action panel spacing.
 * * fade-only animations allow an IME to open when the dialog opens.
 * * Short windows can scroll to reveal the full title and actions.
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
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val titleBottomPadding = dimensionResource(MaterialR.dimen.m3_alert_dialog_title_bottom_margin)
    // The dialog window has its own LocalContext, which can differ from the themed preview context.
    val border = MaterialTheme.widgetBorder
    val elevation = ankiDroidDialogElevation()
    AnkiDroidDialog(
        onDismissRequest = onDismissRequest,
        // Keep the View dialog's device width for long messages (verified on API 34/36).
        // Robolectric does not match reality here.
        modifier = Modifier.width(alertDialogWidth()),
        properties = properties,
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            shadowElevation = elevation,
            border = border,
        ) {
            AnkiDroidDialogLayout(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier.padding(bottom = titleBottomPadding),
                    )
                },
                body = {
                    // The View dialog's message panel also has a 48dp minimum height.
                    Box(Modifier.heightIn(min = 48.dp)) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                },
                actions = {
                    AnkiDroidDialogButtons(
                        confirmText = confirmText,
                        dismissText = dismissText,
                        onConfirm = onConfirm,
                        onDismissRequest = onDismissRequest,
                        confirmEnabled = enabled,
                    )
                },
            )
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
