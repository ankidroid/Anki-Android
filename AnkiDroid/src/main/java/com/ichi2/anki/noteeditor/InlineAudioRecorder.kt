// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.compat.USAGE_TOUCH
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.noteeditor.AudioRecordingViewModel.Failure
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.recorder.ui.MicrophonePermissionDeniedDialog
import com.ichi2.anki.recorder.ui.RecorderStatus
import com.ichi2.anki.recorder.ui.rememberMicrophonePermissionRequest
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * [InlineAudioRecorderCard] driven by [viewModel]. It asks for the microphone on the first record, keeps the screen on
 * while recording and stops recording when the app is left. Back and Close ask first if there is a recording.
 * [modifier] and [contentPadding] apply outside the card; the padding is for the side insets.
 */
@Composable
internal fun InlineAudioRecorder(
    viewModel: AudioRecordingViewModel,
    fieldName: String,
    onInsert: () -> Unit,
    onClose: () -> Unit,
    onFailure: (Failure) -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    var showPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val hasMicrophonePermission = rememberMicrophonePermissionRequest(onDenied = { showPermissionDenied = true })
    val activity = LocalActivity.current
    val view = LocalView.current
    val context = LocalContext.current
    val isRecording = state.status == RecorderStatus.RECORDING

    DisposableEffect(view, isRecording) {
        val screen = view.rootView
        screen.keepScreenOn = isRecording
        onDispose { screen.keepScreenOn = false }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // Compose doesn't recompose while stopped, so the effect above would only clear this after returning.
        view.rootView.keepScreenOn = false
        if (activity?.isChangingConfigurations != true) viewModel.interrupt()
    }
    LaunchedEffect(failure) {
        failure?.let {
            onFailure(it)
            viewModel.failureShown()
        }
    }
    if (showPermissionDenied) {
        MicrophonePermissionDeniedDialog(onDismiss = { showPermissionDenied = false })
    }

    fun close() {
        if (viewModel.recorderState.value == RecorderState.Idle) {
            onClose()
        } else {
            DiscardChangesDialog.showDialog(context, message = TR.addingDiscardCurrentInput()) { onClose() }
        }
    }

    BackHandler { close() }

    fun vibrate() = compat.vibrate(context, 20.milliseconds, USAGE_TOUCH)

    fun stopRecording() {
        viewModel.stopRecording()
        if (viewModel.recorderState.value is RecorderState.Recorded) vibrate()
    }

    InlineAudioRecorderCard(
        state = state,
        waveform = viewModel.waveform,
        fieldName = fieldName,
        onToggleRecording = {
            Timber.i("'record' pressed")
            val before = viewModel.recorderState.value
            if (before == RecorderState.Idle && (activity?.blocksTouches == true || !hasMicrophonePermission())) {
                false
            } else {
                viewModel.toggleRecording().also {
                    if (before == RecorderState.Recording && viewModel.recorderState.value == RecorderState.Paused) vibrate()
                }
            }
        },
        onHoldRelease = {
            Timber.i("'record' released after holding")
            stopRecording()
        },
        onSave = {
            Timber.i("'save recording' clicked")
            stopRecording()
        },
        onDiscard = {
            Timber.i("'discard recording' clicked")
            viewModel.discard()
            vibrate()
        },
        onInsert = {
            Timber.i("'insert recording' clicked")
            onInsert()
        },
        onClose = {
            Timber.i("'close recorder' clicked")
            close()
        },
        modifier = modifier,
        animated = animated,
        contentPadding = contentPadding,
    )
}

private val Activity.blocksTouches: Boolean
    get() = window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
