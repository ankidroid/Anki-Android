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
package com.ichi2.anki.multimediacard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AudioRecorderAndroidTest : RobolectricTest() {

    private lateinit var audioRecorder: AudioRecorder

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        audioRecorder = AudioRecorder()
    }

    // verify that the audio recorder is initialized in high sampling mode
    @Test
    @Throws(IOException::class)
    fun testStartRecordingHighSampling() {
        val recordingHandler = Mockito.mock(Runnable::class.java)
        audioRecorder.setOnRecordingInitializedHandler(recordingHandler)
        audioRecorder.startRecording(targetContext, "testpath")
        Mockito.verify(recordingHandler, Mockito.times(1)).run()
    }

    // verify that the audio recorder is initialized in low sampling mode
    @Test
    @Throws(IOException::class)
    fun testRecordingLowSampling() {
        class InitHandlerWithError : Runnable {
            var timesRun = 0
                private set
            private var hasThrown = false
            override fun run() {
                timesRun++
                if (!hasThrown) {
                    hasThrown = true
                    throw RuntimeException()
                }
            }
        }

        val recordingHandler = InitHandlerWithError()
        audioRecorder.setOnRecordingInitializedHandler(recordingHandler)
        audioRecorder.startRecording(targetContext, "testpath")
        Assert.assertEquals("Initialization handler should run twice", 2, recordingHandler.timesRun.toLong())
    }
}
