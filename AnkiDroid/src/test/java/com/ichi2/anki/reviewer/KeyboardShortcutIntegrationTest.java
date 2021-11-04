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

package com.ichi2.anki.reviewer;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.view.KeyEvent;

import com.ichi2.anki.Reviewer;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.multimediacard.AudioPlayer;
import com.ichi2.anki.multimediacard.AudioRecorder;
import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.testutils.KeyEventUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;

import java.io.IOException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class KeyboardShortcutIntegrationTest extends RobolectricTest {

    private Reviewer mReviewer;


    @Before
    @Override
    public void setUp() {
        super.setUp();
        addNoteUsingBasicModel("Hello", "World");
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext()).grantPermissions(Manifest.permission.RECORD_AUDIO);
        mReviewer = super.startActivityNormallyOpenCollectionWithIntent(Reviewer.class, new Intent());
        waitForAsyncTasksToComplete();
    }


    /**
     * Press "Shift + V"
     * Release "Shift", then release "V"
     *
     * Expected: Recording is triggered
     * #7780
     */
    @Test
    public void testActionsOccurOnKeyDown() throws IOException {
        AudioRecorder recorder = setupRecorderMock();

        pressShiftAndVThenRelease();

        verify(recorder, times(1)).startRecording(any(), anyString());
        verifyNoMoreInteractions(recorder);
    }

    /**
     * Press V
     * Hold V
     *
     * Expected: Recording is triggered and is not untriggered
     */
    @Test
    public void holdIsNotASecondAction() throws IOException {
        AudioRecorder recorder = setupRecorderMock();

        pressAndHoldShiftV();

        verify(recorder, times(1)).startRecording(any(), anyString());
        verifyNoMoreInteractions(recorder);
    }


    /**
     * Press "Shift + V"
     * While recording press "V"
     *
     * Expected: Current state is stopped and the desired state is triggered
     * #7780
     */
    @Test
    public void playingWhileRecordingStopsRecordingAndStartsPlaying() throws IOException {
        AudioPlayer player = setupPlayerMock();
        AudioRecorder recorder = setupRecorderMock();

        pressShiftAndVThenRelease();

        verify(recorder, times(1)).startRecording(any(), anyString());
        verifyNoMoreInteractions(recorder, player);
        assertStatus(AudioView.Status.RECORDING);

        // now we're set up and recording, pressing V should move us to playing

        pressVThenRelease();

        verify(recorder, times(1)).stopRecording();
        verify(player, times(1)).play(anyString());
        verifyNoMoreInteractions(recorder, player);
        assertStatus(AudioView.Status.PLAYING);
    }


    /**
     * Press " V"
     * While playing press "Shift + V"
     *
     * Expected: Current state is stopped and the desired state is triggered
     * #7780
     */
    @Test
    public void recordingWhilePlayingStopsPlayingAndStartsRecording() throws IOException {
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext()).grantPermissions(Manifest.permission.RECORD_AUDIO);

        AudioPlayer player = setupPlayerMock();
        AudioRecorder recorder = setupRecorderMock();

        // We don't need anything recorded as the mock doesn't mind
        pressVThenRelease();

        verify(player, times(1)).play(anyString());
        verifyNoMoreInteractions(recorder, player);
        assertStatus(AudioView.Status.PLAYING);

        // now we're set up and recording, pressing Shift + V should move us to recording
        pressShiftAndVThenRelease();

        verify(player, times(1)).stop();
        verify(recorder, times(1)).startRecording(any(), anyString());
        verifyNoMoreInteractions(recorder, player);
        assertStatus(AudioView.Status.RECORDING);

    }


    protected void assertStatus(AudioView.Status recording) {
        assertThat(mReviewer.getAudioView().getStatus(), is(recording));
    }


    protected AudioPlayer setupPlayerMock() {
        assertThat(mReviewer.openMicToolbar(), is(true));
        AudioPlayer player = mock(AudioPlayer.class);
        mReviewer.getAudioView().setPlayer(player);
        return player;
    }


    protected AudioRecorder setupRecorderMock() {
        assertThat(mReviewer.openMicToolbar(), is(true));
        AudioRecorder recorder = mock(AudioRecorder.class);
        mReviewer.getAudioView().setRecorder(recorder);
        return recorder;
    }


    private void pressVThenRelease() {
        depressVKey();
        releaseVKey();
    }


    private void pressAndHoldShiftV() {
        depressShiftKey();

        KeyEvent vKey = KeyEventUtils.getVKey();
        when(vKey.isShiftPressed()).thenReturn(true);
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, vKey);
        when(vKey.getRepeatCount()).thenReturn(1);
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, vKey);
    }


    protected void pressShiftAndVThenRelease() {
        depressShiftKey();
        depressVKeyWithShiftHeld();
        releaseShiftKey();
        releaseVKey();
    }


    private void releaseVKey() {
        KeyEvent mock = mock(KeyEvent.class);
        when(mock.getKeyCode()).thenReturn(KeyEvent.KEYCODE_V);
        when(mock.getUnicodeChar()).thenReturn((int) 'v');
        when(mock.getUnicodeChar(anyInt())).thenReturn((int) 'v');
        mReviewer.onKeyUp(KeyEvent.KEYCODE_V, mock);
    }


    private void releaseShiftKey() {
        KeyEvent mock = mock(KeyEvent.class);
        when(mock.getKeyCode()).thenReturn(KeyEvent.KEYCODE_SHIFT_LEFT);
        mReviewer.onKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT, mock);
    }

    private void depressVKey() {
        KeyEvent mock = mock(KeyEvent.class);
        when(mock.getKeyCode()).thenReturn(KeyEvent.KEYCODE_V);
        when(mock.getUnicodeChar()).thenReturn((int) 'v');
        when(mock.getUnicodeChar(anyInt())).thenReturn((int) 'v');
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, mock);
    }


    private void depressVKeyWithShiftHeld() {
        KeyEvent mock = mock(KeyEvent.class);
        when(mock.isShiftPressed()).thenReturn(true);
        when(mock.getKeyCode()).thenReturn(KeyEvent.KEYCODE_V);
        mReviewer.onKeyDown(KeyEvent.KEYCODE_V, mock);
    }


    private void depressShiftKey() {

        KeyEvent mock = mock(KeyEvent.class);
        when(mock.getAction()).thenReturn(0);
        when(mock.getDeviceId()).thenReturn(9);
        when(mock.getDownTime()).thenReturn(35660208L);
        when(mock.getEventTime()).thenReturn(35660208L);
        when(mock.getFlags()).thenReturn(8);
        when(mock.getKeyCode()).thenReturn(KeyEvent.KEYCODE_SHIFT_LEFT);
        when(mock.getMetaState()).thenReturn(65);
        when(mock.getScanCode()).thenReturn(42);
        when(mock.getSource()).thenReturn(257);
        when(mock.getRepeatCount()).thenReturn(0);

        mReviewer.onKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT, mock);
    }
}
