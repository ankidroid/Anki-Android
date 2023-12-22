/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.libanki.Sound
import net.ankiweb.rsdroid.RustCleanup
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/** Tests Sound Rendering - should be extracted from the GUI at some point */
@RustCleanup("doesn't work with V16")
@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerSoundRenderTest : RobolectricTest() {
    /** Call this after a valid card has been added */
    private val sounds by lazy {
        val ret = super.startRegularActivity<Reviewer>()
        assertThat("activity was started before it had cards", ret.isDestroyed, equalTo(false))
        ret
    }

    @Test
    fun sound_on_front() {
        addNoteUsingBasicModel("[sound:a.mp3]", "back")

        assertThat(sounds.q(), hasSize(1))

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), hasSize(1))
        assertThat(sounds.a(), nullValue())
        assertThat("despite being included in the answer by {{FrontSide}}, play once", sounds.qa(), hasSize(1))
    }

    @Test
    fun sound_on_back() {
        addNoteUsingBasicModel("front", "[sound:a.mp3]")

        assertThat(sounds.q(), nullValue())

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), nullValue())
        assertThat(sounds.a(), hasSize(1))
        assertThat(sounds.qa(), hasSize(1))
    }

    @Test
    fun different_sound_on_front_and_back() {
        addNoteUsingBasicModel("[sound:a.mp3]", "[sound:b.mp3]")

        assertThat(sounds.q(), hasSize(1))

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), hasSize(1))
        assertThat(sounds.a(), hasSize(1))
        assertThat(sounds.qa(), hasSize(2))
    }

    @Test
    fun same_sound_on_front_and_back() {
        addNoteUsingBasicModel("[sound:a.mp3]", "[sound:a.mp3]")

        assertThat(sounds.q(), hasSize(1))

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), hasSize(1))
        assertThat(sounds.a(), hasSize(1))
        assertThat("Despite being in FrontSide, the sound is added again, so play it twice", sounds.qa(), hasSize(2))
    }

    @Test
    fun same_sound_on_front_and_back_twice() {
        addNoteUsingBasicModel("[sound:a.mp3][sound:a.mp3]", "[sound:a.mp3]")

        assertThat(sounds.q(), hasSize(2))

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), hasSize(2))
        assertThat(sounds.a(), hasSize(1))
        assertThat("Despite being in FrontSide, the sound is added again, so play it twice", sounds.qa(), hasSize(3))
    }

    @Test
    fun same_sound_on_front_and_back_no_frontSide() {
        addNoteWithNoFrontSide("[sound:a.mp3]", "[sound:a.mp3]")

        assertThat(sounds.q(), hasSize(1))

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), hasSize(1))
        assertThat(sounds.a(), hasSize(1))
        assertThat(sounds.qa(), hasSize(2))
    }

    @Test
    fun different_sound_on_front_and_back_no_frontSide() {
        addNoteWithNoFrontSide("[sound:a.mp3]", "[sound:b.mp3]")

        assertThat(sounds.q(), hasSize(1))

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), hasSize(1))
        assertThat(sounds.a(), hasSize(1))
        assertThat(sounds.qa(), hasSize(2))
    }

    @Test
    fun sound_on_back_no_frontSide() {
        addNoteWithNoFrontSide("aa", "[sound:a.mp3]")

        assertThat(sounds.q(), nullValue())

        sounds.executeCommand(ViewerCommand.SHOW_ANSWER)

        assertThat(sounds.q(), nullValue())
        assertThat(sounds.a(), hasSize(1))
        assertThat(sounds.qa(), hasSize(1))
    }

    private fun addNoteWithNoFrontSide(
        front: String,
        back: String,
    ) {
        addNonClozeModel("NoFrontSide", arrayOf("Front", "Back"), "{{Front}}", "{{Back}}")
        addNoteUsingModelName("NoFrontSide", front, back)
    }

    fun Reviewer.a() = mSoundPlayer.getSounds(Sound.SoundSide.ANSWER)

    fun Reviewer.q() = mSoundPlayer.getSounds(Sound.SoundSide.QUESTION)

    fun Reviewer.qa() = mSoundPlayer.getSounds(Sound.SoundSide.QUESTION_AND_ANSWER)
}
