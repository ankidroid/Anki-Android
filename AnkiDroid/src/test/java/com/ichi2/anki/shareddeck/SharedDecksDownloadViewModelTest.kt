// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

import com.ichi2.testutils.assertFalse
import org.junit.Before
import org.junit.Test
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that every [SharedDecksDownloadEvent] produces the expected [SharedDecksDownloadUiState] transition.
 */
class SharedDecksDownloadViewModelTest {
    private lateinit var viewModel: SharedDecksDownloadViewModel

    @Before
    fun setUp() {
        viewModel = SharedDecksDownloadViewModel()
    }

    @Test
    fun `initial state is empty Downloading phase`() {
        val state = viewModel.uiState.value
        assertEquals("", state.title)
        assertEquals(DownloadPhase.Downloading, state.phase)
        assertEquals(0f, state.progress.percent, 0.0001f)
        assertEquals(0L, state.progress.downloadedBytes)
        assertEquals(0L, state.progress.totalBytes)
        assertEquals(0L, state.downloadStats.speedBytesPerSecond)
        assertNull(state.downloadStats.secondsRemaining)
        assertFalse(state.showNetworkError)
    }

    @Test
    fun `TitleChanged updates only the title`() {
        viewModel.onEvent(SharedDecksDownloadEvent.TitleChanged("My Deck.apkg"))

        val state = viewModel.uiState.value
        assertEquals("My Deck.apkg", state.title)
        assertEquals(DownloadPhase.Downloading, state.phase)
        assertEquals(0f, state.progress.percent, 0.0001f)
    }

    @Test
    fun `DownloadStarted resets progress and network-error flag but keeps title`() {
        viewModel.onEvent(SharedDecksDownloadEvent.TitleChanged("Spanish 5000"))
        viewModel.onEvent(SharedDecksDownloadEvent.NetworkErrorChanged(showing = true))
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadCompleted)

        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        val state = viewModel.uiState.value
        assertEquals("Spanish 5000", state.title, "title is preserved across DownloadStarted")
        assertEquals(DownloadPhase.Downloading, state.phase)
        assertEquals(0f, state.progress.percent, 0.0001f)
        assertEquals(0L, state.progress.downloadedBytes)
        assertEquals(0L, state.progress.totalBytes)
        assertEquals(0L, state.downloadStats.speedBytesPerSecond)
        assertNull(state.downloadStats.secondsRemaining)
        assertFalse("network-error flag is cleared", state.showNetworkError)
    }

    @Test
    fun `ProgressUpdated populates progress and bytes fields`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 25_000_000,
                totalBytes = 100_000_000,
                currentTime = 0L,
            ),
        )

        val state = viewModel.uiState.value
        assertEquals(25f, state.progress.percent, 0.0001f)
        assertEquals(25_000_000L, state.progress.downloadedBytes)
        assertEquals(100_000_000L, state.progress.totalBytes)
    }

    @Test
    fun `ProgressUpdated yields 0 percent when total size is unknown`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 5_000_000,
                totalBytes = 0,
                currentTime = 0L,
            ),
        )

        val state = viewModel.uiState.value
        assertEquals(0f, state.progress.percent, 0.0001f)
        assertEquals(5_000_000L, state.progress.downloadedBytes)
        assertEquals(0L, state.progress.totalBytes)
    }

    @Test
    fun `ProgressUpdated computes positive percent even if DownloadManager reports a negative count`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = -10_000_000,
                totalBytes = 100_000_000,
                currentTime = 0L,
            ),
        )

        val state = viewModel.uiState.value
        assertEquals(10f, state.progress.percent, 0.0001f)
    }

    @Test
    fun `ProgressUpdated leaves secondsRemaining null when no speed is known`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 0,
                totalBytes = 100_000_000,
                currentTime = 1_000L,
            ),
        )

        val state = viewModel.uiState.value
        assertEquals(0L, state.downloadStats.speedBytesPerSecond)
        assertNull(state.downloadStats.secondsRemaining)
    }

    @Test
    fun `ProgressUpdated computes positive speed and ETA after the speed calculator warms up`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 0,
                totalBytes = 100_000_000,
                currentTime = 1_000L,
            ),
        )
        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 1_000_000,
                totalBytes = 100_000_000,
                currentTime = 2_000L,
            ),
        )
        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 2_000_000,
                totalBytes = 100_000_000,
                currentTime = 3_000L,
            ),
        )

        val state = viewModel.uiState.value
        assertTrue(
            "speedBytesPerSecond should be positive after 3 samples, got ${state.downloadStats.speedBytesPerSecond}",
            state.downloadStats.speedBytesPerSecond > 0,
        )
        assertNotNull(
            "secondsRemaining should be calculable once speed > 0",
            state.downloadStats.secondsRemaining,
        )
        assertTrue(
            "secondsRemaining should be positive, got ${state.downloadStats.secondsRemaining}",
            state.downloadStats.secondsRemaining!! > 0,
        )
    }

    @Test
    fun `NetworkErrorChanged toggles the flag without touching progress`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)
        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 5_000_000,
                totalBytes = 10_000_000,
                currentTime = 0L,
            ),
        )

        viewModel.onEvent(SharedDecksDownloadEvent.NetworkErrorChanged(showing = true))
        assertTrue(viewModel.uiState.value.showNetworkError)
        assertEquals(5_000_000L, viewModel.uiState.value.progress.downloadedBytes)

        viewModel.onEvent(SharedDecksDownloadEvent.NetworkErrorChanged(showing = false))
        assertFalse(viewModel.uiState.value.showNetworkError)
        assertEquals(5_000_000L, viewModel.uiState.value.progress.downloadedBytes)
    }

    @Test
    fun `DownloadCompleted moves to Complete phase and forces percent to 100`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)
        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 42_000_000,
                totalBytes = 100_000_000,
                currentTime = 0L,
            ),
        )

        viewModel.onEvent(SharedDecksDownloadEvent.DownloadCompleted)

        val state = viewModel.uiState.value
        assertEquals(DownloadPhase.Complete, state.phase)
        assertEquals(100f, state.progress.percent, 0.0001f)
        assertEquals(42_000_000L, state.progress.downloadedBytes)
        assertEquals(100_000_000L, state.progress.totalBytes)
    }

    @Test
    fun `DownloadFailed moves to Failed phase and zeroes the progress`() {
        viewModel.onEvent(SharedDecksDownloadEvent.TitleChanged("Half-downloaded.apkg"))
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)
        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 60_000_000,
                totalBytes = 100_000_000,
                currentTime = 0L,
            ),
        )

        viewModel.onEvent(SharedDecksDownloadEvent.DownloadFailed)

        val state = viewModel.uiState.value
        assertEquals(DownloadPhase.Failed, state.phase)
        assertEquals(0f, state.progress.percent, 0.0001f)
        assertEquals(0L, state.progress.downloadedBytes)
        assertEquals(0L, state.progress.totalBytes)
        assertEquals("Half-downloaded.apkg", state.title)
    }

    @Test
    fun `retrying a failed download flips Failed back to Downloading`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadFailed)
        assertEquals(DownloadPhase.Failed, viewModel.uiState.value.phase)

        // The fragment dispatches DownloadStarted when the user taps Try Again.
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        assertEquals(DownloadPhase.Downloading, viewModel.uiState.value.phase)
    }

    @Test
    fun `repeated identical events do not change the state instance value`() {
        viewModel.onEvent(SharedDecksDownloadEvent.TitleChanged("Same.apkg"))
        val first = viewModel.uiState.value

        viewModel.onEvent(SharedDecksDownloadEvent.TitleChanged("Same.apkg"))
        val second = viewModel.uiState.value

        assertEquals(first, second)
    }

    @Test
    fun `DownloadStarted after partial progress wipes the byte counters`() {
        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)
        viewModel.onEvent(
            SharedDecksDownloadEvent.ProgressUpdated(
                downloadedBytes = 9_000_000,
                totalBytes = 10_000_000,
                currentTime = 0L,
            ),
        )
        val partial = viewModel.uiState.value
        assertNotEquals(0L, partial.progress.downloadedBytes)

        viewModel.onEvent(SharedDecksDownloadEvent.DownloadStarted)

        val restarted = viewModel.uiState.value
        assertEquals(0L, restarted.progress.downloadedBytes)
        assertEquals(0L, restarted.progress.totalBytes)
        assertEquals(0f, restarted.progress.percent, 0.0001f)
    }
}
