/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.Reviewer
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimediacard.AudioPlayer
import com.ichi2.anki.multimediacard.AudioRecorder
import com.ichi2.anki.multimediacard.AudioView
import com.ichi2.testutils.KeyEventUtils.Companion.getVKey
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class KeyboardShortcutIntegrationTest : RobolectricTest() {
    private lateinit var mReviewer: Reviewer

    @Before
    override fun setUp() {
        super.setUp()
        addNoteUsingBasicModel("Hello", "World")
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        mReviewer = super.startActivityNormallyOpenCollectionWithIntent(Reviewer::class.java, Intent())
        waitForAsyncTasksToComplete()
    }

    /**
     * Press "Shift + V"
     * Release "Shift", then release "V"
     *
     * Expected: Recording is triggered
     * #7780
     */
    @Test
    @Throws(IOException::class)
    fun testActionsOccurOnKeyDown() {
        val recorder = setupRecorderMock()

        pressShiftAndVThenRelease()

        verify(recorder, times(1)).startRecording(any(), anyString())
        verifyNoMoreInteractions(recorder)
    }

    /**
     * Press V
     * Hold V
     *
     * Expected: Recording is triggered and is not untriggered
     */
    @Test
    @Throws(IOException::class)
    fun holdIsNotASecondAction() {
        val recorder = setupRecorderMock()

        pressAndHoldShiftV()

        verify(recorder, times(1)).startRecording(any(), anyString())
        verifyNoMoreInteractions(recorder)
    }

    /**
     * Press "Shift + V"
     * While recording press "V"
     *
     * Expected: Current state is stopped and the desired state is triggered
     * #7780
     */
    @Test
    @Throws(IOException::class)
    fun playingWhileRecordingStopsRecordingAndStartsPlaying() {
        val player = setupPlayerMock()
        val recorder = setupRecorderMock()

        pressShiftAndVThenRelease()

        verify(recorder, times(1)).startRecording(any(), anyString())
        verifyNoMoreInteractions(recorder, player)
        assertStatus(AudioView.Status.RECORDING)

        // now we're set up and recording, pressing V should move us to playing
        pressVThenRelease()

        verify(recorder, times(1)).stopRecording()
        verify(player, times(1)).play(anyString())
        verifyNoMoreInteractions(recorder, player)
        assertStatus(AudioView.Status.PLAYING)
    }

    /**
     * Press " V"
     * While playing press "Shift + V"
     *
     * Expected: Current state is stopped and the desired state is triggered
     * #7780
     */
    @Test
    @Throws(IOException::class)
    fun recordingWhilePlayingStopsPlayingAndStartsRecording() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)

        val player = setupPlayerMock()
        val recorder = setupRecorderMock()

        // We don't need anything recorded as the mock doesn't mind
        pressVThenRelease()

        verify(player, times(1)).play(anyString())
        verifyNoMoreInteractions(recorder, player)
        assertStatus(AudioView.Status.PLAYING)

        // now we're set up and recording, pressing Shift + V should move us to recording
        pressShiftAndVThenRelease()

        verify(player, times(1)).stop()
        verify(recorder, times(1)).startRecording(any(), anyString())
        verifyNoMoreInteractions(recorder, player)
        assertStatus(AudioView.Status.RECORDING)
    }

    private fun assertStatus(recording: AudioView.Status) {
        assertThat(mReviewer.audioView!!.status, equalTo(recording))
    }

    private fun setupPlayerMock(): AudioPlayer {
        assertThat(mReviewer.openMicToolbar(), equalTo(true))
        return mock(AudioPlayer::class.java).also {
            mReviewer.audioView!!.setPlayer(it)
        }
    }

    private fun setupRecorderMock(): AudioRecorder {
        assertThat(mReviewer.openMicToolbar(), equalTo(true))
        return mock(AudioRecorder::class.java).also {
            mReviewer.audioView!!.setRecorder(it)
        }
    }

    private fun pressVThenRelease() {
        depressVKey()
        releaseVKey()
    }

    private fun pressAndHoldShiftV() {
        depressShiftKey()

        val vKey = getVKey()
        whenever(vKey.isShiftPressed).thenReturn(true)
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, vKey)
        whenever(vKey.repeatCount).thenReturn(1)
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, vKey)
    }

    private fun pressShiftAndVThenRelease() {
        depressShiftKey()
        depressVKeyWithShiftHeld()
        releaseShiftKey()
        releaseVKey()
    }

    private fun releaseVKey() {
        val mock = mock(KeyEvent::class.java)
        whenever(mock.keyCode).thenReturn(KeyEvent.KEYCODE_V)
        whenever(mock.unicodeChar).thenReturn('v'.code)
        whenever(mock.getUnicodeChar(anyInt())).thenReturn('v'.code)
        mReviewer.onKeyUp(KeyEvent.KEYCODE_V, mock)
    }

    private fun releaseShiftKey() {
        val mock = mock(KeyEvent::class.java)
        whenever(mock.keyCode).thenReturn(KeyEvent.KEYCODE_SHIFT_LEFT)
        mReviewer.onKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT, mock)
    }

    private fun depressVKey() {
        val mock = mock(KeyEvent::class.java)
        whenever(mock.keyCode).thenReturn(KeyEvent.KEYCODE_V)
        whenever(mock.unicodeChar).thenReturn('v'.code)
        whenever(mock.getUnicodeChar(anyInt())).thenReturn('v'.code)
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, mock)
    }

    private fun depressVKeyWithShiftHeld() {
        val mock = mock(KeyEvent::class.java)
        whenever(mock.isShiftPressed).thenReturn(true)
        whenever(mock.keyCode).thenReturn(KeyEvent.KEYCODE_V)
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, mock)
    }

    private fun depressShiftKey() {
        val mock = mock(KeyEvent::class.java)
        whenever(mock.action).thenReturn(0)
        whenever(mock.deviceId).thenReturn(9)
        whenever(mock.downTime).thenReturn(35660208L)
        whenever(mock.eventTime).thenReturn(35660208L)
        whenever(mock.flags).thenReturn(8)
        whenever(mock.keyCode).thenReturn(KeyEvent.KEYCODE_SHIFT_LEFT)
        whenever(mock.metaState).thenReturn(65)
        whenever(mock.scanCode).thenReturn(42)
        whenever(mock.source).thenReturn(257)
        whenever(mock.repeatCount).thenReturn(0)
        mReviewer.onKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT, mock)
    }
}
