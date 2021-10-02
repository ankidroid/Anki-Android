/*
 Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimediacard;

import com.ichi2.anki.RobolectricTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;

import java.io.File;
import java.io.IOException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class AudioPlayerTest extends RobolectricTest {

    private AudioPlayer mAudioPlayer;
    private File mFile;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void before() throws IOException {
        mAudioPlayer = new AudioPlayer();
        mFile = tempFolder.newFile("testaudio.wav");

        ShadowMediaPlayer testPlayer = new ShadowMediaPlayer();
        DataSource fileSource = DataSource.toDataSource(mFile.getAbsolutePath());
        ShadowMediaPlayer.MediaInfo fileInfo = new ShadowMediaPlayer.MediaInfo();
        testPlayer.addMediaInfo(fileSource, fileInfo);
    }

    @Test
    public void testPlayOnce() throws IOException {
        Runnable stoppingListener = mock(Runnable.class);
        Runnable stoppedListener = mock(Runnable.class);

        mAudioPlayer.setOnStoppingListener(stoppingListener);
        mAudioPlayer.setOnStoppedListener(stoppedListener);

        runTasksInBackground();
        mAudioPlayer.play(mFile.getAbsolutePath());
        advanceRobolectricLooper();
        //audio should play and finish, with each listener only running once
        verify(stoppingListener, times(1)).run();
        verify(stoppedListener, times(1)).run();

    }

    @Test
    public void testPlayMultipleTimes() throws IOException {
        Runnable stoppingListener = mock(Runnable.class);
        Runnable stoppedListener = mock(Runnable.class);

        mAudioPlayer.setOnStoppingListener(stoppingListener);
        mAudioPlayer.setOnStoppedListener(stoppedListener);

        runTasksInBackground();
        mAudioPlayer.play(mFile.getAbsolutePath());
        advanceRobolectricLooper();
        mAudioPlayer.play(mFile.getAbsolutePath());
        advanceRobolectricLooper();
        mAudioPlayer.play(mFile.getAbsolutePath());
        advanceRobolectricLooper();
        //audio should play and finish three times, with each listener running 3 times total
        verify(stoppingListener, times(3)).run();
        verify(stoppedListener, times(3)).run();

    }

    @Test
    public void testPausing() throws IOException {
        Runnable stoppingListener = mock(Runnable.class);
        Runnable stoppedListener = mock(Runnable.class);

        mAudioPlayer.setOnStoppingListener(stoppingListener);
        mAudioPlayer.setOnStoppedListener(stoppedListener);

        runTasksInBackground();
        mAudioPlayer.play(mFile.getAbsolutePath());
        mAudioPlayer.pause();
        advanceRobolectricLooper();
        //audio is paused, and the listeners should not have run yet
        verify(stoppingListener, times(0)).run();
        verify(stoppedListener, times(0)).run();

        mAudioPlayer.start();
        advanceRobolectricLooper();
        //audio should have finished now after it unpauses, and the listeners should have run once
        verify(stoppingListener, times(1)).run();
        verify(stoppedListener, times(1)).run();
    }

    @Test
    public void testAbruptStop() throws IOException {
        Runnable stoppingListener = mock(Runnable.class);
        Runnable stoppedListener = mock(Runnable.class);

        mAudioPlayer.setOnStoppingListener(stoppingListener);
        mAudioPlayer.setOnStoppedListener(stoppedListener);

        runTasksInBackground();
        mAudioPlayer.play(mFile.getAbsolutePath());
        mAudioPlayer.stop();
        advanceRobolectricLooper();
        // audio was stopped prior to completion, and on investigation of the "stop" listeners,
        // it appears they are hooked to the *completion* of the audio, so it appears correct that
        // no "stop" listeners were called as the audio did not in fact complete
        verify(stoppingListener, times(0)).run();
        verify(stoppedListener, times(0)).run();
    }

    @Test
    public void testPlayOnceWithNullListeners() {
        mAudioPlayer.setOnStoppingListener(null);
        mAudioPlayer.setOnStoppedListener(null);
        assertDoesNotThrow(() -> {
            try {
                runTasksInBackground();
                mAudioPlayer.play(mFile.getAbsolutePath());
                advanceRobolectricLooper();
            } catch (IOException e) {
                assert false; //shouldn't have an IOException
            }
        });
        advanceRobolectricLooper();

    }

    //tests if the stop() function successfully catches an IOException
    @Test
    public void testStopWithIOException() {
        ShadowMediaPlayer.addException(DataSource.toDataSource(mFile.getAbsolutePath()), new IOException("Expected"));
        assertDoesNotThrow( () -> {mAudioPlayer.stop();});
    }
}
