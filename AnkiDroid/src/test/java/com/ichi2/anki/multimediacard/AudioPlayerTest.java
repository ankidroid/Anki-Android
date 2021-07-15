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

    @Before
    public void before() throws IOException {
        mAudioPlayer = new AudioPlayer();
        mFile = tempFolder.newFile("testaudio.wav");

        ShadowMediaPlayer testPlayer = new ShadowMediaPlayer();
        DataSource mFileSource = DataSource.toDataSource(mFile.getAbsolutePath());
        ShadowMediaPlayer.MediaInfo mFileInfo = new ShadowMediaPlayer.MediaInfo();
        testPlayer.addMediaInfo(mFileSource, mFileInfo);
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testPlayOnce() throws IOException {
        Runnable stoppingListener = mock(Runnable.class);
        Runnable stoppedListener = mock(Runnable.class);

        mAudioPlayer.setOnStoppingListener(stoppingListener);
        mAudioPlayer.setOnStoppedListener(stoppedListener);

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

        mAudioPlayer.play(mFile.getAbsolutePath());
        mAudioPlayer.stop();
        advanceRobolectricLooper();
        //audio has immediately stopped, and the stop listeners should not have run
        verify(stoppingListener, times(0)).run();
        verify(stoppedListener, times(0)).run();
    }

    @Test
    public void testPlayOnceWithNullListeners() {
        mAudioPlayer.setOnStoppingListener(null);
        mAudioPlayer.setOnStoppedListener(null);
        assertDoesNotThrow(() -> {
            try {
                mAudioPlayer.play(mFile.getAbsolutePath());
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
