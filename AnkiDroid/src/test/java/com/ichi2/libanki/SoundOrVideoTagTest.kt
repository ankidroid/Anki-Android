/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.SoundOrVideoTag.Type
import com.ichi2.libanki.SoundOrVideoTag.Type.VIDEO
import com.ichi2.testutils.JvmTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundOrVideoTagTest : JvmTest() {
    @Test
    fun mp3IsAudio() {
        val tag = SoundOrVideoTag("test.mp3")
        assertThat(tag.getType(col.media.dir), equalTo(Type.AUDIO))
    }

    @Test
    fun audioIsDefault() {
        // if we don't know, assume it's audio
        // When we want to play a tag, the audio player (Android) can handle a failure
        // better than the video player (HTML)
        val tag = SoundOrVideoTag("test.txt")
        assertThat(tag.getType(col.media.dir), equalTo(Type.AUDIO))
    }

    @Test
    fun mp4IsVideo() {
        // Note: This will go out to the filesystem. As it doesn't exist, assume it's a video
        val tag = SoundOrVideoTag("test.mp4")
        assertThat(tag.getType(col.media.dir), equalTo(VIDEO))
    }
}
