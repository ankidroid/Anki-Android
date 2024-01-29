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
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.AbstractFlashcardViewer.Companion.getMediaBaseUrl
import com.ichi2.anki.AndroidTtsError
import com.ichi2.anki.AndroidTtsError.TtsErrorCode
import com.ichi2.anki.AndroidTtsPlayer
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper.Companion.getMediaDirectory
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cardviewer.SoundErrorBehavior.CONTINUE_AUDIO
import com.ichi2.anki.cardviewer.SoundErrorBehavior.RETRY_AUDIO
import com.ichi2.anki.cardviewer.SoundErrorBehavior.STOP_AUDIO
import com.ichi2.anki.localizedErrorMessage
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.AvTag
import com.ichi2.libanki.Card
import com.ichi2.libanki.SoundOrVideoTag
import com.ichi2.libanki.TTSTag
import com.ichi2.libanki.TtsPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
 * [setOnSoundGroupCompletedListener] can be used to call
 * something when [playAllSounds] or [replayAllSounds] completes
 *
 * **Out of scope**
 * [com.ichi2.anki.ReadText]: AnkiDroid has a legacy "tts" setting, before Anki Desktop TTS.
 * This uses [com.ichi2.anki.MetaDB], and may either read `<tts>` or all text on a card
 *
 */
class SoundPlayer : Closeable {

    private val soundTagPlayer: SoundTagPlayer
    private val ttsPlayer: Deferred<TtsPlayer>
    private val soundErrorListener: SoundErrorListener

    constructor(soundTagPlayer: SoundTagPlayer, ttsPlayer: Deferred<TtsPlayer>, soundErrorListener: SoundErrorListener) {
        this.soundTagPlayer = soundTagPlayer
        this.ttsPlayer = ttsPlayer
        this.soundErrorListener = soundErrorListener
    }

    constructor(soundErrorListener: SoundErrorListener) {
        this.soundTagPlayer = SoundTagPlayer(getMediaBaseUrl(getMediaDirectory(AnkiDroidApp.instance).path))
        this.ttsPlayer = scope.async { AndroidTtsPlayer.createInstance(AnkiDroidApp.instance, scope) }
        this.soundErrorListener = soundErrorListener
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var questions: List<AvTag>
    private lateinit var answers: List<AvTag>

    lateinit var config: CardSoundConfig
    var isEnabled = true
        set(value) {
            if (!value) {
                scope.launch { stopSounds() }
            }
            field = value
        }

    private var playSoundsJob: Job? = null

    private var onSoundGroupCompleted: (() -> Unit)? = null

    fun setOnSoundGroupCompletedListener(listener: (() -> Unit)?) {
        onSoundGroupCompleted = listener
    }

    suspend fun loadCardSounds(card: Card) {
        Timber.i("loading sounds for card %d", card.id)
        stopSounds()
        this.questions = withCol { card.renderOutput(this).questionAvTags }
        this.answers = withCol { card.renderOutput(this).answerAvTags }

        if (!this::config.isInitialized || !config.appliesTo(card)) {
            config = withCol { CardSoundConfig.create(card) }
        }
    }

    private suspend fun playAllSoundsForSide(cardSide: CardSide): Job? {
        if (!isEnabled) return null
        playSoundsJob {
            Timber.i("playing sounds for %s", cardSide)
            playAllSoundsInternal(cardSide, isAutomaticPlayback = true)
        }
        return this.playSoundsJob
    }

    suspend fun playOneSound(tag: AvTag): Job? {
        if (!isEnabled) return null
        cancelPlaySoundsJob()
        Timber.i("playing one sound")

        suspend fun play(tag: AvTag) = play(tag, isAutomaticPlayback = false)

        suspend fun retry() {
            try {
                play(tag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "failed to replay audio")
            }
        }

        playSoundsJob = scope.launch {
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
        return playSoundsJob
    }

    suspend fun stopSounds() {
        if (playSoundsJob != null) Timber.i("stopping sounds")
        cancelPlaySoundsJob(playSoundsJob)
    }

    override fun close() {
        soundTagPlayer.releaseSound()
        try {
            ttsPlayer.getCompleted().close()
        } catch (e: Exception) {
            Timber.i(e, "ttsPlayer close()")
        }
        scope.cancel()
    }

    private suspend fun cancelPlaySoundsJob(job: Job? = playSoundsJob) {
        if (job == null) return
        Timber.i("cancelling job")
        withContext(Dispatchers.IO) {
            job.cancelAndJoin()
        }
        // This stops multiple calls logging, while allowing an 'old' value in as the parameter
        if (job == playSoundsJob) {
            playSoundsJob = null
        }
    }

    /**
     * Obtains all the sounds for the [cardSide] and plays them sequentially
     */
    private suspend fun playAllSoundsInternal(cardSide: CardSide, isAutomaticPlayback: Boolean) {
        if (!isEnabled) return
        val soundList = when (cardSide) {
            CardSide.QUESTION -> questions
            CardSide.ANSWER -> answers
            CardSide.BOTH -> questions + answers
        }

        try {
            for ((index, sound) in soundList.withIndex()) {
                Timber.d("playing sound %d/%d", index + 1, soundList.size)
                if (!play(sound, isAutomaticPlayback)) {
                    Timber.d("stopping sound playback early")
                    return
                }
            }
        } finally {
            // call the completion listener, even if a CancellationException was thrown
            onSoundGroupCompleted?.invoke()
        }
    }

    /**
     * Plays the provided [tag] and returns whether playback should continue
     * @return whether playback should continue: `true`: continue, `false`: stop playback
     */
    private suspend fun play(tag: AvTag, isAutomaticPlayback: Boolean): Boolean = withContext(Dispatchers.IO) {
        suspend fun play() {
            ensureActive()
            when (tag) {
                is SoundOrVideoTag -> soundTagPlayer.play(tag, soundErrorListener)
                is TTSTag -> {
                    awaitTtsPlayer(isAutomaticPlayback)?.play(tag)?.error?.let {
                        soundErrorListener.onTtsError(it, isAutomaticPlayback)
                    }
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
                        Timber.i("retrying audio")
                        play()
                        Timber.i("retry succeeded")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w("retry audio failed", e)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unexpected audio exception. Continuing")
        }
        return@withContext true
    }

    fun hasSounds(displayAnswer: Boolean): Boolean =
        if (displayAnswer) answers.any() else questions.any()

    /**
     * Plays all sounds for the current side, calling [onSoundGroupCompleted] when completed
     */
    suspend fun playAllSounds(side: SingleCardSide) = when (side) {
        SingleCardSide.FRONT -> playAllSoundsForSide(CardSide.QUESTION)
        SingleCardSide.BACK -> playAllSoundsForSide(CardSide.ANSWER)
    }

    /**
     * Replays all sounds for the current side, calling [onSoundGroupCompleted] when completed
     */
    suspend fun replayAllSounds(side: SingleCardSide) = when (side) {
        SingleCardSide.BACK -> if (config.replayQuestion) playAllSoundsForSide(CardSide.BOTH) else playAllSoundsForSide(CardSide.ANSWER)
        SingleCardSide.FRONT -> playAllSoundsForSide(CardSide.QUESTION)
    }

    private suspend fun awaitTtsPlayer(isAutomaticPlayback: Boolean): TtsPlayer? {
        val player = withTimeoutOrNull(TTS_PLAYER_TIMEOUT_MS) {
            ttsPlayer.await()
        }
        if (player == null) {
            Timber.v("timeout waiting for TTS Player")
            val error = AndroidTtsError(TtsErrorCode.APP_TTS_INIT_TIMEOUT)
            soundErrorListener.onTtsError(error, isAutomaticPlayback)
        }
        return player
    }

    /** Ensures that only one [playSoundsJob] is running at once */
    private suspend fun playSoundsJob(block: suspend CoroutineScope.() -> Unit) {
        val oldJob = playSoundsJob
        this.playSoundsJob = scope.launch {
            cancelPlaySoundsJob(oldJob)
            block()
            playSoundsJob = null
        }
    }

    companion object {
        const val TTS_PLAYER_TIMEOUT_MS = 2_500L

        /**
         * @param soundUriBase The base path to the sound directory as a `file://` URI
         */
        @NeedsTest("ensure the lifecycle is subscribed to in a Reviewer")
        fun newInstance(viewer: AbstractFlashcardViewer, soundUriBase: String): SoundPlayer {
            val scope = viewer.lifecycleScope
            val soundErrorListener = viewer.createSoundErrorListener()
            // tts can take a long time to init, this defers the operation until it's needed
            val tts = scope.async(Dispatchers.IO) { AndroidTtsPlayer.createInstance(viewer, viewer.lifecycleScope) }

            val soundPlayer = SoundTagPlayer(soundUriBase)

            return SoundPlayer(
                soundTagPlayer = soundPlayer,
                ttsPlayer = tts,
                soundErrorListener = soundErrorListener
            ).apply {
                setOnSoundGroupCompletedListener(viewer::onSoundGroupCompleted)
            }
        }
    }
}

interface SoundErrorListener {
    @CheckResult
    fun onError(uri: Uri): SoundErrorBehavior

    @CheckResult
    fun onMediaPlayerError(mp: MediaPlayer?, which: Int, extra: Int, uri: Uri): SoundErrorBehavior
    fun onTtsError(error: TtsPlayer.TtsError, isAutomaticPlayback: Boolean)
}

enum class SoundErrorBehavior {
    /** Stop playing audio */
    STOP_AUDIO,

    /** Continue to the next audio (if any) */
    CONTINUE_AUDIO,

    /** Retry the current audio */
    RETRY_AUDIO
}

fun AbstractFlashcardViewer.createSoundErrorListener(): SoundErrorListener {
    val activity = this
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
            uri: Uri
        ): SoundErrorBehavior {
            Timber.w("Media Error: (%d, %d)", which, extra)
            return onError(uri)
        }

        override fun onTtsError(error: TtsPlayer.TtsError, isAutomaticPlayback: Boolean) {
            AbstractFlashcardViewer.mediaErrorHandler.processTtsFailure(error, isAutomaticPlayback) {
                activity.showSnackbar(error.localizedErrorMessage(activity))
            }
        }

        override fun onError(uri: Uri): SoundErrorBehavior {
            try {
                val file = uri.toFile()
                // There is a multitude of transient issues with the MediaPlayer. (1, -1001) for example
                // Retrying fixes most of these
                if (file.exists()) return RETRY_AUDIO
                // file doesn't exist - may be due to scoped storage
                if (handleStorageMigrationError(file)) {
                    return RETRY_AUDIO
                }
                // just doesn't exist - process the error
                AbstractFlashcardViewer.mediaErrorHandler.processMissingSound(file) { filename: String? -> displayCouldNotFindMediaSnackbar(filename) }
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
