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

import com.ichi2.anki.Reviewer
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.AutomaticAnswerAction.*
import com.ichi2.anki.reviewer.AutomaticAnswerAction.Companion.fromPreferenceValue
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AutomaticAnswerActionTest {

    @Test
    fun fromPreferenceValue() {
        assertThat(fromPreferenceValue(0), equalTo(BURY_CARD))
        assertThat(fromPreferenceValue(1), equalTo(ANSWER_AGAIN))
        assertThat(fromPreferenceValue(2), equalTo(ANSWER_HARD))
        assertThat(fromPreferenceValue(3), equalTo(ANSWER_GOOD))
        assertThat(fromPreferenceValue(4), equalTo(ANSWER_EASY))
    }

    @Test
    fun testExecute() {
        assertExecuteReturns(BURY_CARD, ViewerCommand.BURY_CARD)

        assertExecuteReturns(ANSWER_AGAIN, ViewerCommand.FLIP_OR_ANSWER_EASE1)
        assertExecuteReturns(ANSWER_HARD, ViewerCommand.FLIP_OR_ANSWER_EASE2)
        assertExecuteReturns(ANSWER_GOOD, ViewerCommand.FLIP_OR_ANSWER_EASE3)
        assertExecuteReturns(ANSWER_EASY, ViewerCommand.FLIP_OR_ANSWER_EASE4)
    }

    private fun assertExecuteReturns(action: AutomaticAnswerAction, expectedCommand: ViewerCommand) {
        val captor = argumentCaptor<ViewerCommand>()
        val mock: Reviewer = mock {
            on { executeCommand(captor.capture()) } doReturn true
        }

        action.execute(mock)

        assertThat(captor.firstValue, equalTo(expectedCommand))
    }
}
