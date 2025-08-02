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
package com.ichi2.anki.previewer

import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.CallSuper
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.cardviewer.MediaErrorBehavior
import com.ichi2.anki.cardviewer.MediaErrorHandler
import com.ichi2.anki.cardviewer.MediaErrorListener
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.multimedia.getAvTag
import com.ichi2.anki.multimedia.replaceAvRefsWithPlayButtons
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.PostRequestHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

abstract class CardViewerViewModel :
    ViewModel(),
    OnErrorListener,
    PostRequestHandler {
    override val onError = MutableSharedFlow<String>()
    val onMediaError = MutableSharedFlow<String>()
    val onTtsError = MutableSharedFlow<TtsPlayer.TtsError>()
    val mediaErrorHandler = MediaErrorHandler()

    val eval = MutableSharedFlow<String>()

    open val showingAnswer = MutableStateFlow(false)

    protected val cardMediaPlayer =
        CardMediaPlayer(
            javascriptEvaluator = { launchCatchingIO { eval.emit(it) } },
            mediaErrorListener = createSoundErrorListener(),
        ).also {
            addCloseable(it)
        }
    abstract var currentCard: Deferred<Card>

    abstract val server: AnkiServer

    @CallSuper
    override fun onCleared() {
        server.stop()
        super.onCleared()
    }

    /* *********************************************************************************************
     ************************ Public methods: meant to be used by the View **************************
     ********************************************************************************************* */

    /**
     * Call this after the webView has finished loading the page
     *
     * @param isAfterRecreation whether this is being called after an `Activity` recreation
     */
    abstract fun onPageFinished(isAfterRecreation: Boolean)

    fun baseUrl(): String = server.baseUrl()

    fun setSoundPlayerEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            cardMediaPlayer.setEnabled(isEnabled)
        }
    }

    fun playSoundFromUrl(url: String) {
        launchCatchingIO {
            getAvTag(currentCard.await(), url)?.let {
                cardMediaPlayer.playOne(it)
            }
        }
    }

    fun onVideoFinished() = cardMediaPlayer.onVideoFinished()

    // A coroutine in the cardMediaPlayer waits for the video to complete
    // This cancels it
    fun onVideoPaused() = cardMediaPlayer.onVideoPaused()

    /* *********************************************************************************************
     *************************************** Internal methods ***************************************
     ********************************************************************************************* */

    protected abstract suspend fun typeAnsFilter(text: String): String

    private suspend fun bodyClass() = bodyClassForCardOrd(currentCard.await().ord)

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L358) */
    private suspend fun mungeQA(text: String) = typeAnsFilter(prepareCardTextForDisplay(text))

    private suspend fun prepareCardTextForDisplay(text: String): String =
        replaceAvRefsWithPlayButtons(
            text = withCol { media.escapeMediaFilenames(text) },
            renderOutput = currentCard.await().let { card -> withCol { card.renderOutput(this) } },
        )

    protected open suspend fun showQuestion() {
        Timber.v("showQuestion")
        showingAnswer.emit(false)

        val card = currentCard.await()
        val questionData = withCol { card.question(this) }
        val question = mungeQA(questionData)
        val answer =
            withCol { media.escapeMediaFilenames(card.answer(this)) }

        eval.emit("_showQuestion(${Json.encodeToString(question)}, ${Json.encodeToString(answer)}, '${bodyClass()}');")
    }

    /**
     * Parses the card answer and sends a [eval] request to load it into the `qa` HTML div
     *
     * * [Anki reference](https://github.com/ankitects/anki/blob/c985acb9fe36d3651eb83cf4cfe44d046ec7458f/qt/aqt/reviewer.py#L460)
     * * [Typescript reference](https://github.com/ankitects/anki/blob/c985acb9fe36d3651eb83cf4cfe44d046ec7458f/ts/reviewer/index.ts#L193)
     *
     * @see [stdHtml]
     */
    protected open suspend fun showAnswer() {
        Timber.v("showAnswer()")
        showingAnswer.emit(true)

        val card = currentCard.await()
        val answerData = withCol { card.answer(this) }
        val answer = mungeQA(answerData)

        eval.emit("_showAnswer(${Json.encodeToString(answer)}, '${bodyClass()}');")
    }

    private fun createSoundErrorListener(): MediaErrorListener {
        return object : MediaErrorListener {
            override fun onError(uri: Uri): MediaErrorBehavior {
                if (uri.scheme != "file") {
                    return MediaErrorBehavior.CONTINUE_MEDIA
                }

                val file = uri.toFile()
                // There is a multitude of transient issues with the MediaPlayer.
                // Retrying fixes most of these
                if (file.exists()) return MediaErrorBehavior.RETRY_MEDIA
                mediaErrorHandler.processMissingMedia(file) { fileName ->
                    viewModelScope.launch { onMediaError.emit(fileName) }
                }
                return MediaErrorBehavior.CONTINUE_MEDIA
            }

            override fun onMediaPlayerError(
                mp: MediaPlayer?,
                which: Int,
                extra: Int,
                uri: Uri,
            ): MediaErrorBehavior {
                Timber.w("Media Error: (%d, %d)", which, extra)
                return onError(uri)
            }

            override fun onTtsError(
                error: TtsPlayer.TtsError,
                isAutomaticPlayback: Boolean,
            ) {
                mediaErrorHandler.processTtsFailure(error, isAutomaticPlayback) {
                    viewModelScope.launch { onTtsError.emit(error) }
                }
            }
        }
    }

    override suspend fun handlePostRequest(
        uri: String,
        bytes: ByteArray,
    ): ByteArray =
        if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
            when (uri.substring(AnkiServer.ANKI_PREFIX.length)) {
                "i18nResources" -> withCol { i18nResourcesRaw(bytes) }
                else -> throw IllegalArgumentException("Unhandled Anki request: $uri")
            }
        } else {
            throw IllegalArgumentException("Unhandled POST request: $uri")
        }
}
