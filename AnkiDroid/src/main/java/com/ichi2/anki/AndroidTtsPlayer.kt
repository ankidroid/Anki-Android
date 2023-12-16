/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.ERROR
import androidx.annotation.CheckResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AndroidTtsError.TtsErrorCode
import com.ichi2.compat.UtteranceProgressListenerCompat
import com.ichi2.libanki.AvTag
import com.ichi2.libanki.TTSTag
import com.ichi2.libanki.TtsPlayer
import com.ichi2.libanki.TtsPlayer.TtsCompletionStatus
import com.ichi2.libanki.TtsVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

class AndroidTtsPlayer(private val context: Context, private val voices: List<TtsVoice>) :
    TtsPlayer(),
    DefaultLifecycleObserver {

    private lateinit var scope: CoroutineScope

    // this can be null in the case that TTS failed to load
    private var tts: TextToSpeech? = null

    /** Flyweight pattern for an empty bundle */
    private val bundleFlyweight = Bundle()

    private val ttsCompletedChannel: Channel<TtsCompletionStatus> = Channel()
    suspend fun init(scope: CoroutineScope) {
        this.scope = scope
        this.tts = TtsVoices.createTts(context)?.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListenerCompat() {
                override fun onStart(utteranceId: String?) { }

                override fun onDone(utteranceId: String?) {
                    scope.launch(Dispatchers.IO) { ttsCompletedChannel.send(TtsCompletionStatus.success()) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    scope.launch(Dispatchers.IO) { ttsCompletedChannel.send(AndroidTtsError.failure(errorCode)) }
                }
            })
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        owner.lifecycleScope.launch { init(this) }
        super.onCreate(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        close()
    }

    override fun get_available_voices(): List<TtsVoice> {
        return this.voices
    }

    override suspend fun play(tag: AvTag): TtsCompletionStatus {
        if (tag !is TTSTag) {
            Timber.w("Expected TTS Tag, got %s", tag)
            return AndroidTtsError.failure(TtsErrorCode.APP_UNEXPECTED_TAG)
        }
        val match = voice_for_tag(tag)
        if (match == null) {
            Timber.w("could not find voice for %s", tag)
            return AndroidTtsError.failure(TtsErrorCode.APP_MISSING_VOICE)
        }

        val voice = match.voice
        if (voice !is AndroidTtsVoice) {
            Timber.w("Invalid voice for %s", tag)
            return AndroidTtsError.failure(TtsErrorCode.APP_INVALID_VOICE)
        }

        return play(tag, voice).also { result ->
            Timber.d("TTS result %s", result)
        }
    }

    private suspend fun play(tag: TTSTag, voice: AndroidTtsVoice): TtsCompletionStatus =
        suspendCancellableCoroutine { continuation ->
            val tts = tts?.also {
                it.voice = voice.voice
                if (it.setSpeechRate(tag.speed) == ERROR) {
                    return@suspendCancellableCoroutine continuation.resume(AndroidTtsError.failure(TtsErrorCode.APP_SPEECH_RATE_FAILED))
                }
                // if it's already playing: stop it
                it.stopPlaying()
            } ?: return@suspendCancellableCoroutine continuation.resume(AndroidTtsError.failure(TtsErrorCode.APP_TTS_INIT_FAILED))

            Timber.d("tts text '%s' to be played for locale (%s)", tag.fieldText, tag.lang)
            tts.speak(tag.fieldText, TextToSpeech.QUEUE_FLUSH, bundleFlyweight, "stringId")

            continuation.invokeOnCancellation {
                Timber.d("stopping tts due to cancellation")
                tts.stopPlaying()
            }

            scope.launch(Dispatchers.IO) {
                Timber.v("awaiting tts completion")
                continuation.resume(
                    ttsCompletedChannel.receive().also {
                        Timber.v("tts completed")
                    }
                )
            }
        }

    companion object {
        private fun TextToSpeech.stopPlaying() {
            if (this.isSpeaking) {
                Timber.d("tts engine appears to be busy... clearing queue")
                this.stop()
            }
        }

        @CheckResult
        suspend fun createInstance(context: Context, scope: CoroutineScope): AndroidTtsPlayer {
            val voices = TtsVoices.allTtsVoices().toList()
            return AndroidTtsPlayer(context, voices).apply {
                init(scope)
                Timber.v("TTS creation: initialized player instance")
            }
        }
    }

    override fun close() {
        Timber.d("Disposing of TTS Engine")
        tts?.stop()
        tts?.shutdown()
    }
}

class AndroidTtsError(@Suppress("unused") val errorCode: TtsErrorCode) : TtsPlayer.TtsError() {
    enum class TtsErrorCode(var code: Int) {
        ERROR(TextToSpeech.ERROR),
        ERROR_SYNTHESIS(TextToSpeech.ERROR_SYNTHESIS),
        ERROR_INVALID_REQUEST(TextToSpeech.ERROR_INVALID_REQUEST),
        ERROR_NETWORK(TextToSpeech.ERROR_NETWORK),
        ERROR_NETWORK_TIMEOUT(TextToSpeech.ERROR_NETWORK_TIMEOUT),
        ERROR_NOT_INSTALLED_YET(TextToSpeech.ERROR_NOT_INSTALLED_YET),
        ERROR_OUTPUT(TextToSpeech.ERROR_OUTPUT),
        ERROR_SERVICE(TextToSpeech.ERROR_SERVICE),
        APP_UNKNOWN(0),
        APP_UNEXPECTED_TAG(1),
        APP_MISSING_VOICE(2),
        APP_INVALID_VOICE(3),
        APP_SPEECH_RATE_FAILED(4),
        APP_TTS_INIT_FAILED(5)
        ;

        /** A string which google will relate to the TTS Engine in most cases */
        val developerString: String
            get() =
                when (this) {
                    ERROR -> "ERROR"
                    ERROR_SYNTHESIS -> "ERROR_SYNTHESIS"
                    ERROR_INVALID_REQUEST -> "ERROR_INVALID_REQUEST"
                    ERROR_NETWORK -> "ERROR_NETWORK"
                    ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                    ERROR_NOT_INSTALLED_YET -> "ERROR_NOT_INSTALLED_YET"
                    ERROR_OUTPUT -> "ERROR_OUTPUT"
                    ERROR_SERVICE -> "ERROR_SERVICE"
                    APP_UNEXPECTED_TAG -> "APP_UNEXPECTED_TAG"
                    APP_MISSING_VOICE -> "APP_MISSING_VOICE"
                    APP_INVALID_VOICE -> "APP_INVALID_VOICE"
                    APP_SPEECH_RATE_FAILED -> "APP_SPEECH_RATE_FAILED"
                    APP_TTS_INIT_FAILED -> "APP_TTS_INIT_FAILED"
                    APP_UNKNOWN -> "APP_UNKNOWN"
                }

        companion object {
            fun fromErrorCode(errorCode: Int): TtsErrorCode =
                entries.firstOrNull { it.code == errorCode } ?: APP_UNKNOWN
        }
    }

    companion object {
        fun failure(errorCode: TtsErrorCode): TtsCompletionStatus =
            TtsCompletionStatus.failure(AndroidTtsError(errorCode))

        fun failure(errorCode: Int): TtsCompletionStatus =
            failure(TtsErrorCode.fromErrorCode(errorCode))
    }
}
