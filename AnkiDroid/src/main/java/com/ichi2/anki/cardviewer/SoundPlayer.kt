/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.cardviewer

import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.net.toFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.AndroidTtsPlayer
import com.ichi2.anki.cardviewer.SoundErrorBehavior.CONTINUE_AUDIO
import com.ichi2.anki.cardviewer.SoundErrorBehavior.RETRY_AUDIO
import com.ichi2.anki.cardviewer.SoundErrorBehavior.STOP_AUDIO
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.AvTag
import com.ichi2.libanki.Card
import com.ichi2.libanki.Sound.SoundSide
import com.ichi2.libanki.Sound.SoundSide.ANSWER
import com.ichi2.libanki.Sound.SoundSide.QUESTION
import com.ichi2.libanki.Sound.SoundSide.QUESTION_AND_ANSWER
import com.ichi2.libanki.SoundOrVideoTag
import com.ichi2.libanki.TTSTag
import com.ichi2.libanki.TtsPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.Closeable
import java.io.File

/**
 * Handles the two ways an Anki card defines sound:
 * * Regular Sound (file-based, mp3 etc..): [SoundOrVideoTag]
 *   *  No docs for [sound:], but this handles Sound or Video with a reference to the file
 *   * `[sound:audio.mp3]` in a field
 *  * in the media directory.
 * * Text to Speech [TTSTag]
 *   * [docs][https://docs.ankiweb.net/templates/fields.html?highlight=tts#text-to-speech]
 *   * `{{tts en_GB:Front}}` on the card template
 *
 * This class combines the above concerns behind an "adapter" interface in order to simplify complexity.
 *
 * **Public interface**
 * * [playAllSounds]
 * * [replayAllSounds]
 * * [playOneSound]
 * * [stopSounds]
 * * [loadCardSounds] - informs the class of whether we're on the front/back of a card
 *
 * @see AvTag
 *
 * @param onSoundGroupCompleted Function to be called when [playAllSounds] or [replayAllSounds] completes
 *
 * **Out of scope**
 * [com.ichi2.anki.ReadText]: AnkiDroid has a legacy "tts" setting, before Anki Desktop TTS.
 * This uses [com.ichi2.anki.MetaDB], and may either read `<tts>` or all text on a card
 *
 */
class SoundPlayer(
    private val soundTagPlayer: SoundTagPlayer,
    private val ttsPlayer: Deferred<TtsPlayer>,
    private val lifecycle: Lifecycle,
    private val onSoundGroupCompleted: () -> Unit,
    private val soundErrorListener: SoundErrorListener
) : DefaultLifecycleObserver, Closeable {

    private lateinit var questions: List<AvTag>
    private lateinit var answers: List<AvTag>
    private lateinit var side: Side

    lateinit var config: CardSoundConfig

    private var playSoundsJob: Job? = null

    val scope get() = lifecycle.coroutineScope

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        scope.launch { stopSounds() }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        close()
    }

    suspend fun loadCardSounds(card: Card, side: Side) {
        Timber.i("loading sounds for card %s (%s)", card.id, side)
        stopSounds()
        this.questions = card.renderOutput().questionAvTags
        this.answers = card.renderOutput().answerAvTags
        this.side = side

        if (!this::config.isInitialized || !config.appliesTo(card)) {
            config = CardSoundConfig.create(card)
        }
    }

    private suspend fun playAllSoundsForSide(soundSide: SoundSide) {
        if (!canPlaySounds()) {
            return
        }
        cancelPlaySoundsJob()
        Timber.i("playing sounds for %s", soundSide)
        this.playSoundsJob = scope.launch(Dispatchers.IO) {
            playAllSoundsInternal(soundSide)
            playSoundsJob = null
        }
    }

    suspend fun playOneSound(tag: AvTag) {
        if (!canPlaySounds()) {
            return
        }
        cancelPlaySoundsJob()
        Timber.i("playing one sound")

        suspend fun retry() {
            try {
                play(tag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "failed to replay audio")
            }
        }

        playSoundsJob = scope.launch(Dispatchers.IO) {
            try {
                play(tag)
            } catch (e: SoundException) {
                when (e.continuationBehavior) {
                    RETRY_AUDIO -> retry()
                    CONTINUE_AUDIO, STOP_AUDIO -> { }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Exception playing sound")
            }
            Timber.v("completed playing one sound")
            playSoundsJob = null
        }
    }

    suspend fun stopSounds() {
        Timber.i("stopping sounds")
        cancelPlaySoundsJob()
    }

    override fun close() {
        soundTagPlayer.releaseSound()
        try {
            ttsPlayer.getCompleted().close()
        } catch (e: Exception) {
            Timber.i(e, "ttsPlayer close()")
        }
    }

    private suspend fun cancelPlaySoundsJob() {
        playSoundsJob?.let {
            Timber.i("cancelling job")
            withContext(Dispatchers.IO) {
                playSoundsJob?.cancelAndJoin()
            }
        }
    }

    /**
     * Obtains all the sounds for the [soundSide] and plays them sequentially
     */
    private suspend fun playAllSoundsInternal(soundSide: SoundSide) {
        if (!canPlaySounds()) {
            return
        }
        val soundList = when (soundSide) {
            QUESTION -> questions
            ANSWER -> answers
            QUESTION_AND_ANSWER -> questions + answers
        }

        try {
            for ((index, sound) in soundList.withIndex()) {
                Timber.d("playing sound %d/%d", index + 1, soundList.size)
                if (!play(sound)) {
                    Timber.d("stopping sound playback early")
                    return
                }
            }
        } finally {
            // call the completion listener, even if a CancellationException was thrown
            onSoundGroupCompleted()
        }
    }

    /**
     * Plays the provided [tag] and returns whether playback should continue
     * @return whether playback should continue: `true`: continue, `false`: stop playback
     */
    private suspend fun play(tag: AvTag): Boolean = withContext(Dispatchers.IO) {
        suspend fun play() {
            ensureActive()
            when (tag) {
                is SoundOrVideoTag -> soundTagPlayer.play(tag, soundErrorListener)
                is TTSTag -> {
                    awaitTtsPlayer()?.play(tag)
                }
                else -> Timber.w("unknown audio: ${tag.javaClass}")
            }
            ensureActive()
        }

        try {
            play()
        } catch (e: SoundException) {
            when (e.continuationBehavior) {
                STOP_AUDIO -> return@withContext false
                CONTINUE_AUDIO -> return@withContext true
                RETRY_AUDIO -> {
                    try {
                        play()
                    } catch (e: Exception) {
                        Timber.w("failed to replay audio", e)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Unexpected audio exception. Continuing", e)
        }
        return@withContext true
    }

    fun hasSounds(displayAnswer: Boolean): Boolean =
        if (displayAnswer) answers.any() else questions.any()

    /**
     * Plays all sounds for the current side, calling [onSoundGroupCompleted] when completed
     */
    suspend fun playAllSounds() = when (side) {
        Side.FRONT -> playAllSoundsForSide(QUESTION)
        Side.BACK -> playAllSoundsForSide(ANSWER)
    }

    /**
     * Replays all sounds for the current side, calling [onSoundGroupCompleted] when completed
     */
    suspend fun replayAllSounds() = when (side) {
        Side.BACK -> if (config.replayQuestion) playAllSoundsForSide(QUESTION_AND_ANSWER) else playAllSoundsForSide(ANSWER)
        Side.FRONT -> playAllSoundsForSide(QUESTION)
    }

    private suspend fun awaitTtsPlayer(): TtsPlayer? {
        return withTimeoutOrNull(TTS_PLAYER_TIMEOUT_MS) {
            ttsPlayer.await()
        }
    }

    private fun canPlaySounds(): Boolean {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Timber.w("sounds are not played as the activity is inactive")
            return false
        }
        return true
    }
    companion object {
        const val TTS_PLAYER_TIMEOUT_MS = 2_500L

        /**
         * @param soundUriBase The base path to the sound directory as a `file://` URI
         */
        @NeedsTest("ensure the lifecycle is subscribed to in a Reviewer")
        fun newInstance(viewer: AbstractFlashcardViewer, soundUriBase: String): SoundPlayer {
            val scope = viewer.lifecycleScope
            val soundErrorListener = viewer.createSoundErrorListener(soundUriBase)
            // tts can take a long time to init, this defers the operation until it's needed
            val tts = scope.async(Dispatchers.IO) { AndroidTtsPlayer.createInstance(viewer, viewer.lifecycleScope) }

            val soundPlayer = SoundTagPlayer(soundUriBase)

            return SoundPlayer(
                soundTagPlayer = soundPlayer,
                ttsPlayer = tts,
                lifecycle = viewer.lifecycle,
                onSoundGroupCompleted = viewer::onSoundGroupCompleted,
                soundErrorListener = soundErrorListener
            ).apply {
                viewer.lifecycle.addObserver(this)
            }
        }
    }
}

interface SoundErrorListener {
    @CheckResult
    fun onError(uri: Uri): SoundErrorBehavior

    @CheckResult
    fun onMediaPlayerError(mp: MediaPlayer?, which: Int, extra: Int, tag: SoundOrVideoTag): SoundErrorBehavior
}

enum class SoundErrorBehavior {
    /** Stop playing audio */
    STOP_AUDIO,

    /** Continue to the next audio (if any) */
    CONTINUE_AUDIO,

    /** Retry the current audio */
    RETRY_AUDIO
}

fun AbstractFlashcardViewer.createSoundErrorListener(baseUri: String): SoundErrorListener {
    return object : SoundErrorListener {
        private var handledError: HashSet<String> = hashSetOf()

        private fun AbstractFlashcardViewer.handleStorageMigrationError(file: File): Boolean {
            val migrationService = migrationService ?: return false
            if (handledError.contains(file.absolutePath)) {
                return false
            }
            handledError.add(file.absolutePath)
            return migrationService.migrateFileImmediately(file)
        }

        override fun onMediaPlayerError(
            mp: MediaPlayer?,
            which: Int,
            extra: Int,
            tag: SoundOrVideoTag
        ): SoundErrorBehavior {
            Timber.w("Media Error: (%d, %d)", which, extra)
            val uri = try {
                Uri.parse(baseUri + tag.filename)
            } catch (e: Exception) {
                Timber.w(e)
                return CONTINUE_AUDIO
            }
            return onError(uri)
        }

        override fun onError(uri: Uri): SoundErrorBehavior {
            try {
                val file = uri.toFile()
                if (file.exists()) return CONTINUE_AUDIO
                // file doesn't exist - may be due to scoped storage
                if (handleStorageMigrationError(file)) {
                    return RETRY_AUDIO
                }
                // just doesn't exist - process the error
                AbstractFlashcardViewer.mMissingImageHandler.processMissingSound(file) { filename: String? -> displayCouldNotFindMediaSnackbar(filename) }
                return CONTINUE_AUDIO
            } catch (e: Exception) {
                Timber.w(e)
                return CONTINUE_AUDIO
            }
        }
    }
}

/** An exception thrown when playing a sound, and how to continue playing sounds */
class SoundException : Exception {
    val continuationBehavior: SoundErrorBehavior
    constructor(errorHandling: SoundErrorBehavior) : super() {
        this.continuationBehavior = errorHandling
    }
    constructor(errorHandling: SoundErrorBehavior, exception: Exception) : super(exception) {
        this.continuationBehavior = errorHandling
    }
}
