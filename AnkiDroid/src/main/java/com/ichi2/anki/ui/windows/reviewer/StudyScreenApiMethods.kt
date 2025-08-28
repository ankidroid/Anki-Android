/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import anki.scheduler.CardAnswer
import com.ichi2.anki.common.utils.ext.getIntOrNull
import com.ichi2.anki.jsapi.Endpoint
import com.ichi2.anki.jsapi.JsApi
import org.json.JSONObject

suspend fun ReviewerViewModel.handleStudyScreenEndpoint(
    endpoint: Endpoint.StudyScreen,
    data: JSONObject?,
): ByteArray {
    return when (endpoint) {
        Endpoint.StudyScreen.GET_NEW_COUNT -> JsApi.success(countsFlow.value.new)
        Endpoint.StudyScreen.GET_LEARNING_COUNT -> JsApi.success(countsFlow.value.learn)
        Endpoint.StudyScreen.GET_TO_REVIEW_COUNT -> JsApi.success(countsFlow.value.review)
        Endpoint.StudyScreen.SHOW_ANSWER -> {
            if (showingAnswer.value) return JsApi.success()
            onShowAnswer()
            JsApi.success()
        }
        Endpoint.StudyScreen.ANSWER -> {
            val ratingNumber = data?.getIntOrNull("rating") ?: return JsApi.fail("Missing rating")
            if (ratingNumber !in 1..4) {
                return JsApi.fail("Invalid rating")
            }
            val rating = CardAnswer.Rating.forNumber(ratingNumber - 1)
            answerCard(rating)
            JsApi.success()
        }
        Endpoint.StudyScreen.IS_SHOWING_ANSWER -> JsApi.success(showingAnswer.value)
        Endpoint.StudyScreen.GET_NEXT_TIME -> {
            val ratingNumber = data?.getIntOrNull("rating") ?: return JsApi.fail("Missing rating")
            if (ratingNumber !in 1..4) {
                return JsApi.fail("Invalid rating")
            }
            val rating = CardAnswer.Rating.forNumber(ratingNumber - 1)

            val queueState = queueState.await() ?: return JsApi.fail("There is no card at top of the queue")

            val nextTimes = AnswerButtonsNextTime.from(queueState)
            val nextTime =
                when (rating) {
                    CardAnswer.Rating.AGAIN -> nextTimes.again
                    CardAnswer.Rating.HARD -> nextTimes.hard
                    CardAnswer.Rating.GOOD -> nextTimes.good
                    CardAnswer.Rating.EASY -> nextTimes.easy
                    CardAnswer.Rating.UNRECOGNIZED -> return JsApi.fail("Invalid rating")
                }
            JsApi.success(nextTime)
        }
        Endpoint.StudyScreen.GET_NEXT_TIMES -> {
            val queueState = queueState.await() ?: return JsApi.fail("There is no card at top of the queue")
            val nextTimes = AnswerButtonsNextTime.from(queueState)
            val result = listOf(nextTimes.again, nextTimes.hard, nextTimes.good, nextTimes.easy)
            JsApi.success(result)
        }
        Endpoint.StudyScreen.OPEN_CARD_INFO -> {
            val cardId = data?.getLong("cardId")
            emitCardInfoDestination(cardId)
            JsApi.success()
        }
        Endpoint.StudyScreen.OPEN_NOTE_EDITOR -> {
            val cardId = data?.getLong("cardId")
            emitEditNoteDestination(cardId)
            JsApi.success()
        }
        Endpoint.StudyScreen.DELETE_NOTE -> {
            deleteNote()
            JsApi.success()
        }
    }
}
