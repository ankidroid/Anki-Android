/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.recorder

import android.content.Context
import android.media.MediaRecorder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.compat.CompatHelper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

@RunWith(AndroidJUnit4::class)
class AudioRecorderTest {
    private lateinit var context: Context
    private lateinit var audioRecorder: AudioRecorder
    private val mockMediaRecorder = mockk<MediaRecorder>(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        mockkObject(CompatHelper)

        every { CompatHelper.compat.getMediaRecorder(any()) } returns mockMediaRecorder

        audioRecorder = AudioRecorder(context)
    }

    @After
    fun teardown() {
        unmockkObject(CompatHelper)
        clearAllMocks()
    }

    @Test
    fun `start should set isRecording to true and configure recorder`() {
        audioRecorder.start()

        assertTrue(audioRecorder.isRecording)
        assertNotNull("The file should still be initialized", audioRecorder.currentFile)

        verify { mockMediaRecorder.prepare() }
        verify { mockMediaRecorder.start() }
    }

    @Test
    fun `stop should set isRecording to false`() {
        audioRecorder.start()
        audioRecorder.stop()

        assertFalse(audioRecorder.isRecording)
        verify { mockMediaRecorder.stop() }
    }

    @Test
    fun `close should release media recorder resources`() {
        audioRecorder.start()

        audioRecorder.close()

        verify { mockMediaRecorder.release() }
    }

    @Test
    fun `start should fallback to AMR if AAC configuration fails`() {
        // Force configure to fail the first time (AAC) and succeed the second (AMR)
        every { mockMediaRecorder.prepare() } throws Exception("AAC not supported") andThen Unit

        audioRecorder.start()

        verify { mockMediaRecorder.reset() }
        verify(exactly = 2) { mockMediaRecorder.prepare() }

        assertTrue(audioRecorder.isRecording)
    }

    @Test
    fun `isRecording should remain false if mediaRecorder start fails`() {
        every { mockMediaRecorder.start() } throws RuntimeException("Hardware failure")

        audioRecorder.start()

        assertFalse(audioRecorder.isRecording)

        assertNotNull("The file should still be initialized", audioRecorder.currentFile)
    }

    @Test
    fun `start should do nothing if already recording`() {
        audioRecorder.start()
        audioRecorder.start()

        verify(exactly = 1) { mockMediaRecorder.start() }
    }

    @Test
    fun `pause and resume should call hardware recorder`() {
        audioRecorder.start()

        audioRecorder.pause()
        verify { mockMediaRecorder.pause() }

        audioRecorder.resume()
        verify { mockMediaRecorder.resume() }
    }

    @Test
    fun `start should use provided file instead of temp file`() {
        val customFile = File(context.cacheDir, "custom_audio.3gp")

        audioRecorder.start(customFile)

        assertEquals(customFile.absolutePath, audioRecorder.currentFile?.absolutePath)
        verify { mockMediaRecorder.setOutputFile(customFile.absolutePath) }
    }
}
