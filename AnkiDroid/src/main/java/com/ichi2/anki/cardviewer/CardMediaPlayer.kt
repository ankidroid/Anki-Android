// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AbstractFlashcardViewer.Companion.getMediaBaseUrl
import com.ichi2.anki.AndroidTtsError
import com.ichi2.anki.AndroidTtsPlayer
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cardviewer.MediaErrorBehavior.CONTINUE_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.RETRY_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.STOP_MEDIA
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.storage.CollectionHelper.getMediaDirectory
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.reviewer.CardSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.Closeable
import kotlin.time.Duration.Companion.milliseconds

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
 * * [autoplayAllForSide]
 * * [playAllForSide]
 * * [replayAll]
 * * [playOne]
 * * [stop]
 *
 * @see AvTag
 *
 * [setOnMediaGroupCompletedListener] can be used to call
 * something when [playAllForSide], [replayAll] or [playOne] completes. It is not called if playback is stopped
 */
@NeedsTest("Integration test: A video is autoplayed if it's the first media on a card")
@NeedsTest("A sound is played after a video finishes")
@NeedsTest("Pausing a video calls onMediaGroupCompleted")
class CardMediaPlayer : Closeable {
    private val soundTagPlayer: SoundTagPlayer
    private val ttsPlayer: Deferred<TtsPlayer>
    private val mediaErrorListener: MediaErrorListener

    @VisibleForTesting
    constructor(
        soundTagPlayer: SoundTagPlayer,
        ttsPlayer: Deferred<TtsPlayer>,
        mediaErrorListener: MediaErrorListener,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        this.scope = CoroutineScope(SupervisorJob() + dispatcher)
        this.soundTagPlayer = soundTagPlayer
        this.ttsPlayer = ttsPlayer
        this.mediaErrorListener = mediaErrorListener
    }

    constructor(javascriptEvaluator: JavascriptEvaluator, mediaErrorListener: MediaErrorListener) {
        this.scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        this.mediaErrorListener = mediaErrorListener
        this.soundTagPlayer =
            SoundTagPlayer(
                soundUriBase = getMediaBaseUrl(getMediaDirectory(appContext)),
                videoPlayer = VideoPlayer(javascriptEvaluator),
            )
        this.ttsPlayer = scope.async { AndroidTtsPlayer.createInstance(scope) }
    }

    private val scope: CoroutineScope

    /** Serializes playbacks to avoid overloading the thread pool and a potential deadlock */
    private val playbackMutex = Mutex()

    var isEnabled: Boolean = true
        private set

    suspend fun setEnabled(enabled: Boolean) {
        playbackMutex.withLock {
            isEnabled = enabled
            if (!enabled) stop()
        }
    }

    private val playingJob = MutableStateFlow<Job?>(null)

    @VisibleForTesting
    var playAvTagsJob: Job?
        get() = playingJob.value
        set(value) {
            playingJob.value = value
            value?.invokeOnCompletion { playingJob.compareAndSet(value, null) }
        }
    val isPlaying get() = playAvTagsJob != null

    /** Suspends while media is playing */
    suspend fun awaitIdle() {
        playingJob.first { it == null }
    }

    /** Launches [block] as [playAvTagsJob], which is cleared however the playback ends */
    private suspend fun launchPlayback(block: suspend () -> Unit) {
        playbackMutex.withLock {
            if (!isEnabled) return
            val previousJob = playAvTagsJob
            val job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // registered before the job is visible, so stopping it always waits for the outgoing media
                    withContext(NonCancellable) { previousJob?.join() }
                    // continues on the player's dispatcher, not the caller's thread; also checks cancellation
                    yield()
                    block()
                }
            // replaces the previous job before it ends, so the player isn't idle in between
            playAvTagsJob = job
            try {
                previousJob?.cancelAndJoin()
            } catch (e: CancellationException) {
                job.cancel()
                throw e
            }
        }
    }

    private var onMediaGroupCompleted: (suspend () -> Unit)? = null

    fun setOnMediaGroupCompletedListener(listener: (suspend () -> Unit)?) {
        onMediaGroupCompleted = listener
    }

    suspend fun autoplayAllForSide(
        card: Card,
        cardSide: CardSide,
    ) {
        val avTags = withCol { if (card.autoplay(this)) card.avTags(this, cardSide) else null }
        if (avTags != null) {
            launchPlayback { playAvTags(avTags, cardSide) }
        } else {
            stop()
        }
    }

    /**
     * Obtains all the [AvTag]s for the [cardSide] and plays them sequentially
     */
    suspend fun playAllForSide(
        card: Card,
        cardSide: CardSide,
    ) {
        launchPlayback {
            val avTags = withCol { card.avTags(this, cardSide) }
            playAvTags(avTags, cardSide)
        }
    }

    private suspend fun playAvTags(
        avTagList: List<AvTag>,
        cardSide: CardSide,
    ) {
        Timber.i("playing sounds for %s", cardSide)
        for ((index, avTag) in avTagList.withIndex()) {
            Timber.d("playing AV Tag %d/%d", index + 1, avTagList.size)
            if (!play(avTag, isAutomaticPlayback = true)) {
                Timber.d("stopping AV Tag playback early")
                break
            }
        }
        // skipped if canceled: the media did not complete, and the card side may have changed
        onMediaGroupCompleted?.invoke()
    }

    /**
     * Replays all sounds for [side], calling [onMediaGroupCompleted] when completed
     */
    suspend fun replayAll(
        card: Card,
        side: SingleCardSide,
    ) {
        // the lookup is part of the playback, so stopping the player also cancels it
        launchPlayback {
            val (cardSide, avTags) =
                withCol {
                    val cardSide =
                        when (side) {
                            SingleCardSide.FRONT -> CardSide.QUESTION
                            SingleCardSide.BACK ->
                                if (card.replayQuestionAudioOnAnswerSide(this)) CardSide.BOTH else CardSide.ANSWER
                        }
                    cardSide to card.avTags(this, cardSide)
                }
            playAvTags(avTags, cardSide)
        }
    }

    private fun Card.avTags(
        col: Collection,
        cardSide: CardSide,
    ): List<AvTag> {
        val renderOutput = renderOutput(col)
        return when (cardSide) {
            CardSide.QUESTION -> renderOutput.questionAvTags
            CardSide.ANSWER -> renderOutput.answerAvTags
            CardSide.BOTH -> renderOutput.questionAvTags + renderOutput.answerAvTags
        }
    }

    suspend fun playOne(tag: AvTag) {
        launchPlayback {
            Timber.i("playing one AV Tag")
            play(tag, isAutomaticPlayback = false)
            Timber.v("completed playing one AV Tag")
            onMediaGroupCompleted?.invoke()
        }
    }

    suspend fun stop() {
        if (isPlaying) Timber.i("stopping playing all AV tags")
        playAvTagsJob?.cancelAndJoin()
    }

    override fun close() {
        soundTagPlayer.release()
        ttsPlayer.close(logPrefix = "ttsPlayer")
        scope.cancel()
    }

    /**
     * Plays the provided [tag] and returns whether playback should continue
     * @return whether playback should continue: `true`: continue, `false`: stop playback
     */
    private suspend fun play(
        tag: AvTag,
        isAutomaticPlayback: Boolean,
    ): Boolean {
        suspend fun play() {
            currentCoroutineContext().ensureActive()
            when (tag) {
                is SoundOrVideoTag -> soundTagPlayer.play(tag, mediaErrorListener)
                is TTSTag -> {
                    awaitTtsPlayer(isAutomaticPlayback)?.play(tag)?.error?.let {
                        mediaErrorListener.onTtsError(it, isAutomaticPlayback)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
        }

        try {
            play()
        } catch (e: MediaException) {
            when (e.continuationBehavior) {
                STOP_MEDIA -> return false
                CONTINUE_MEDIA -> return true
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
        return true
    }

    private suspend fun awaitTtsPlayer(isAutomaticPlayback: Boolean): TtsPlayer? {
        val player =
            withTimeoutOrNull(TTS_PLAYER_TIMEOUT) {
                ttsPlayer.await()
            }
        if (player == null) {
            Timber.v("timeout waiting for TTS Player")
            val error = AndroidTtsError.InitTimeout
            mediaErrorListener.onTtsError(error, isAutomaticPlayback)
        }
        return player
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
        private val TTS_PLAYER_TIMEOUT = 2_500.milliseconds
    }
}

/**
 * Cancels the [Deferred] and safely closes its resulting [Closeable] upon completion.
 *
 * The deferred is cancelled immediately.
 * When it completes, the underlying [Closeable] is closed.
 *
 * @param logPrefix Prefix used when logging
 */
private fun Deferred<Closeable>.close(logPrefix: String) {
    this.cancel()
    this.invokeOnCompletion {
        try {
            this.getCompleted().close()
            Timber.d("$logPrefix closed")
        } catch (_: CancellationException) {
            // Ignore: no value was produced, nothing to close
        } catch (e: Exception) {
            Timber.w(e, "$logPrefix close()")
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
