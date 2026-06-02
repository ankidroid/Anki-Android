// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import kotlin.math.abs

/**
 * Holds the UI state for [SharedDecksDownloadFragment].
 */
class SharedDecksDownloadViewModel : ViewModel() {
    val uiState: StateFlow<SharedDecksDownloadUiState>
        field = MutableStateFlow(SharedDecksDownloadUiState())

    private val speedCalculator = DownloadSpeedCalculator()

    fun onEvent(event: SharedDecksDownloadEvent) {
        when (event) {
            is SharedDecksDownloadEvent.TitleChanged -> handleTitleChanged(event.title)
            SharedDecksDownloadEvent.DownloadStarted -> handleDownloadStarted()
            is SharedDecksDownloadEvent.ProgressUpdated -> handleProgressUpdated(event)
            is SharedDecksDownloadEvent.NetworkErrorChanged -> handleNetworkErrorChanged(event.showing)
            SharedDecksDownloadEvent.DownloadCompleted -> handleDownloadCompleted()
            SharedDecksDownloadEvent.DownloadFailed -> handleDownloadFailed()
        }
    }

    private fun handleTitleChanged(title: String) {
        Timber.d("Title changed: %s", title)
        uiState.update { it.copy(title = title) }
    }

    private fun handleDownloadStarted() {
        Timber.i("Download started; resetting progress, speed stats, and network-error flag")
        speedCalculator.reset()
        uiState.update {
            it.copy(
                phase = DownloadPhase.Downloading,
                progress = ProgressInfo(),
                downloadStats = DownloadStats(),
                showNetworkError = false,
            )
        }
    }

    private fun handleProgressUpdated(event: SharedDecksDownloadEvent.ProgressUpdated) {
        val percent =
            if (event.totalBytes > 0) {
                abs(event.downloadedBytes * 100f / event.totalBytes)
            } else {
                0f
            }

        val smoothedSpeed = speedCalculator.update(event.downloadedBytes, event.currentTime)
        val bytesPerSecond = smoothedSpeed.toLong()

        val secondsRemaining =
            if (bytesPerSecond > 0 && event.totalBytes > 0) {
                (event.totalBytes - event.downloadedBytes) / bytesPerSecond
            } else {
                null
            }

        uiState.update {
            it.copy(
                progress =
                    ProgressInfo(
                        percent = percent,
                        downloadedBytes = event.downloadedBytes,
                        totalBytes = event.totalBytes,
                    ),
                downloadStats =
                    DownloadStats(
                        speedBytesPerSecond = bytesPerSecond,
                        secondsRemaining = secondsRemaining,
                    ),
            )
        }
    }

    private fun handleNetworkErrorChanged(showing: Boolean) {
        Timber.d("Network error banner %s", if (showing) "shown" else "hidden")
        uiState.update { it.copy(showNetworkError = showing) }
    }

    private fun handleDownloadCompleted() {
        Timber.d("Download complete; transitioning UI to Complete phase")
        uiState.update {
            it.copy(
                phase = DownloadPhase.Complete,
                progress = it.progress.copy(percent = 100f),
            )
        }
    }

    private fun handleDownloadFailed() {
        Timber.d("Download failed; transitioning UI to Failed phase and resetting progress")
        uiState.update {
            it.copy(
                phase = DownloadPhase.Failed,
                progress = ProgressInfo(),
            )
        }
    }
}

/**
 * Snapshot of every view-visible field on the shared-decks download screen.
 */
data class SharedDecksDownloadUiState(
    val title: String = "",
    val progress: ProgressInfo = ProgressInfo(),
    val downloadStats: DownloadStats = DownloadStats(),
    val showNetworkError: Boolean = false,
    val phase: DownloadPhase = DownloadPhase.Downloading,
)

data class ProgressInfo(
    val percent: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

data class DownloadStats(
    val speedBytesPerSecond: Long = 0L,
    /** `null` when the remaining time cannot be estimated. */
    val secondsRemaining: Long? = null,
)

enum class DownloadPhase {
    /** Default. Cancel button visible. */
    Downloading,

    /** Download finished. Import button replaces Cancel. */
    Complete,

    /** Download error. Try again + Open in browser buttons replace Cancel. */
    Failed,
}

/**
 * Anything the screen can be told to do. The fragment dispatches these in
 * response to download progress, broadcasts, and user actions; the ViewModel
 * folds them into the next [SharedDecksDownloadUiState].
 */
sealed interface SharedDecksDownloadEvent {
    data class TitleChanged(
        val title: String,
    ) : SharedDecksDownloadEvent

    /** Reset progress + speed stats and put the screen back into the Downloading phase. */
    data object DownloadStarted : SharedDecksDownloadEvent

    /** Raw bytes-downloaded snapshot from the [android.app.DownloadManager] poll. */
    data class ProgressUpdated(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val currentTime: Long,
    ) : SharedDecksDownloadEvent

    data class NetworkErrorChanged(
        val showing: Boolean,
    ) : SharedDecksDownloadEvent

    data object DownloadCompleted : SharedDecksDownloadEvent

    data object DownloadFailed : SharedDecksDownloadEvent
}
