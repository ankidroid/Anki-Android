// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.dialogs.AnkiDroidDialog
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.openAppSettingsScreen
import timber.log.Timber

/** The returned check is true if the microphone can be used. If not, it asks for the permission and returns false. */
@Composable
internal fun rememberMicrophonePermissionRequest(onDenied: () -> Unit): () -> Boolean {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) onDenied()
        }
    return remember(context, launcher) {
        hasPermission@{
            if (Permissions.canRecordAudio(context)) return@hasPermission true
            Timber.i("Requesting the microphone permission")
            launcher.launch(Manifest.permission.RECORD_AUDIO)
            false
        }
    }
}

@Composable
internal fun MicrophonePermissionDeniedDialog(onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    AnkiDroidDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(CommonString.permission_denied),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                )
                Text(
                    stringResource(CommonString.microphone_permission_denied_message),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertDialogDefaults.textContentColor,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) }
                    TextButton(
                        onClick = {
                            onDismiss()
                            activity?.openAppSettingsScreen()
                        },
                    ) { Text(stringResource(CommonString.dialog_ok)) }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MicrophonePermissionDeniedDialogPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        MicrophonePermissionDeniedDialog(onDismiss = {})
    }
}
