/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.ui.windows.reviewer.autoadvance

import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.asyncIO
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.libanki.Card
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Implementation of the `Auto Advance` deck options
 *
 * A timer (in seconds) can be set to automatically trigger an action after it runs out,
 * either in the question side ([QuestionAction]) or in the answer side ([AnswerAction]).
 *
 * If a timer is set to 0, the corresponding action is not triggered.
 *
 * @see AutoAdvanceSettings
 */
class AutoAdvance(val viewModel: ReviewerViewModel) {
    private var questionActionJob: Job? = null
    private var answerActionJob: Job? = null

    private var settings = viewModel.asyncIO {
        val card = viewModel.currentCard.await()
        AutoAdvanceSettings.createInstance(card.currentDeckId().did)
    }

    private suspend fun durationToShowQuestionFor() = settings.await().durationToShowQuestionFor
    private suspend fun durationToShowAnswerFor() = settings.await().durationToShowAnswerFor
    private suspend fun questionAction() = settings.await().questionAction
    private suspend fun answerAction() = settings.await().answerAction
    suspend fun shouldWaitForAudio() = settings.await().waitForAudio

    fun cancelQuestionAndAnswerActionJobs() {
        questionActionJob?.cancel()
        answerActionJob?.cancel()
    }

    fun onCardChange(card: Card) {
        cancelQuestionAndAnswerActionJobs()
        settings = viewModel.asyncIO {
            AutoAdvanceSettings.createInstance(card.currentDeckId().did)
        }
    }

    suspend fun onShowQuestion() {
        answerActionJob?.cancel()
        if (!durationToShowQuestionFor().isPositive()) return

        questionActionJob = viewModel.launchCatchingIO {
            delay(durationToShowQuestionFor())
            when (questionAction()) {
                QuestionAction.SHOW_ANSWER -> viewModel.showAnswer()
                QuestionAction.SHOW_REMINDER -> showReminder(TR.studyingQuestionTimeElapsed())
            }
        }
    }

    suspend fun onShowAnswer() {
        questionActionJob?.cancel()
        if (!durationToShowAnswerFor().isPositive()) return

        answerActionJob = viewModel.launchCatchingIO {
            delay(durationToShowAnswerFor())
            when (answerAction()) {
                AnswerAction.BURY_CARD -> viewModel.buryCard()
                AnswerAction.ANSWER_AGAIN -> viewModel.answerAgain()
                AnswerAction.ANSWER_HARD -> viewModel.answerHard()
                AnswerAction.ANSWER_GOOD -> viewModel.answerGood()
                AnswerAction.SHOW_REMINDER -> showReminder(TR.studyingAnswerTimeElapsed())
            }
        }
    }

    private fun showReminder(message: String) {
        viewModel.launchCatchingIO {
            viewModel.actionFeedbackFlow.emit(message)
        }
    }
}
