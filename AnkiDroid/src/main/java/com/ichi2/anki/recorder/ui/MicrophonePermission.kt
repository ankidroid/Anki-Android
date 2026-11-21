// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.ichi2.anki.CommonString
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.dialogs.MessageDialog
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

/** Explains that recording needs the microphone. OK opens the app's settings, where it can be allowed. */
@Composable
internal fun MicrophonePermissionDeniedDialog(onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    MessageDialog(
        title = stringResource(CommonString.permission_denied),
        message = stringResource(CommonString.microphone_permission_denied_message),
        confirmText = stringResource(CommonString.dialog_ok),
        onConfirm = {
            onDismiss()
            activity?.openAppSettingsScreen()
        },
        onDismissRequest = onDismiss,
    )
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
