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

package com.ichi2.anki.reviewer

import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.AnswerButtons.*
import com.ichi2.anki.reviewer.AnswerButtons.Companion.canAnswerEasy
import com.ichi2.anki.reviewer.AnswerButtons.Companion.canAnswerHard
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AnswerButtonsTest {
    @Test
    fun checkTwoButtons() {
        val numberOfButtons = 2
        assertThat(AGAIN.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE1))
        assertThat("hard", canAnswerHard(numberOfButtons), equalTo(false))
        assertThat(GOOD.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE2))
        assertThat("easy", canAnswerEasy(numberOfButtons), equalTo(false))
    }

    @Test
    fun checkThreeButtons() {
        val numberOfButtons = 3

        assertThat(AGAIN.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE1))
        assertThat("hard", canAnswerHard(numberOfButtons), equalTo(false))
        assertThat(GOOD.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE2))
        assertThat(EASY.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE3))

        assertThat("easy", canAnswerEasy(numberOfButtons), equalTo(true))
    }

    @Test
    fun checkFourButtons() {
        val numberOfButtons = 4

        assertThat(AGAIN.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE1))
        assertThat(HARD.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE2))
        assertThat(GOOD.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE3))
        assertThat(EASY.toViewerCommand(numberOfButtons), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE4))

        assertThat("easy", canAnswerEasy(numberOfButtons), equalTo(true))
        assertThat("hard", canAnswerHard(numberOfButtons), equalTo(true))
    }
}
