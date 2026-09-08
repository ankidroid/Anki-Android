// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.shareddeck

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import kotlin.math.abs

/** State holder for [SharedDecksDownloadFragment]. */
class SharedDecksDownloadViewModel : ViewModel() {
    val uiState: StateFlow<SharedDecksDownloadUiState>
        field = MutableStateFlow(SharedDecksDownloadUiState())

    /** A download was enqueued. Also called on a retry, which clears the failed state. */
    fun onDownloadStarted(fileName: String) {
        Timber.i("download started")
        uiState.value = SharedDecksDownloadUiState(fileName = fileName)
    }

    fun onProgress(
        downloadedBytes: Long,
        totalBytes: Long,
    ) {
        // DownloadManager reports -1 for a download whose size it does not know yet
        val percent = if (totalBytes > 0) abs(downloadedBytes * 100f / totalBytes) else 0f
        uiState.update { it.copy(percent = percent) }
    }

    /** The [android.app.DownloadManager] query failed, so progress cannot be reported. */
    fun onProgressUnavailable() {
        uiState.update { it.copy(percent = null) }
    }

    fun onWaitingForNetwork(waiting: Boolean) {
        uiState.update { it.copy(isWaitingForNetwork = waiting) }
    }

    fun onDownloadComplete() {
        Timber.i("download complete")
        uiState.update { it.copy(phase = DownloadPhase.Complete, percent = 100f) }
    }

    fun onDownloadFailed() {
        Timber.i("download failed")
        uiState.update { it.copy(phase = DownloadPhase.Failed, percent = 0f) }
    }
}

/** Everything the download screen shows. */
data class SharedDecksDownloadUiState(
    val fileName: String? = null,
    /** `null` when progress could not be read, in which case the screen says only that a download is running. */
    val percent: Float? = 0f,
    val phase: DownloadPhase = DownloadPhase.Downloading,
    val isWaitingForNetwork: Boolean = false,
)

/** How far the download has got. Decides the status text and which buttons are offered. */
enum class DownloadPhase {
    /** Running, or paused waiting for the network. Cancel is offered. */
    Downloading,

    /** Finished. Import is offered. */
    Complete,

    /** Failed. Try again and Open in browser are offered. */
    Failed,
}

/** Whole numbers for 0 and 100, one decimal place in between, so the common cases read cleanly. */
fun formatDownloadPercent(percent: Float): String {
    val whole = percent.toInt()
    return if (whole == 0 || whole == 100) whole.toString() else "%.1f".format(percent)
}
