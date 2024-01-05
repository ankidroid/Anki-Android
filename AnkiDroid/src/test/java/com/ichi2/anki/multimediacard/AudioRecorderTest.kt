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

import android.media.MediaRecorder
import org.junit.Before
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class AudioRecorderTest {
    private lateinit var audioRecorder: AudioRecorder

    @Mock(name = "mRecorder")
    private val mockedMediaRecorder: MediaRecorder? = null

    @InjectMocks
    private lateinit var injectedRecorder: AudioRecorder

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        audioRecorder = AudioRecorder()
    }

    // verifies that stopRecording() and release() calls the proper methods in mRecorder
    @Test
    fun testStopAndRelease() {
        injectedRecorder.stopRecording()
        injectedRecorder.release()
        verify(mockedMediaRecorder, times(1))?.stop()
        verify(mockedMediaRecorder, times(1))?.release()
    }
}
