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

import androidx.fragment.app.FragmentActivity
import anki.frontend.SetSchedulingStatesRequest
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.utils.AssetHelper
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class ReviewerServer(activity: FragmentActivity, val mediaDir: String) : AnkiServer(activity) {
    var reviewerHtml: String = ""

    override fun start() {
        super.start()
        Timber.i("Starting server on http://127.0.0.1:$listeningPort")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Timber.d("${session.method} $uri")
        if (session.method == Method.GET) {
            if (uri == "/reviewer.html") {
                return newFixedLengthResponse(reviewerHtml)
            }
            if (uri.startsWith("/assets/")) {
                val mime = getMimeFromUri(uri)
                val stream = when (uri) {
                    "/assets/reviewer_extras_bundle.js" ->
                        this.javaClass.classLoader!!.getResourceAsStream("web/reviewer_extras_bundle.js")
                    "/assets/reviewer_extras.css" ->
                        this.javaClass.classLoader!!.getResourceAsStream("web/reviewer_extras.css")
                    else ->
                        this.javaClass.classLoader!!.getResourceAsStream(uri.substring(1))
                }
                if (stream != null) {
                    Timber.v("OK: $uri")
                    return newChunkedResponse(Response.Status.OK, mime, stream)
                }
            }

            // fall back to looking in media folder
            val file = File(mediaDir, uri.substring(1))
            if (file.exists()) {
                val inputStream = FileInputStream(file)
                val mimeType = AssetHelper.guessMimeType(uri)
                Timber.v("OK: $uri")
                return newChunkedResponse(Response.Status.OK, mimeType, inputStream)
                // probably don't need this anymore
                // resp.addHeader("Access-Control-Allow-Origin", "*")
            }
        } else if (session.method == Method.POST) {
            val inputBytes = getSessionBytes(session)
            if (uri.startsWith(ANKI_PREFIX)) {
                return buildResponse {
                    handlePostRequest(uri.substring(ANKI_PREFIX.length), inputBytes)
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
        val state = reviewer.queueState ?: return ByteArray(0)
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == reviewer.customSchedulingKey) {
            state.states = req.states
        }
        reviewer.customSchedulerIdlingResource.decrement()
        return ByteArray(0)
    }
}
