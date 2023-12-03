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
    val jsApi = if (activity is Reviewer) {
        reviewer().javaScriptFunction()
    } else {
        cardTemplatePreviewer().javaScriptFunction()
    }

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
            if (uri.startsWith(ANKIDROID_JS_PREFIX)) {
                return buildResponse {
                    handleJsApiPostRequest(uri.substring(ANKIDROID_JS_PREFIX.length), inputBytes)
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

    private suspend fun handleJsApiPostRequest(methodName: String, bytes: ByteArray): ByteArray {
        return when (methodName) {
            "init" -> jsApi.init(bytes)
            "newCardCount" -> jsApi.ankiGetNewCardCount(bytes)
            "lrnCardCount" -> jsApi.ankiGetLrnCardCount(bytes)
            "revCardCount" -> jsApi.ankiGetRevCardCount(bytes)
            "eta" -> jsApi.ankiGetETA(bytes)
            "cardMark" -> jsApi.ankiGetCardMark(bytes)
            "cardFlag" -> jsApi.ankiGetCardFlag(bytes)
            "cardReps" -> jsApi.ankiGetCardReps(bytes)
            "cardInterval" -> jsApi.ankiGetCardInterval(bytes)
            "cardFactor" -> jsApi.ankiGetCardFactor(bytes)
            "cardMod" -> jsApi.ankiGetCardMod(bytes)
            "cardId" -> jsApi.ankiGetCardId(bytes)
            "cardNid" -> jsApi.ankiGetCardNid(bytes)
            "cardType" -> jsApi.ankiGetCardType(bytes)
            "cardDid" -> jsApi.ankiGetCardDid(bytes)
            "cardLeft" -> jsApi.ankiGetCardLeft(bytes)
            "cardODid" -> jsApi.ankiGetCardODid(bytes)
            "cardODue" -> jsApi.ankiGetCardODue(bytes)
            "cardQueue" -> jsApi.ankiGetCardQueue(bytes)
            "cardLapses" -> jsApi.ankiGetCardLapses(bytes)
            "cardDue" -> jsApi.ankiGetCardDue(bytes)
            "deckName" -> jsApi.ankiGetDeckName(bytes)
            "isActiveNetworkMetered" -> jsApi.ankiIsActiveNetworkMetered(bytes)
            "ttsSetLanguage" -> jsApi.ankiTtsSetLanguage(bytes)
            "ttsSpeak" -> jsApi.ankiTtsSpeak(bytes)
            "ttsIsSpeaking" -> jsApi.ankiTtsIsSpeaking(bytes)
            "ttsSetPitch" -> jsApi.ankiTtsSetPitch(bytes)
            "ttsSetSpeechRate" -> jsApi.ankiTtsSetSpeechRate(bytes)
            "ttsFieldModifierIsAvailable" -> jsApi.ankiTtsFieldModifierIsAvailable(bytes)
            "ttsStop" -> jsApi.ankiTtsStop(bytes)
            "nextTime1" -> jsApi.ankiGetNextTime1(bytes)
            "nextTime2" -> jsApi.ankiGetNextTime2(bytes)
            "nextTime3" -> jsApi.ankiGetNextTime3(bytes)
            "nextTime4" -> jsApi.ankiGetNextTime4(bytes)
            "searchCard" -> jsApi.ankiSearchCard(bytes)
            "searchCardWithCallback" -> jsApi.ankiSearchCardWithCallback(bytes)
            "buryCard" -> jsApi.ankiBuryCard(bytes)
            "buryNote" -> jsApi.ankiBuryNote(bytes)
            "suspendCard" -> jsApi.ankiSuspendCard(bytes)
            "suspendNote" -> jsApi.ankiSuspendNote(bytes)
            "setCardDue" -> jsApi.ankiSetCardDue(bytes)
            "resetProgress" -> jsApi.ankiResetProgress(bytes)
            "isDisplayingAnswer" -> jsApi.ankiIsDisplayingAnswer(bytes)
            "addTagToCard" -> jsApi.ankiAddTagToCard(bytes)
            "isInFullscreen" -> jsApi.ankiIsInFullscreen(bytes)
            "isTopbarShown" -> jsApi.ankiIsTopbarShown(bytes)
            "isInNightMode" -> jsApi.ankiIsInNightMode(bytes)
            "enableHorizontalScrollbar" -> jsApi.ankiEnableHorizontalScrollbar(bytes)
            "enableVerticalScrollbar" -> jsApi.ankiEnableVerticalScrollbar(bytes)
            "toggleFlag" -> jsApi.ankiToggleFlag(bytes)
            "markCard" -> jsApi.ankiMarkCard(bytes)
            else -> {
                throw Exception("unhandled request: $methodName")
            }
        }
    }

    private fun reviewer(): Reviewer {
        return (activity as Reviewer)
    }

    private fun cardTemplatePreviewer(): CardTemplatePreviewer {
        return (activity as CardTemplatePreviewer)
    }

    private fun getSchedulingStatesWithContext(): ByteArray {
        val state = reviewer().queueState ?: return ByteArray(0)
        return state.schedulingStatesWithContext().toByteArray()
    }

    private fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val reviewer = reviewer()
        val state = reviewer.queueState ?: return ByteArray(0)
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == reviewer.customSchedulingKey) {
            state.states = req.states
        }
        return ByteArray(0)
    }
}
