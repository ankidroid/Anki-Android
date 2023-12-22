/***************************************************************************************
 * Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import anki.frontend.SetSchedulingStatesRequest
import com.ichi2.anki.pages.AnkiServer
import timber.log.Timber

class ReviewerServer(activity: AbstractFlashcardViewer) : AnkiServer(activity) {
    private val jsApi = activity.javaScriptFunction()

    override fun start() {
        super.start()
        Timber.i("Starting server on http://$LOCALHOST:$listeningPort")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Timber.d("${session.method} $uri")
        if (session.method == Method.POST) {
            val inputBytes = getSessionBytes(session)
            if (uri.startsWith(ANKI_PREFIX)) {
                return buildResponse {
                    handlePostRequest(uri.substring(ANKI_PREFIX.length), inputBytes)
                }
            }
            if (uri.startsWith(ANKIDROID_JS_PREFIX)) {
                return buildResponse {
                    jsApi.handleJsApiRequest(uri.substring(ANKIDROID_JS_PREFIX.length), inputBytes, activity is Reviewer)
                }
            }
        }

        Timber.w("not found: $uri")
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
    }

    private fun handlePostRequest(methodName: String, bytes: ByteArray): ByteArray {
        return when (methodName) {
            "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
            "setSchedulingStates" -> setSchedulingStates(bytes)
            else -> {
                throw Exception("unhandled request: $methodName")
            }
        }
    }

    private fun reviewer(): Reviewer {
        return (activity as Reviewer)
    }

    private fun getSchedulingStatesWithContext(): ByteArray {
        val state = reviewer().queueState ?: return ByteArray(0)
        return state.schedulingStatesWithContext().toBuilder()
            .mergeStates(
                state.states.toBuilder().mergeCurrent(
                    state.states.current.toBuilder()
                        .setCustomData(state.topCard.toBackendCard().customData).build()
                ).build()
            )
            .build()
            .toByteArray()
    }

    private fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val reviewer = reviewer()
        val state = reviewer.queueState
        if (state == null) {
            reviewer.statesMutated = true
            return ByteArray(0)
        }
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == reviewer.customSchedulingKey) {
            state.states = req.states
        }
        reviewer.statesMutated = true
        return ByteArray(0)
    }
}
