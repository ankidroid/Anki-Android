// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.common.android.R
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.google.android.material.R as MaterialR

/**
 * A Material 3 message dialog with two actions.
 *
 * Changes to the standard M3 dialog:
 * * Primary body text, matching AnkiDroid's View dialogs.
 * * The View dialog theme's width, including its phone/tablet and orientation variants.
 * * The View dialog title, body and action panel spacing.
 * * The theme's widget outline distinguishes e-ink surfaces.
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
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val titleBottomPadding = dimensionResource(MaterialR.dimen.m3_alert_dialog_title_bottom_margin)
    val border = MaterialTheme.widgetBorder
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
            shadowElevation = ankiDroidDialogElevation(),
            border = border,
        ) {
            Layout(
                modifier = Modifier.padding(ankiDroidDialogContentPadding()),
                content = {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier.padding(bottom = titleBottomPadding),
                    )
                    Box {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                    AnkiDroidDialogButtons(
                        confirmText = confirmText,
                        dismissText = dismissText,
                        onConfirm = onConfirm,
                        onDismissRequest = onDismissRequest,
                        confirmEnabled = enabled,
                    )
                },
            ) { children, constraints ->
                // Reserve actions first so a long title or message cannot push them off-screen.
                val actions = children[2].measure(constraints.copy(minHeight = 0))
                val title =
                    children[0].measure(
                        constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - actions.height).coerceAtLeast(0)),
                    )
                val body =
                    children[1].measure(
                        constraints.copy(
                            minHeight = 0,
                            maxHeight = (constraints.maxHeight - actions.height - title.height).coerceAtLeast(0),
                        ),
                    )
                layout(constraints.maxWidth, title.height + body.height + actions.height) {
                    title.placeRelative(0, 0)
                    body.placeRelative(0, title.height)
                    actions.placeRelative(0, title.height + body.height)
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
