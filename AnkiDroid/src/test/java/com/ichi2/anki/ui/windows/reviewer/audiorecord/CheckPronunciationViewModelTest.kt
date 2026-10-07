// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.testutils.JvmTest
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CheckPronunciationViewModelTest : JvmTest() {
    @get:Rule
    val recordingsFolder = TemporaryFolder()

    private val mediaRecorderStub = MediaRecorderStub()
    private val viewModelStore = ViewModelStore()
    private lateinit var mockPlayer: AudioPlayer

    private val onPreparedCallback = slot<() -> Unit>()
    private var onCompletionCallback: (() -> Unit)? = null

    private var isPlayingMock = false
    private var isPausedMock = false

    @Before
    fun setup() {
        mediaRecorderStub.install()
        mockPlayer =
            mockk(relaxUnitFun = true) {
                every { play(any(), capture(onPreparedCallback)) } just runs
                every { isPlaying } answers { isPlayingMock }
                every { isPaused } answers { isPausedMock }
                every { duration } returns 3000
                every { currentPosition } returns 1500
                every { onCompletion = any() } answers {
                    onCompletionCallback = firstArg()
                }
            }
    }

    @After
    fun uninstallMediaRecorder() {
        mediaRecorderStub.uninstall()
    }

    @Test
    fun `onRecordingCompleted should make playback visible`() =
        runViewModelTest { viewModel ->
            viewModel.isPlaybackVisibleFlow.test {
                assertFalse(awaitItem())
                viewModel.record()
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `onPlayOrReplay when stopped should start playback and update UI`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()
            isPlayingMock = false
            isPausedMock = false

            viewModel.isPlayingFlow.test {
                assertFalse(awaitItem())
                viewModel.onPlayOrReplay()
                assertTrue(awaitItem())
            }

            verify { mockPlayer.play(recording.absolutePath, any()) }

            viewModel.playbackProgressBarMaxFlow.test {
                assertEquals(1, awaitItem())
                onPreparedCallback.captured.invoke()
                assertEquals(3000, awaitItem())
            }
        }

    @Test
    fun `onPlayOrReplay when playing should replay audio`() =
        runViewModelTest { viewModel ->
            // Precondition: Playback view must be visible
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = true

            viewModel.replayFlow.test {
                viewModel.onPlayOrReplay()
                assertEquals(Unit, awaitItem())
            }

            verify { mockPlayer.replay() }
            // Allow the progress bar job to complete
            isPlayingMock = false
        }

    @Test
    fun `onPlayOrReplay when paused should resume playback and update UI`() =
        runViewModelTest { viewModel ->
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = false
            isPausedMock = true

            viewModel.isPlayingFlow.test {
                assertFalse(awaitItem())
                viewModel.onPlayOrReplay()
                assertTrue(awaitItem())
            }

            verify { mockPlayer.resume() }
        }

    @Test
    fun `pausePlayback should pause audio and update UI`() =
        runViewModelTest { viewModel ->
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = true
            isPausedMock = false

            viewModel.isPlayingFlow.value = true
            viewModel.isPlayingFlow.test {
                assertTrue(awaitItem())
                viewModel.pausePlayback()
                assertFalse(awaitItem())
            }

            verify { mockPlayer.pause() }
        }

    @Test
    fun `when playback completes should reset playing state and fill progress`() =
        runViewModelTest { viewModel ->
            // Start playback to set the state
            viewModel.record()
            isPlayingMock = false
            viewModel.onPlayOrReplay()
            onPreparedCallback.captured.invoke()
            isPlayingMock = true

            // Launch collectors concurrently
            val stateJob =
                launch {
                    viewModel.isPlayingFlow.test {
                        assertTrue(awaitItem())
                        assertFalse(awaitItem())
                    }
                }
            val progressJob =
                launch {
                    viewModel.playbackProgressFlow.test {
                        awaitItem() // initial 0
                        assertEquals(1500, awaitItem()) // from progress job
                        assertEquals(3000, awaitItem()) // from completion
                    }
                }

            // Trigger the completion event
            onCompletionCallback?.invoke()

            // Clean up
            stateJob.cancel()
            progressJob.cancel()
        }

    @Test
    fun `onRecordingStarted should hide playback and start recording`() =
        runViewModelTest { viewModel ->
            viewModel.record()

            viewModel.isPlaybackVisibleFlow.test {
                assertTrue(awaitItem())
                viewModel.onRecordingStarted()
                assertFalse(awaitItem())
            }

            verify(exactly = 2) { mediaRecorderStub.mediaRecorder.start() }
            coVerify { mockPlayer.close() }
        }

    @Test
    fun `a cancelled recording leaves no file`() =
        runViewModelTest { viewModel ->
            viewModel.onRecordingStarted()

            viewModel.onRecordingCancelled()

            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the cancelled recording is deleted")
        }

    @Test
    fun `recording again deletes the previous recording`() =
        runViewModelTest { viewModel ->
            val previous = viewModel.record()

            val current = viewModel.record()

            assertFalse(previous.exists(), "the previous recording is deleted")
            assertTrue(current.exists(), "the new recording is kept")
        }

    @Test
    fun `starting again while recording replaces the recording`() =
        runViewModelTest { viewModel ->
            viewModel.onRecordingStarted()
            val previous = mediaRecorderStub.outputFiles.single()

            val current = viewModel.record()

            assertFalse(previous.exists(), "the interrupted recording is deleted")
            assertTrue(current.exists(), "the new recording is kept")
        }

    @Test
    fun `cancelling playback deletes the recording`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModel.onCancelPlayback()

            assertFalse(recording.exists(), "the dismissed recording is deleted")
        }

    @Test
    fun `cancelling playback keeps a recording in progress`() =
        runViewModelTest { viewModel ->
            viewModel.onRecordingStarted()

            viewModel.onCancelPlayback()
            viewModel.onRecordingCompleted()

            assertTrue(viewModel.isPlaybackVisibleFlow.value, "the recording can still be played")
            assertTrue(mediaRecorderStub.outputFiles.single().exists(), "the recording is kept")
        }

    @Test
    fun `resetAll deletes the recording and closes the player`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModel.resetAll()

            assertFalse(recording.exists(), "the recording is deleted")
            assertFalse(viewModel.isPlaybackVisibleFlow.value, "playback is hidden")
            verify { mockPlayer.close() }
        }

    @Test
    fun `clearing the ViewModel deletes the recording`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModelStore.clear()

            assertFalse(recording.exists(), "the recording is deleted when the study screen closes")
        }

    @Test
    fun `a recording that fails to start shows no playback and leaves no file`() =
        runViewModelTest { viewModel ->
            mediaRecorderStub.failStart = true

            viewModel.record()

            assertFalse(viewModel.isPlaybackVisibleFlow.value, "no playback for a failed recording")
            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the empty file is deleted")
        }

    private fun runViewModelTest(testBody: suspend TestScope.(CheckPronunciationViewModel) -> Unit) =
        runTest {
            val viewModel =
                CheckPronunciationViewModel(
                    AudioRecorder(ApplicationProvider.getApplicationContext()),
                    mockPlayer,
                    recordingsFolder.root,
                ).also { viewModelStore.put(VIEW_MODEL_KEY, it) }
            try {
                testBody(viewModel)
            } finally {
                viewModelStore.clear()
            }
        }

    private fun CheckPronunciationViewModel.record(): File {
        onRecordingStarted()
        onRecordingCompleted()
        return mediaRecorderStub.outputFiles.last()
    }

    companion object {
        private const val VIEW_MODEL_KEY = "checkPronunciation"
    }
}
