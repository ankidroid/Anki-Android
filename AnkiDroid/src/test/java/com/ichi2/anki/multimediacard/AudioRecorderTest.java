package com.ichi2.anki.multimediacard;


import android.media.MediaRecorder;

import com.ichi2.anki.RobolectricTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class AudioRecorderTest extends RobolectricTest {

    private AudioRecorder mAudioRecorder;

    @Mock (name = "mRecorder")
    private MediaRecorder mMockedMediaRecorder;

    @InjectMocks
    private AudioRecorder mInjectedRecorder;

    @Before
    public void before() {
        MockitoAnnotations.openMocks(this);
        mAudioRecorder = new AudioRecorder();
    }

    //verifies that stopRecording() and release() calls the proper methods in mRecorder
    @Test
    public void testStopAndRelease() {
        mInjectedRecorder.stopRecording();
        mInjectedRecorder.release();

        verify(mMockedMediaRecorder, times(1)).stop();
        verify(mMockedMediaRecorder, times(1)).release();
    }

    //verify that the audio recorder is initialized in high sampling mode
    @Test
    public void testStartRecordingHighSampling() throws IOException {

        Runnable recordingHandler = mock(Runnable.class);

        mAudioRecorder.setOnRecordingInitializedHandler(recordingHandler);
        mAudioRecorder.startRecording("testpath");

        verify(recordingHandler, times(1)).run();
    }

    //verify that the audio recorder is initialized in low sampling mode
    @Test
    public void testRecordingLowSampling() throws IOException {

        class initHandlerWithError implements Runnable {
            private int timesRun = 0;
            private boolean hasThrown = false;
            @Override
            public void run() {
                timesRun++;
                if(!hasThrown) {
                    hasThrown = true;
                    //the try-catch in AudioRecorder should catch this exception and move to low-sampling mode
                    throw new RuntimeException();
                }
            }
            public int getTimesRun() {
                return timesRun;
            }
        }
        initHandlerWithError recordingHandler = new initHandlerWithError();
        mAudioRecorder.setOnRecordingInitializedHandler(recordingHandler);
        mAudioRecorder.startRecording("testpath");

        assertEquals("Initialization handler should run twice", 2, recordingHandler.getTimesRun());
    }


}
