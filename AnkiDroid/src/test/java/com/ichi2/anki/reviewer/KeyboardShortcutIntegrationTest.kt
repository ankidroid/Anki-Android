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
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.robolectric.Shadows
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("auto IDE fixes")
@KotlinCleanup("`is` -> equalTo")
@KotlinCleanup("`when` -> whenever")
class KeyboardShortcutIntegrationTest : RobolectricTest() {
    @KotlinCleanup("lateinit")
    private var mReviewer: Reviewer? = null
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

    protected fun assertStatus(recording: AudioView.Status) {
        assertThat(mReviewer!!.audioView!!.status, `is`(recording))
    }

    @KotlinCleanup("scope function")
    protected fun setupPlayerMock(): AudioPlayer {
        assertThat(mReviewer!!.openMicToolbar(), `is`(true))
        val player = mock(AudioPlayer::class.java)
        mReviewer!!.audioView!!.setPlayer(player)
        return player
    }

    protected fun setupRecorderMock(): AudioRecorder {
        assertThat(mReviewer!!.openMicToolbar(), `is`(true))
        val recorder = mock(AudioRecorder::class.java)
        mReviewer!!.audioView!!.setRecorder(recorder)
        return recorder
    }

    private fun pressVThenRelease() {
        depressVKey()
        releaseVKey()
    }

    private fun pressAndHoldShiftV() {
        depressShiftKey()

        val vKey = getVKey()
        `when`(vKey.isShiftPressed).thenReturn(true)
        mReviewer!!.onKeyDown(KeyEvent.KEYCODE_V, vKey)
        `when`(vKey.repeatCount).thenReturn(1)
        mReviewer!!.onKeyDown(KeyEvent.KEYCODE_V, vKey)
    }

    protected fun pressShiftAndVThenRelease() {
        depressShiftKey()
        depressVKeyWithShiftHeld()
        releaseShiftKey()
        releaseVKey()
    }

    private fun releaseVKey() {
        val mock = mock(KeyEvent::class.java)
        `when`(mock.keyCode).thenReturn(KeyEvent.KEYCODE_V)
        `when`(mock.unicodeChar).thenReturn('v'.code)
        `when`(mock.getUnicodeChar(anyInt())).thenReturn('v'.code)
        mReviewer!!.onKeyUp(KeyEvent.KEYCODE_V, mock)
    }

    private fun releaseShiftKey() {
        val mock = mock(KeyEvent::class.java)
        `when`(mock.keyCode).thenReturn(KeyEvent.KEYCODE_SHIFT_LEFT)
        mReviewer!!.onKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT, mock)
    }

    private fun depressVKey() {
        val mock = mock(KeyEvent::class.java)
        `when`(mock.keyCode).thenReturn(KeyEvent.KEYCODE_V)
        `when`(mock.unicodeChar).thenReturn('v'.code)
        `when`(mock.getUnicodeChar(anyInt())).thenReturn('v'.code)
        mReviewer!!.onKeyDown(KeyEvent.KEYCODE_V, mock)
    }

    private fun depressVKeyWithShiftHeld() {
        val mock = mock(KeyEvent::class.java)
        `when`(mock.isShiftPressed).thenReturn(true)
        `when`(mock.keyCode).thenReturn(KeyEvent.KEYCODE_V)
        mReviewer!!.onKeyDown(KeyEvent.KEYCODE_V, mock)
    }

    private fun depressShiftKey() {
        val mock = mock(KeyEvent::class.java)
        `when`(mock.action).thenReturn(0)
        `when`(mock.deviceId).thenReturn(9)
        `when`(mock.downTime).thenReturn(35660208L)
        `when`(mock.eventTime).thenReturn(35660208L)
        `when`(mock.flags).thenReturn(8)
        `when`(mock.keyCode).thenReturn(KeyEvent.KEYCODE_SHIFT_LEFT)
        `when`(mock.metaState).thenReturn(65)
        `when`(mock.scanCode).thenReturn(42)
        `when`(mock.source).thenReturn(257)
        `when`(mock.repeatCount).thenReturn(0)
        mReviewer!!.onKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT, mock)
    }
}
