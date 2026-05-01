/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class MultimediaActionHandlerTest {
    @ParameterizedTest
    @EnumSource(MultimediaAction::class)
    fun `every action resolves to a handler bound to that action`(action: MultimediaAction) {
        val handler = MultimediaActionHandler.forAction(action)
        assertThat(handler.action, equalTo(action))
    }

    @Test
    fun `image-file handler creates an ImageField`() {
        assertThat(MultimediaActionHandler.ImageFile.createField(), instanceOf(ImageField::class.java))
    }

    @Test
    fun `camera handler creates an ImageField`() {
        assertThat(MultimediaActionHandler.Camera.createField(), instanceOf(ImageField::class.java))
    }

    @Test
    fun `drawing handler creates an ImageField`() {
        assertThat(MultimediaActionHandler.Drawing.createField(), instanceOf(ImageField::class.java))
    }

    @Test
    fun `audio-clip handler creates a MediaClipField`() {
        assertThat(MultimediaActionHandler.AudioFile.createField(), instanceOf(MediaClipField::class.java))
    }

    @Test
    fun `video-clip handler creates a MediaClipField`() {
        assertThat(MultimediaActionHandler.VideoFile.createField(), instanceOf(MediaClipField::class.java))
    }

    @Test
    fun `audio-recording handler creates an AudioRecordingField`() {
        assertThat(
            MultimediaActionHandler.AudioRecording.createField(),
            instanceOf(AudioRecordingField::class.java),
        )
    }

    @Test
    fun `createField returns a fresh instance per call`() {
        val first = MultimediaActionHandler.ImageFile.createField()
        val second = MultimediaActionHandler.ImageFile.createField()
        assertThat(first, not(sameInstance(second)))
    }
}
