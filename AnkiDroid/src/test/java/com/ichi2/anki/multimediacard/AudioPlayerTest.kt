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
package com.ichi2.anki.multimediacard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.*
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.io.IOException
import kotlin.Throws

@RunWith(AndroidJUnit4::class)
class AudioPlayerTest : RobolectricTest() {
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var file: File

    @get:Rule
    var temporaryDirectory = TemporaryFolder()

    @Before
    @Throws(IOException::class)
    fun before() {
        audioPlayer = AudioPlayer()
        file = temporaryDirectory.newFile("testaudio.wav")

        val fileSource = DataSource.toDataSource(file.absolutePath)
        val fileInfo = ShadowMediaPlayer.MediaInfo()
        ShadowMediaPlayer.addMediaInfo(fileSource, fileInfo)
    }

    @Test
    @Throws(IOException::class)
    fun testPlayOnce() {
        val stoppingListener = mock<() -> Unit>()
        val stoppedListener = mock<() -> Unit>()

        audioPlayer.onStoppingListener = stoppingListener
        audioPlayer.onStoppedListener = stoppedListener

        audioPlayer.play(file.absolutePath)
        advanceRobolectricLooper()
        // audio should play and finish, with each listener only running once
        verify(stoppingListener, times(1)).invoke()
        verify(stoppedListener, times(1)).invoke()
    }

    @Test
    @Throws(IOException::class)
    fun testPlayMultipleTimes() {
        val stoppingListener = mock<() -> Unit>()
        val stoppedListener = mock<() -> Unit>()

        audioPlayer.onStoppingListener = stoppingListener
        audioPlayer.onStoppedListener = stoppedListener

        audioPlayer.play(file.absolutePath)
        advanceRobolectricLooper()
        audioPlayer.play(file.absolutePath)
        advanceRobolectricLooper()
        audioPlayer.play(file.absolutePath)
        advanceRobolectricLooper()
        // audio should play and finish three times, with each listener running 3 times total
        verify(stoppingListener, times(3)).invoke()
        verify(stoppedListener, times(3)).invoke()
    }

    @Test
    @Throws(IOException::class)
    fun testPausing() {
        val stoppingListener = mock<() -> Unit>()
        val stoppedListener = mock<() -> Unit>()

        audioPlayer.onStoppingListener = stoppingListener
        audioPlayer.onStoppedListener = stoppedListener

        audioPlayer.play(file.absolutePath)
        audioPlayer.pause()
        advanceRobolectricLooper()
        // audio is paused, and the listeners should not have run yet
        verify(stoppingListener, times(0)).invoke()
        verify(stoppedListener, times(0)).invoke()

        audioPlayer.start()
        advanceRobolectricLooper()
        // audio should have finished now after it unpauses, and the listeners should have run once
        verify(stoppingListener, times(1)).invoke()
        verify(stoppedListener, times(1)).invoke()
    }

    @Test
    @Throws(IOException::class)
    fun testAbruptStop() {
        val stoppingListener = mock<() -> Unit>()
        val stoppedListener = mock<() -> Unit>()

        audioPlayer.onStoppingListener = stoppingListener
        audioPlayer.onStoppedListener = stoppedListener

        audioPlayer.play(file.absolutePath)
        audioPlayer.stop()
        advanceRobolectricLooper()
        // audio was stopped prior to completion, and on investigation of the "stop" listeners,
        // it appears they are hooked to the *completion* of the audio, so it appears correct that
        // no "stop" listeners were called as the audio did not in fact complete
        verify(stoppingListener, times(0)).invoke()
        verify(stoppedListener, times(0)).invoke()
    }

    @Test
    fun testPlayOnceWithNullListeners() {
        audioPlayer.onStoppingListener = null
        audioPlayer.onStoppedListener = null
        assertDoesNotThrow {
            try {
                audioPlayer.play(file.absolutePath)
                advanceRobolectricLooper()
            } catch (e: IOException) {
                assert(
// shouldn't have an IOException
                    false,
                )
            }
        }
        advanceRobolectricLooper()
    }

    // tests if the stop() function successfully catches an IOException
    @Test
    fun testStopWithIOException() {
        ShadowMediaPlayer.addException(DataSource.toDataSource(file.absolutePath), IOException("Expected"))
        assertDoesNotThrow { audioPlayer.stop() }
    }
}
