// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.anki.recorder.PlayerState
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.recorder.VoicePlayer
import com.ichi2.anki.recorder.VoiceRecorder
import com.ichi2.anki.recorder.ui.RecorderUiState
import com.ichi2.anki.recorder.ui.WaveformHistory
import com.ichi2.anki.recorder.ui.toStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class AudioRecordingViewModel(
    audioRecorder: AudioRecorder = AudioRecorder(appContext),
    createPlayer: () -> Player = { VoicePlayer.createExoPlayer(appContext) },
    recordingsDirectory: File = VoiceRecorder.recordingsDirectory(appContext),
    timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    private val recorder = VoiceRecorder(audioRecorder, recordingsDirectory, viewModelScope, timeSource)
    private val player = VoicePlayer(viewModelScope, createPlayer)

    val recorderState: StateFlow<RecorderState> = recorder.state
    val elapsed: StateFlow<Duration> = recorder.elapsed
    val amplitudes: SharedFlow<Int?> = recorder.amplitudes
    val playerState: StateFlow<PlayerState> = player.state
    val playbackPosition: StateFlow<Duration> = player.position

    val failure: StateFlow<Failure?>
        field = MutableStateFlow<Failure?>(null)

    val uiState: StateFlow<RecorderUiState>
        field = MutableStateFlow(RecorderUiState())

    val waveform = WaveformHistory()

    private val isRecordingOrPaused: Boolean
        get() = recorderState.value == RecorderState.Recording || recorderState.value == RecorderState.Paused

    init {
        addCloseable {
            player.close()
            recorder.close()
        }
        player.state
            .filter { it == PlayerState.Failed }
            .onEach { failure.value = Failure.PLAYBACK }
            .launchIn(viewModelScope)
        recorderState
            .onEach { state ->
                if (state == RecorderState.Idle) {
                    waveform.clear()
                    uiState.value = RecorderUiState()
                } else {
                    uiState.update { it.copy(status = state.toStatus()) }
                }
            }.launchIn(viewModelScope)
        elapsed
            .map { it.inWholeSeconds }
            .filter { it > 0 }
            .onEach { seconds -> uiState.update { it.copy(elapsedSeconds = seconds) } }
            .launchIn(viewModelScope)
        amplitudes.onEach(waveform::add).launchIn(viewModelScope)
    }

    fun startRecording() {
        if (recorderState.value != RecorderState.Idle) return ignore("start")
        player.unload()
        if (!recorder.start()) failure.value = Failure.RECORDING
    }

    fun pauseRecording() {
        if (recorderState.value != RecorderState.Recording) return ignore("pause")
        if (!recorder.pause()) failure.value = Failure.RECORDING
    }

    fun resumeRecording() {
        if (recorderState.value != RecorderState.Paused) return ignore("resume")
        if (!recorder.resume()) failure.value = Failure.RECORDING
    }

    fun stopRecording() {
        if (!isRecordingOrPaused) return ignore("stop")
        if (!recorder.stop()) failure.value = Failure.RECORDING
    }

    /** Starts, pauses or resumes, and returns whether it is now recording. */
    fun toggleRecording(): Boolean {
        when (recorderState.value) {
            RecorderState.Idle -> startRecording()
            RecorderState.Recording -> pauseRecording()
            RecorderState.Paused -> resumeRecording()
            is RecorderState.Recorded -> ignore("toggle")
        }
        return recorderState.value == RecorderState.Recording
    }

    fun discard() {
        player.unload()
        recorder.discard()
    }

    fun play() {
        val file = (recorderState.value as? RecorderState.Recorded)?.file ?: return ignore("play")
        val state = player.state.value
        if (state == PlayerState.Empty || state == PlayerState.Failed) {
            player.load(file)
        }
        player.play()
    }

    fun pausePlayback() {
        player.pause()
    }

    fun skipBack() {
        player.seekBy(-SKIP_INTERVAL)
    }

    fun skipForward() {
        player.seekBy(SKIP_INTERVAL)
    }

    fun interrupt() {
        if (isRecordingOrPaused) stopRecording()
        player.unload()
    }

    fun consumeRecording(block: (File) -> Unit) {
        if (recorderState.value !is RecorderState.Recorded) return ignore("consume")
        player.unload()
        if (!recorder.consumeRecording(block)) failure.value = Failure.RECORDING
    }

    fun failureShown() {
        failure.value = null
    }

    private fun ignore(command: String) {
        Timber.w("Ignoring %s while %s", command, recorderState.value)
    }

    enum class Failure {
        RECORDING,
        PLAYBACK,
    }

    companion object {
        private val SKIP_INTERVAL = 5.seconds
    }
}
