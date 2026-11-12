// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.use
import com.ichi2.anki.common.android.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import androidx.appcompat.R as AppCompatR

/**
 * A Material 3 message dialog with two actions.
 *
 * Changes to the standard M3 dialog:
 * * The View dialog theme's width, including its phone/tablet and orientation variants.
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
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val titleBottomPadding = 16.dp
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
        ) {
            Column(Modifier.padding(ankiDroidDialogContentPadding())) {
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
                        color = AlertDialogDefaults.textContentColor,
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

/** Resolve the View dialog's themed minimum width before window insets are consumed. */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun alertDialogWidth(): Dp {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    // Re-read on rotation or resizing: the theme has orientation and screen size variants.
    return remember(context, configuration, density, resources) {
        val metrics = resources.displayMetrics
        // DecorView resolves fractions against the configured screen width. Compose's
        // available width can already exclude a landscape cutout or system bar.
        val availableWidth = configuration.screenWidthDp * metrics.density
        // The width attributes come from the alert dialog overlay rather than the Activity theme.
        val dialogTheme = TypedValue()
        context.theme.resolveAttribute(AppCompatR.attr.alertDialogTheme, dialogTheme, true)
        context.obtainStyledAttributes(dialogTheme.resourceId, intArrayOf(configuration.alertDialogMinWidthAttribute)).use {
            val value = it.peekValue(0)
            val pixels =
                when (value?.type) {
                    TypedValue.TYPE_DIMENSION -> value.getDimension(metrics)
                    TypedValue.TYPE_FRACTION -> value.getFraction(availableWidth, availableWidth)
                    else -> availableWidth
                }
            // DecorView truncates to whole pixels before measuring the dialog.
            with(density) { pixels.toInt().toDp() }
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
