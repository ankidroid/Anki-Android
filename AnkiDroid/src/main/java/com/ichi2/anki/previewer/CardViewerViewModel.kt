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
import com.ichi2.anki.AnkiDroidJsAPI
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.cardviewer.MediaErrorHandler
import com.ichi2.anki.cardviewer.SoundErrorBehavior
import com.ichi2.anki.cardviewer.SoundErrorListener
import com.ichi2.anki.cardviewer.SoundPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.PostRequestHandler
import com.ichi2.libanki.Card
import com.ichi2.libanki.Sound
import com.ichi2.libanki.TtsPlayer
import com.ichi2.libanki.note
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject
import timber.log.Timber

abstract class CardViewerViewModel : ViewModel(), OnErrorListener, PostRequestHandler {
    override val onError = MutableSharedFlow<String>()
    val onMediaError = MutableSharedFlow<String>()
    val onTtsError = MutableSharedFlow<TtsPlayer.TtsError>()
    val mediaErrorHandler = MediaErrorHandler()

    val eval = MutableSharedFlow<String>()

    val showingAnswer = MutableStateFlow(false)
    protected val soundPlayer = SoundPlayer(createSoundErrorListener())
    protected lateinit var currentCard: Card

    protected abstract val server: AnkiServer

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        soundPlayer.close()
        server.stop()
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

    fun setSoundPlayerEnabled(isEnabled: Boolean) {
        soundPlayer.isEnabled = isEnabled
    }

    fun playSoundFromUrl(url: String) {
        launchCatchingIO {
            Sound.getAvTag(currentCard, url)?.let {
                soundPlayer.playOneSound(it)
            }
        }
    }

    fun baseUrl() = server.baseUrl()

    fun assetLoaderDomain() = server.domain()

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    protected abstract suspend fun typeAnsFilter(text: String): String

    private fun bodyClass(): String = bodyClassForCardOrd(currentCard.ord)

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L358) */
    private suspend fun mungeQA(text: String): String =
        typeAnsFilter(prepareCardTextForDisplay(text))

    private suspend fun prepareCardTextForDisplay(text: String): String {
        return Sound.addPlayButtons(withCol { media.escapeMediaFilenames(text) })
    }

    protected open suspend fun showQuestion() {
        Timber.v("showQuestion()")
        showingAnswer.emit(false)

        val questionData = withCol { currentCard.question(this) }
        val question = mungeQA(questionData)
        val answer =
            withCol { media.escapeMediaFilenames(currentCard.answer(this)) }

        eval.emit("_showQuestion(${Json.encodeToString(question)}, ${Json.encodeToString(answer)}, '${bodyClass()}');")
    }

    protected open suspend fun showAnswer() {
        Timber.v("showAnswer()")
        showingAnswer.emit(true)
        val answerData = withCol { currentCard.answer(this) }
        val answer = mungeQA(answerData)
        eval.emit("_showAnswer(${Json.encodeToString(answer)}, '${bodyClass()}');")
    }

    private fun createSoundErrorListener(): SoundErrorListener {
        return object : SoundErrorListener {
            override fun onError(uri: Uri): SoundErrorBehavior {
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
                uri: Uri
            ): SoundErrorBehavior {
                Timber.w("Media Error: (%d, %d)", which, extra)
                return onError(uri)
            }

            override fun onTtsError(error: TtsPlayer.TtsError, isAutomaticPlayback: Boolean) {
                mediaErrorHandler.processTtsFailure(error, isAutomaticPlayback) {
                    viewModelScope.launch { onTtsError.emit(error) }
                }
            }
        }
    }

    override suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray? {
        return if (uri.startsWith(AnkiServer.ANKIDROID_JS_PREFIX)) {
            AnkiDroidJsAPI.ApiResult(true, "-1").toString().toByteArray()
        } else {
            Timber.d("Unhandled post request %s", uri)
            null
        }
    }

    companion object {
        /* ********************************** Type-in answer ************************************ */
        /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L669] */
        @VisibleForTesting
        val typeAnsRe = Regex("\\[\\[type:(.+?)]]")

        suspend fun getTypeAnswerField(card: Card, text: String): JSONObject? {
            val match = typeAnsRe.find(text) ?: return null

            val typeAnsFieldName = match.groups[1]!!.value.let {
                if (it.startsWith("cloze:")) {
                    it.split(":")[1]
                } else {
                    it
                }
            }

            val fields = withCol { card.model(this).flds }
            for (i in 0 until fields.length()) {
                val field = fields.get(i) as JSONObject
                if (field.getString("name") == typeAnsFieldName) {
                    return field
                }
            }
            return null
        }

        suspend fun getExpectedTypeInAnswer(card: Card, field: JSONObject): String? {
            val fieldName = field.getString("name")
            val expected = withCol { card.note().getItem(fieldName) }
            return if (fieldName.startsWith("cloze:")) {
                val clozeIdx = card.ord + 1
                withCol {
                    extractClozeForTyping(expected, clozeIdx).takeIf { it.isNotBlank() }
                }
            } else {
                expected
            }
        }

        fun getFontSize(field: JSONObject): String = field.getString("size")
    }
}
