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
import com.ichi2.anki.cardviewer.JavascriptEvaluator
import com.ichi2.anki.cardviewer.MediaErrorHandler
import com.ichi2.anki.cardviewer.SoundErrorBehavior
import com.ichi2.anki.cardviewer.SoundErrorListener
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.PostRequestHandler
import com.ichi2.libanki.Card
import com.ichi2.libanki.Sound
import com.ichi2.libanki.TtsPlayer
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

abstract class CardViewerViewModel(
    cardMediaPlayer: CardMediaPlayer,
) : ViewModel(),
    OnErrorListener,
    PostRequestHandler {
    override val onError = MutableSharedFlow<String>()
    val onMediaError = MutableSharedFlow<String>()
    val onTtsError = MutableSharedFlow<TtsPlayer.TtsError>()
    val mediaErrorHandler = MediaErrorHandler()

    val eval = MutableSharedFlow<String>()

    val showingAnswer = MutableStateFlow(false)

    protected val cardMediaPlayer =
        cardMediaPlayer.apply {
            setSoundErrorListener(createSoundErrorListener())
            javascriptEvaluator = { JavascriptEvaluator { launchCatchingIO { eval.emit(it) } } }
        }
    abstract var currentCard: Deferred<Card>

    abstract val server: AnkiServer

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        cardMediaPlayer.close()
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
        cardMediaPlayer.isEnabled = isEnabled
    }

    fun playSoundFromUrl(url: String) {
        launchCatchingIO {
            Sound.getAvTag(currentCard.await(), url)?.let {
                cardMediaPlayer.playOneSound(it)
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

    protected abstract suspend fun typeAnsFilter(
        text: String,
        typedAnswer: String? = null,
    ): String

    private suspend fun bodyClass(): String = bodyClassForCardOrd(currentCard.await().ord)

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L358) */
    private suspend fun mungeQA(
        text: String,
        typedAnswer: String? = null,
    ): String = typeAnsFilter(prepareCardTextForDisplay(text), typedAnswer)

    private suspend fun prepareCardTextForDisplay(text: String): String =
        Sound.addPlayButtons(
            text = withCol { media.escapeMediaFilenames(text) },
            renderOutput = currentCard.await().let { card -> withCol { card.renderOutput(this) } },
        )

    protected open suspend fun showQuestion() {
        Timber.v("showQuestion()")
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
    protected open suspend fun showAnswer(typedAnswer: String? = null) {
        Timber.v("showAnswer()")
        showingAnswer.emit(true)

        val card = currentCard.await()
        val answerData = withCol { card.answer(this) }
        val answer = mungeQA(answerData, typedAnswer)

        eval.emit("_showAnswer(${Json.encodeToString(answer)}, '${bodyClass()}');")
    }

    private fun createSoundErrorListener(): SoundErrorListener {
        return object : SoundErrorListener {
            override fun onError(uri: Uri): SoundErrorBehavior {
                if (uri.scheme != "file") {
                    return SoundErrorBehavior.CONTINUE_AUDIO
                }

                val file = uri.toFile()
                // There is a multitude of transient issues with the MediaPlayer.
                // Retrying fixes most of these
                if (file.exists()) return SoundErrorBehavior.RETRY_AUDIO
                mediaErrorHandler.processMissingSound(file) { fileName ->
                    viewModelScope.launch { onMediaError.emit(fileName) }
                }
                return SoundErrorBehavior.CONTINUE_AUDIO
            }

            override fun onMediaPlayerError(
                mp: MediaPlayer?,
                which: Int,
                extra: Int,
                uri: Uri,
            ): SoundErrorBehavior {
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
