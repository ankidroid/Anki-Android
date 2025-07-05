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
import com.ichi2.anki.AndroidTtsPlayer
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper.getMediaDirectory
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.ReadText
import com.ichi2.anki.cardviewer.MediaErrorBehavior.CONTINUE_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.RETRY_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.STOP_MEDIA
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.dialogs.TtsPlaybackErrorDialog
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.localizedErrorMessage
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.snackbar.showSnackbar
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

/**
 * Handles the two ways an Anki card defines sound:
 * * Regular Sound (file-based, mp3 etc..): [SoundOrVideoTag]
 *   *  No docs for [sound:], but this handles Sound or Video with a reference to the file
 *   * `[sound:audio.mp3]` in a field
 *   * `[sound:video.mp4]` in a field
 *  * in the media directory.
 * * Text to Speech [TTSTag]
 *   * [docs][https://docs.ankiweb.net/templates/fields.html?highlight=tts#text-to-speech]
 *   * `{{tts en_GB:Front}}` on the card template
 *
 * This class combines the above concerns behind an "adapter" interface in order to simplify complexity.
 *
 * **Public interface**
 * * [playAll]
 * * [replayAll]
 * * [playOne]
 * * [stop]
 * * [loadCardAvTags] - informs the class of whether we're on the front/back of a card
 *
 * @see AvTag
 *
 * [setOnMediaGroupCompletedListener] can be used to call
 * something when [playAll] or [replayAll] completes
 *
 * **Out of scope**
 * [com.ichi2.anki.ReadText]: AnkiDroid has a legacy "tts" setting, before Anki Desktop TTS.
 * This uses [com.ichi2.anki.MetaDB], and may either read `<tts>` or all text on a card
 *
 */
@NeedsTest("Integration test: A video is autoplayed if it's the first media on a card")
@NeedsTest("A sound is played after a video finishes")
@NeedsTest("Pausing a video calls onMediaGroupCompleted")
class CardMediaPlayer : Closeable {
    private val soundTagPlayer: SoundTagPlayer
    private val ttsPlayer: Deferred<TtsPlayer>
    private var mediaErrorListener: MediaErrorListener? = null
    var javascriptEvaluator: () -> JavascriptEvaluator? = { null }

    constructor(soundTagPlayer: SoundTagPlayer, ttsPlayer: Deferred<TtsPlayer>, mediaErrorListener: MediaErrorListener) {
        this.soundTagPlayer = soundTagPlayer
        this.ttsPlayer = ttsPlayer
        this.mediaErrorListener = mediaErrorListener
    }

    constructor() {
        // javascriptEvaluator is wrapped in a lambda so the value in this class propagates down
        this.soundTagPlayer =
            SoundTagPlayer(
                soundUriBase = getMediaBaseUrl(getMediaDirectory(AnkiDroidApp.instance)),
                videoPlayer = VideoPlayer { javascriptEvaluator() },
            )
        this.ttsPlayer = scope.async { AndroidTtsPlayer.createInstance(scope) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var questionAvTags: List<AvTag>
    private lateinit var answerAvTags: List<AvTag>

    lateinit var config: CardSoundConfig
    var isEnabled = true
        set(value) {
            if (!value) {
                scope.launch { stop() }
            }
            field = value
        }

    private var playAvTagsJob: Job? = null
    val isPlaying get() = playAvTagsJob != null

    private var onMediaGroupCompleted: (() -> Unit)? = null

    fun setOnMediaGroupCompletedListener(listener: (() -> Unit)?) {
        onMediaGroupCompleted = listener
    }

    fun setMediaErrorListener(mediaErrorListener: MediaErrorListener) {
        this.mediaErrorListener = mediaErrorListener
    }

    suspend fun loadCardAvTags(card: Card) {
        Timber.i("loading av tags for card %d", card.id)
        stop()
        val renderOutput = withCol { card.renderOutput(this) }
        val autoPlay = withCol { card.autoplay(this) }
        this.questionAvTags = renderOutput.questionAvTags
        this.answerAvTags = renderOutput.answerAvTags

        if (!this::config.isInitialized || !config.appliesTo(card) || (this::config.isInitialized && autoPlay != config.autoplay)) {
            config = withCol { CardSoundConfig.create(this@withCol, card) }
        }
    }

    /**
     * Ensures that [questionAvTags] and [answerAvTags] are loaded
     *
     * Does not affect playback if they are
     */
    suspend fun ensureAvTagsLoaded(card: Card) {
        if (this::questionAvTags.isInitialized) return

        Timber.i("loading sounds for card %d", card.id)
        val renderOutput = withCol { card.renderOutput(this) }
        this.questionAvTags = renderOutput.questionAvTags
        this.answerAvTags = renderOutput.answerAvTags

        if (!this::config.isInitialized || !config.appliesTo(card)) {
            config = withCol { CardSoundConfig.create(this@withCol, card) }
        }
    }

    fun autoplayAllForSide(cardSide: CardSide): Job? {
        if (config.autoplay) {
            return playAllForSide(cardSide)
        }
        return null
    }

    fun playAllForSide(cardSide: CardSide): Job? {
        if (!isEnabled) return null
        playAvTagsJob {
            Timber.i("playing sounds for %s", cardSide)
            playAllAvTagsInternal(cardSide, isAutomaticPlayback = true)
        }
        return this.playAvTagsJob
    }

    suspend fun playOne(tag: AvTag): Job? {
        if (!isEnabled) return null
        cancelPlayAvTagsJob()
        Timber.i("playing one AV Tag")

        suspend fun play(tag: AvTag) = play(tag, isAutomaticPlayback = false)

        suspend fun retry() {
            try {
                play(tag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "failed to replay media")
            }
        }

        playAvTagsJob =
            scope.launch {
                try {
                    play(tag)
                } catch (e: MediaException) {
                    when (e.continuationBehavior) {
                        RETRY_MEDIA -> retry()
                        CONTINUE_MEDIA, STOP_MEDIA -> { }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Exception playing AV Tag")
                }
                Timber.v("completed playing one AV Tag")
                playAvTagsJob = null
            }
        return playAvTagsJob
    }

    suspend fun stop() {
        if (isPlaying) Timber.i("stopping playing all AV tags")
        cancelPlayAvTagsJob(playAvTagsJob)
        ReadText.stopTts() // TODO: Reconsider design
    }

    override fun close() {
        soundTagPlayer.release()
        try {
            ttsPlayer.getCompleted().close()
        } catch (e: Exception) {
            Timber.i(e, "ttsPlayer close()")
        }
        scope.cancel()
    }

    private suspend fun cancelPlayAvTagsJob(job: Job? = playAvTagsJob) {
        if (job == null) return
        Timber.i("cancelling job")
        withContext(Dispatchers.IO) {
            job.cancelAndJoin()
        }
        // This stops multiple calls logging, while allowing an 'old' value in as the parameter
        if (job == playAvTagsJob) {
            playAvTagsJob = null
        }
    }

    /**
     * Obtains all the [AvTag]s for the [cardSide] and plays them sequentially
     */
    private suspend fun playAllAvTagsInternal(
        cardSide: CardSide,
        isAutomaticPlayback: Boolean,
    ) {
        if (!isEnabled) return
        val avTagList =
            when (cardSide) {
                CardSide.QUESTION -> questionAvTags
                CardSide.ANSWER -> answerAvTags
                CardSide.BOTH -> questionAvTags + answerAvTags
            }

        try {
            for ((index, avTag) in avTagList.withIndex()) {
                Timber.d("playing AV Tag %d/%d", index + 1, avTagList.size)
                if (!play(avTag, isAutomaticPlayback)) {
                    Timber.d("stopping AV Tag playback early")
                    return
                }
            }
        } finally {
            // call the completion listener, even if a CancellationException was thrown
            onMediaGroupCompleted?.invoke()
        }
    }

    /**
     * Plays the provided [tag] and returns whether playback should continue
     * @return whether playback should continue: `true`: continue, `false`: stop playback
     */
    private suspend fun play(
        tag: AvTag,
        isAutomaticPlayback: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            suspend fun play() {
                ensureActive()
                when (tag) {
                    is SoundOrVideoTag -> soundTagPlayer.play(tag, mediaErrorListener)
                    is TTSTag -> {
                        awaitTtsPlayer(isAutomaticPlayback)?.play(tag)?.error?.let {
                            mediaErrorListener?.onTtsError(it, isAutomaticPlayback)
                        }
                    }
                }
                ensureActive()
            }

            try {
                play()
            } catch (e: MediaException) {
                when (e.continuationBehavior) {
                    STOP_MEDIA -> return@withContext false
                    CONTINUE_MEDIA -> return@withContext true
                    RETRY_MEDIA -> {
                        try {
                            Timber.i("retrying media")
                            play()
                            Timber.i("retry succeeded")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "retry media failed")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unexpected media exception. Continuing")
            }
            return@withContext true
        }

    /** Whether the provided side has available media */
    fun hasMedia(displayAnswer: Boolean): Boolean = if (displayAnswer) answerAvTags.any() else questionAvTags.any()

    /**
     * Plays all sounds for the current side, calling [onMediaGroupCompleted] when completed
     */
    fun playAll(side: SingleCardSide) =
        when (side) {
            SingleCardSide.FRONT -> playAllForSide(CardSide.QUESTION)
            SingleCardSide.BACK -> playAllForSide(CardSide.ANSWER)
        }

    /**
     * Replays all sounds for [side], calling [onMediaGroupCompleted] when completed
     */
    fun replayAll(side: SingleCardSide) =
        when (side) {
            SingleCardSide.BACK -> if (config.replayQuestion) playAllForSide(CardSide.BOTH) else playAllForSide(CardSide.ANSWER)
            SingleCardSide.FRONT -> playAllForSide(CardSide.QUESTION)
        }

    private suspend fun awaitTtsPlayer(isAutomaticPlayback: Boolean): TtsPlayer? {
        val player =
            withTimeoutOrNull(TTS_PLAYER_TIMEOUT_MS) {
                ttsPlayer.await()
            }
        if (player == null) {
            Timber.v("timeout waiting for TTS Player")
            val error = AndroidTtsError.InitTimeout
            mediaErrorListener?.onTtsError(error, isAutomaticPlayback)
        }
        return player
    }

    /** Ensures that only one [playAvTagsJob] is running at once */
    private fun playAvTagsJob(block: suspend CoroutineScope.() -> Unit) {
        val oldJob = playAvTagsJob
        this.playAvTagsJob =
            scope.launch {
                cancelPlayAvTagsJob(oldJob)
                block()
                playAvTagsJob = null
            }
    }

    @NeedsTest("finish moves to next sound")
    fun onVideoFinished() {
        soundTagPlayer.videoPlayer.onVideoFinished()
    }

    @NeedsTest("pause starts automatic answer")
    fun onVideoPaused() {
        Timber.i("video paused")
        soundTagPlayer.videoPlayer.onVideoPaused()
    }

    companion object {
        const val TTS_PLAYER_TIMEOUT_MS = 2_500L

        /**
         * @param mediaUriBase The base path to the media directory as a `file://` URI
         */
        @NeedsTest("ensure the lifecycle is subscribed to in a Reviewer")
        fun newInstance(
            viewer: AbstractFlashcardViewer,
            mediaUriBase: String,
        ): CardMediaPlayer {
            val scope = viewer.lifecycleScope
            val soundErrorListener = viewer.createMediaErrorListener()
            // tts can take a long time to init, this defers the operation until it's needed
            val tts = scope.async(Dispatchers.IO) { AndroidTtsPlayer.createInstance(viewer.lifecycleScope) }

            val soundPlayer = SoundTagPlayer(mediaUriBase, VideoPlayer { viewer.webViewClient!! })

            return CardMediaPlayer(
                soundTagPlayer = soundPlayer,
                ttsPlayer = tts,
                mediaErrorListener = soundErrorListener,
            ).apply {
                setOnMediaGroupCompletedListener(viewer::onMediaGroupCompleted)
            }
        }
    }
}

interface MediaErrorListener {
    @CheckResult
    fun onError(uri: Uri): MediaErrorBehavior

    @CheckResult
    fun onMediaPlayerError(
        mp: MediaPlayer?,
        which: Int,
        extra: Int,
        uri: Uri,
    ): MediaErrorBehavior

    fun onTtsError(
        error: TtsPlayer.TtsError,
        isAutomaticPlayback: Boolean,
    )
}

enum class MediaErrorBehavior {
    /** Stop playing media */
    STOP_MEDIA,

    /** Continue to the next media (if any) */
    CONTINUE_MEDIA,

    /** Retry the current media */
    RETRY_MEDIA,
}

fun AbstractFlashcardViewer.createMediaErrorListener(): MediaErrorListener {
    val activity = this
    return object : MediaErrorListener {
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
            AbstractFlashcardViewer.mediaErrorHandler.processTtsFailure(error, isAutomaticPlayback) {
                when (error) {
                    is AndroidTtsError.MissingVoiceError ->
                        TtsPlaybackErrorDialog.ttsPlaybackErrorDialog(activity, supportFragmentManager, error.tag)
                    is AndroidTtsError.InvalidVoiceError ->
                        activity.showSnackbar(getString(R.string.voice_not_supported))
                    else -> activity.showSnackbar(error.localizedErrorMessage(activity))
                }
            }
        }

        override fun onError(uri: Uri): MediaErrorBehavior {
            if (uri.scheme != "file") {
                return CONTINUE_MEDIA
            }

            try {
                val file = uri.toFile()
                // There is a multitude of transient issues with the MediaPlayer. (1, -1001) for example
                // Retrying fixes most of these
                if (file.exists()) return RETRY_MEDIA
                // just doesn't exist - process the error
                AbstractFlashcardViewer.mediaErrorHandler.processMissingMedia(
                    file,
                ) { filename: String? -> displayCouldNotFindMediaSnackbar(filename) }
                return CONTINUE_MEDIA
            } catch (e: Exception) {
                Timber.w(e)
                return CONTINUE_MEDIA
            }
        }
    }
}

/** An exception thrown when playing a sound, and how to continue playing sounds */
class MediaException : Exception {
    val continuationBehavior: MediaErrorBehavior
    constructor(errorHandling: MediaErrorBehavior) : super() {
        this.continuationBehavior = errorHandling
    }
    constructor(errorHandling: MediaErrorBehavior, exception: Exception) : super(exception) {
        this.continuationBehavior = errorHandling
    }
}
