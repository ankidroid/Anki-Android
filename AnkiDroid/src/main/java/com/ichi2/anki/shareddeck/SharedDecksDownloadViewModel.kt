// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the UI state for [SharedDecksDownloadFragment].
 */
class SharedDecksDownloadViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SharedDecksDownloadUiState())
    val uiState: StateFlow<SharedDecksDownloadUiState> = _uiState.asStateFlow()

    fun onEvent(event: SharedDecksDownloadEvent) {
        _uiState.update { current ->
            when (event) {
                is SharedDecksDownloadEvent.TitleChanged ->
                    current.copy(title = event.title)
                is SharedDecksDownloadEvent.ProgressUpdated ->
                    current.copy(
                        progress = event.percent,
                        percentageText = event.percentageText,
                    )
                is SharedDecksDownloadEvent.PercentageTextChanged ->
                    current.copy(percentageText = event.text)
                is SharedDecksDownloadEvent.NetworkErrorChanged ->
                    current.copy(showNetworkError = event.showing)
                is SharedDecksDownloadEvent.DownloadCompleted ->
                    current.copy(
                        progress = 100,
                        percentageText = event.percentageText,
                        showCancelButton = false,
                        showImportButton = true,
                    )
                is SharedDecksDownloadEvent.DownloadFailed ->
                    current.copy(
                        progress = 0,
                        percentageText = event.failedText,
                        showCancelButton = false,
                        showTryAgainButton = true,
                        showOpenInBrowserButton = true,
                    )
                SharedDecksDownloadEvent.RetryRequested ->
                    current.copy(
                        showCancelButton = true,
                        showTryAgainButton = false,
                        showOpenInBrowserButton = false,
                    )
            }
        }
    }
}

/**
 * Snapshot of every view-visible field on the shared-decks download screen.
 */
data class SharedDecksDownloadUiState(
    val title: String = "",
    val progress: Int = 0,
    val percentageText: String = "",
    val showNetworkError: Boolean = false,
    val showCancelButton: Boolean = true,
    val showImportButton: Boolean = false,
    val showTryAgainButton: Boolean = false,
    val showOpenInBrowserButton: Boolean = false,
)

/**
 * Anything the screen can be told to do. The fragment dispatches these in
 * response to download progress, broadcasts, and user actions; the ViewModel
 * folds them into the next [SharedDecksDownloadUiState].
 */
sealed interface SharedDecksDownloadEvent {
    data class TitleChanged(
        val title: String,
    ) : SharedDecksDownloadEvent

    data class ProgressUpdated(
        val percent: Int,
        val percentageText: String,
    ) : SharedDecksDownloadEvent

    /** Used when the progress percentage is unavailable, e.g. a transient query error. */
    data class PercentageTextChanged(
        val text: String,
    ) : SharedDecksDownloadEvent

    data class NetworkErrorChanged(
        val showing: Boolean,
    ) : SharedDecksDownloadEvent

    data class DownloadCompleted(
        val percentageText: String,
    ) : SharedDecksDownloadEvent

    data class DownloadFailed(
        val failedText: String,
    ) : SharedDecksDownloadEvent

    /** Restore the Downloading button layout before re-running the download. */
    data object RetryRequested : SharedDecksDownloadEvent
}
