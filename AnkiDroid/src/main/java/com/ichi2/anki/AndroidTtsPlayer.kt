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

import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.ERROR
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.ReadText.errorToDeveloperString
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.UtteranceProgressListenerCompat
import com.ichi2.libanki.AvTag
import com.ichi2.libanki.TTSTag
import com.ichi2.libanki.TtsPlayer
import com.ichi2.libanki.TtsVoice
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

class AndroidTtsPlayer(private val context: AnkiActivity, private val voices: List<TtsVoice>) : TtsPlayer(), DefaultLifecycleObserver {
    private var tts: TextToSpeech? = null
    private var createTtsJob: Job? = null

    /** Flyweight pattern for an empty bundle */
    private val bundleFlyweight = Bundle()

    private val ttsCompletedChannel: Channel<String?> = Channel()
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        createTtsJob = owner.lifecycleScope.launch {
            tts = TtsVoices.createTts(context)?.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListenerCompat() {
                    override fun onStart(utteranceId: String?) {
                    }

                    override fun onDone(utteranceId: String?) {
                        owner.lifecycleScope.launch {
                            ttsCompletedChannel.send(utteranceId)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        // TODO: Copied from ReadText
                        Timber.v("Android TTS failed: %s (%d). Check logcat for error. Indicates a problem with Android TTS engine.", errorToDeveloperString(errorCode), errorCode)
                        val helpUrl = Uri.parse(context.getString(R.string.link_faq_tts))
                        context.mayOpenUrl(helpUrl)
                        // TODO: We can do better in this UI now we have a reason for failure
                        context.showSnackbar(R.string.no_tts_available_message) {
                            setAction(R.string.help) { ReadText.openTtsHelpUrl(helpUrl) }
                        }
                    }
                })
            }
            // optimisation: avoid the overhead of waiting for a completed job
            createTtsJob = null
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        tts?.stop()
        tts?.shutdown()
    }

    override fun get_available_voices(): List<TtsVoice> {
        return this.voices
    }

    override suspend fun play(tag: AvTag) {
        if (tag !is TTSTag) {
            Timber.w("Expected TTS Tag, got %s", tag)
            return
        }
        val match = voice_for_tag(tag)
        if (match == null) {
            Timber.w("could not find voice for %s", tag)
            return
        }

        val voice = match.voice
        if (voice !is AndroidTtsVoice) {
            Timber.w("Invalid voice for %s", tag)
            return
        }

        play(tag, voice)
    }

    private suspend fun play(tag: TTSTag, voice: AndroidTtsVoice) {
        val tts = requireTts().also {
            it.voice = voice.voice
            if (it.setSpeechRate(tag.speed) == ERROR) {
                return
            }
            // if it's already playing: stop it
            it.stopPlaying()
        }

        Timber.d("tts text '%s' to be played for locale (%s)", tag.fieldText, tag.lang)
        tts.speak(tag.fieldText, TextToSpeech.QUEUE_FLUSH, bundleFlyweight, "stringId")
        ttsCompletedChannel.receive()
        Timber.v("tts completed")
    }

    /** Blocks if necessary to provide a TextToSpeech instance */
    private suspend fun requireTts(): TextToSpeech {
        createTtsJob?.join()
        return tts!!
    }

    companion object {
        private fun TextToSpeech.stopPlaying() {
            if (this.isSpeaking) {
                Timber.d("tts engine appears to be busy... clearing queue")
                this.stop()
            }
        }

        suspend fun createInstance(context: AnkiActivity): AndroidTtsPlayer {
            val voices = TtsVoices.allTtsVoices().toList()
            return AndroidTtsPlayer(context, voices)
        }
    }
}
