/*
 *  Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>
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
        mAudioRecorder.startRecording(getTargetContext(), "testpath");

        verify(recordingHandler, times(1)).run();
    }

    //verify that the audio recorder is initialized in low sampling mode
    @Test
    public void testRecordingLowSampling() throws IOException {

        class initHandlerWithError implements Runnable {
            private int mTimesRun = 0;
            private boolean mHasThrown = false;
            @Override
            public void run() {
                mTimesRun++;
                if(!mHasThrown) {
                    mHasThrown = true;
                    //the try-catch in AudioRecorder should catch this exception and move to low-sampling mode
                    throw new RuntimeException();
                }
            }
            public int getTimesRun() {
                return mTimesRun;
            }
        }
        initHandlerWithError recordingHandler = new initHandlerWithError();
        mAudioRecorder.setOnRecordingInitializedHandler(recordingHandler);
        mAudioRecorder.startRecording(getTargetContext(), "testpath");

        assertEquals("Initialization handler should run twice", 2, recordingHandler.getTimesRun());
    }


}
