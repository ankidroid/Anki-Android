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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewer.AutomaticAnswer.AutomaticallyAnswered
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class AutomaticAnswerTest {

    @Test
    fun disableWorks() {
        val answer = validAnswer(automaticallyAnsweredMock())

        answer.delayedShowQuestion(0)
        answer.delayedShowAnswer(0)

        assertTrue(answer.timeoutHandler.hasMessages(0), "it should have messages")
        assertFalse(answer.isDisabled, "answer should be enabled")

        answer.disable()

        assertFalse(answer.timeoutHandler.hasMessages(0), "it should not have messages")
        assertTrue(answer.isDisabled, "answer should be disabled")
    }

    @Test
    fun noExecutionIfTimerIsZero_issue8923() {
        val answer = AutomaticAnswer(
            target = automaticallyAnsweredMock(),
            settings = AutomaticAnswerSettings(
                useTimer = true,
                questionDelaySeconds = 0,
                answerDelaySeconds = 0
            )
        )

        answer.scheduleAutomaticDisplayQuestion(10)

        assertFalse(answer.timeoutHandler.hasMessages(0), "no messages even if delay provided")

        answer.scheduleAutomaticDisplayAnswer(10)

        assertFalse(answer.timeoutHandler.hasMessages(0), "no messages even if delay provided")
    }

    @Test
    fun testEnableDisable() {
        val answer = validAnswer(automaticallyAnsweredMock())
        assertFalse(answer.isDisabled, "answer should be enabled")
        answer.disable()
        assertTrue(answer.isDisabled, "answer should be disabled")
        answer.enable()
        assertFalse(answer.isDisabled, "answer should be enabled")
    }

    /** Ensures [disableStopsImmediateCallAnswer] can fail */
    @Test
    fun immediateCall() {
        val answerValue = AutoAnswerMock()
        val answer = validAnswer(answerValue)

        answer.scheduleAutomaticDisplayAnswer()
        waitForTaskCompletion()
        assertTrue(answerValue.answerShown, "answer should be shown")

        answer.scheduleAutomaticDisplayQuestion()
        waitForTaskCompletion()
        assertTrue(answerValue.questionShown, "question should be shown")
    }

    @Test
    fun disableStopsImmediateCallAnswer() {
        val answerValue = AutoAnswerMock()
        val answer = validAnswer(answerValue)
        answer.scheduleAutomaticDisplayAnswer()
        answer.disable()
        assertFalse(answerValue.answerShown, "call did not complete early")
        waitForTaskCompletion()
        assertFalse(answerValue.answerShown, "call not executed due to disable")
    }

    @Test
    fun disableStopsImmediateCallQuestion() {
        val answerValue = AutoAnswerMock()
        val answer = validAnswer(answerValue)
        answer.scheduleAutomaticDisplayQuestion()
        answer.disable()
        assertFalse(answerValue.questionShown, "call did not complete early")
        waitForTaskCompletion()
        assertFalse(answerValue.questionShown, "call not executed due to disable")
    }

    private fun waitForTaskCompletion() {
        runUiThreadTasksIncludingDelayedTasks()
    }

    private fun validAnswer(automaticallyAnswered: AutomaticallyAnswered? = null) = AutomaticAnswer(
        target = automaticallyAnswered ?: automaticallyAnsweredMock(),
        settings = AutomaticAnswerSettings(
            useTimer = true,
            questionDelaySeconds = 10,
            answerDelaySeconds = 10
        )
    )

    private class AutoAnswerMock(var answerShown: Boolean = false, var questionShown: Boolean = false) : AutomaticallyAnswered {
        override fun automaticShowAnswer() {
            answerShown = true
        }

        override fun automaticShowQuestion(action: AutomaticAnswerAction) {
            questionShown = true
        }
    }

    private fun automaticallyAnsweredMock(): AutomaticallyAnswered = mock()
}
