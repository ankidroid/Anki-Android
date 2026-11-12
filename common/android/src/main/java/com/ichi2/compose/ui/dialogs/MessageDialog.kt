// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.res.use
import com.ichi2.anki.common.android.R
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import androidx.appcompat.R as AppCompatR

/**
 * A Material 3 message dialog with two actions.
 *
 * Changes to the standard M3 dialog:
 * * Primary body text and the theme's widget outline, matching AnkiDroid's View dialogs.
 * * The View dialog theme's width, including its phone/tablet and orientation variants.
 * * 16dp vertical padding.
 * * fade-only animations allow an IME to open when the dialog opens.
 * * The title and actions are always visible in short windows.
 *
 * @param enabled whether the confirm action is enabled; dismissal remains available
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val titleBottomPadding = 16.dp
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(alertDialogWidthFraction()).padding(horizontal = 24.dp, vertical = 16.dp),
        properties = properties,
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            border = MaterialTheme.widgetBorder,
        ) {
            Column(Modifier.padding(24.dp)) {
                // Read the window from inside the dialog's composition.
                val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                SideEffect {
                    if (window != null && window.attributes.windowAnimations != R.style.Animation_AnkiDroidDialog) {
                        window.setWindowAnimations(R.style.Animation_AnkiDroidDialog)
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                    modifier = Modifier.padding(bottom = titleBottomPadding),
                )
                Box(Modifier.weight(1f, fill = false)) {
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
            }
        }
    }
}

/** Matches the width of dialogs using Material 3 in XML views. */
@Composable
fun alertDialogWidthFraction(): Float {
    // Compose wraps short messages to their content width. Fill this fraction before
    // applying the same horizontal background insets as the View dialog.
    // Disable usePlatformDefaultWidth on the dialog, or Android limits the width before this fraction.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Re-read on rotation or resizing: the theme has orientation and screen size variants.
    return remember(context, configuration) {
        // The width attributes come from the alert dialog overlay rather than the Activity theme.
        val dialogTheme = TypedValue()
        context.theme.resolveAttribute(AppCompatR.attr.alertDialogTheme, dialogTheme, true)
        context.obtainStyledAttributes(dialogTheme.resourceId, intArrayOf(configuration.alertDialogMinWidthAttribute)).use {
            // A missing width attribute falls back to the full available width.
            it.getFraction(0, 1, 1, 1f)
        }
    }
}

@get:AttrRes
private val Configuration.alertDialogMinWidthAttribute: Int
    get() =
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            android.R.attr.windowMinWidthMajor
        } else {
            android.R.attr.windowMinWidthMinor
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
