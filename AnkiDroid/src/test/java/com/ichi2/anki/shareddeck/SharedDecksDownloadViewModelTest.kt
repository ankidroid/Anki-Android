// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.shareddeck

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import java.util.Locale

/** Tests for [SharedDecksDownloadViewModel] */
class SharedDecksDownloadViewModelTest {
    private val viewModel = SharedDecksDownloadViewModel()

    private val state get() = viewModel.uiState.value

    @Test
    fun `starts in the downloading phase with no file name`() {
        assertThat(state.fileName, nullValue())
        assertThat(state.percent, equalTo(0f))
        assertThat(state.phase, equalTo(DownloadPhase.Downloading))
        assertThat(state.isWaitingForNetwork, equalTo(false))
    }

    @Test
    fun `onDownloadStarted keeps the file name`() {
        viewModel.onDownloadStarted("Japanese Core 2000.apkg")
        assertThat(state.fileName, equalTo("Japanese Core 2000.apkg"))
    }

    @Test
    fun `onProgress reports the share of the total downloaded`() {
        viewModel.onProgress(downloadedBytes = 25, totalBytes = 200)
        assertThat(state.percent, equalTo(12.5f))
    }

    @Test
    fun `progress is zero while the total size is unknown`() {
        // DownloadManager reports -1 until the server tells it how big the file is
        viewModel.onProgress(downloadedBytes = 5_000, totalBytes = -1)
        assertThat(state.percent, equalTo(0f))
    }

    @Test
    fun `onProgressUnavailable drops the percentage`() {
        viewModel.onProgress(downloadedBytes = 50, totalBytes = 100)
        viewModel.onProgressUnavailable()
        assertThat(state.percent, nullValue())
    }

    @Test
    fun `onWaitingForNetwork toggles the warning`() {
        viewModel.onWaitingForNetwork(true)
        assertThat(state.isWaitingForNetwork, equalTo(true))
        viewModel.onWaitingForNetwork(false)
        assertThat(state.isWaitingForNetwork, equalTo(false))
    }

    @Test
    fun `onDownloadComplete finishes at 100 percent`() {
        viewModel.onProgress(downloadedBytes = 90, totalBytes = 100)
        viewModel.onDownloadComplete()
        assertThat(state.phase, equalTo(DownloadPhase.Complete))
        assertThat(state.percent, equalTo(100f))
    }

    @Test
    fun `onDownloadFailed resets the progress`() {
        viewModel.onProgress(downloadedBytes = 90, totalBytes = 100)
        viewModel.onDownloadFailed()
        assertThat(state.phase, equalTo(DownloadPhase.Failed))
        assertThat(state.percent, equalTo(0f))
    }

    @Test
    fun `retrying after a failure puts the screen back to downloading`() {
        viewModel.onDownloadStarted("deck.apkg")
        viewModel.onWaitingForNetwork(true)
        viewModel.onDownloadFailed()

        viewModel.onDownloadStarted("deck.apkg")

        assertThat(state.phase, equalTo(DownloadPhase.Downloading))
        assertThat(state.percent, equalTo(0f))
        assertThat(state.isWaitingForNetwork, equalTo(false))
    }

    @Test
    fun `whole percentages lose the decimal place`() {
        assertThat(formatDownloadPercent(0f), equalTo("0"))
        assertThat(formatDownloadPercent(100f), equalTo("100"))
    }

    /** The separator follows the user's locale, so the assertion pins one. */
    @Test
    fun `partial percentages keep one decimal place`() =
        withLocale(Locale.US) {
            assertThat(formatDownloadPercent(42.75f), equalTo("42.8"))
            assertThat(formatDownloadPercent(7f), equalTo("7.0"))
        }

    private fun withLocale(
        locale: Locale,
        block: () -> Unit,
    ) {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
