/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>                          *
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

import android.content.Context
import android.media.*
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer.OnCompletionListener
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.ichi2.libanki.SoundOrVideoTag
import com.ichi2.libanki.addPlayIcons
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mpg", "mpeg", "mkv", "avi")

private typealias SoundPath = String
// NICE_TO_HAVE: Abstract, then add tests for #6111
/**
 * Parses, loads and plays sound & video files
 * Called `Sound` Anki uses `[sound:]` for both audio and video
 */
class Sound(private val soundPlayer: SoundPlayer, private val soundDir: String) {
    /**
     * @param soundDir base path to the media files
     */
    constructor(soundDir: String) : this(SoundPlayer(), soundDir)

    /**
     * The subset of sounds to involve
     * @param int Used for serialisation
     */
    enum class SoundSide(val int: Int) {
        QUESTION(0), ANSWER(1), QUESTION_AND_ANSWER(2);
    }

    /** Sounds for the question/answer of a card */
    // Stops code paths where QUESTION_AND_ANSWER is invalid
    enum class SingleSoundSide {
        QUESTION, ANSWER;

        fun toSoundSide(): SoundSide = when (this) {
            QUESTION -> SoundSide.QUESTION
            ANSWER -> SoundSide.ANSWER
        }
    }

    /**
     * Stores sounds for the current card. Maps from a side to paths paths
     * Should be accessed via [getSounds]
     */
    private val soundPaths: MutableMap<SoundSide, MutableList<SoundPath>> = EnumMap(SoundSide::class.java)

    /** Returns a non-empty list of sounds, or null if there are no values */
    @VisibleForTesting
    fun getSounds(side: SoundSide) = getSoundList(side).let {
        if (!it.any()) null else it
    }

    private fun getSoundList(side: SoundSide): List<SoundPath> {
        if (side == SoundSide.QUESTION_AND_ANSWER) {
            return getSoundList(SoundSide.QUESTION) + getSoundList(SoundSide.ANSWER)
        }
        return soundPaths[side] ?: emptyList()
    }

    /**
     * Clears current sound paths; call before [expandSounds] + [addSounds]
     * is called for the next card
     */
    fun resetSounds() {
        soundPaths.clear()
    }

    /**
     * Stores entries to the filepaths for sounds, categorized as belonging to the front (question) or back (answer) of cards.
     * Note that all sounds embedded in the content will be given the same base categorization of question or answer.
     * Additionally, the result is to be sorted by the order of appearance on the card.
     * @param tags the entries expected in display order
     * @param side the base categorization of the sounds in the content
     */
    fun addSounds(tags: List<SoundOrVideoTag>, side: SingleSoundSide) {
        val soundPathCollection = soundPaths.getOrPut(side.toSoundSide()) { mutableListOf() }

        Timber.d("Adding %d sounds to side: %s", tags.size, side)
        val paths = tags.map { getSoundPath(soundDir, it.filename) }
        soundPathCollection.addAll(paths)
    }

    fun playSound(
        replacedUrl: String,
        onCompletionListener: OnCompletionListener?,
        soundErrorListener: OnErrorListener
    ) {
        soundPlayer.playSound(replacedUrl, onCompletionListener, soundErrorListener)
    }

    /** Plays all the sounds for the indicated side(s)  */
    fun playSounds(side: SoundSide, errorListener: OnErrorListener?) {
        // If there are sounds to play for the current card, start with the first one
        val soundPaths = getSounds(side) ?: return
        Timber.d("playSounds: playing $side")
        this.soundPlayer.playSound(
            soundPaths[0],
            PlayAllCompletionListener(side, errorListener),
            errorListener
        )
    }

    /** Returns the total length of all sounds for the side in milliseconds */
    fun getSoundsLength(side: SoundSide): Long {
        val soundPaths = getSounds(side) ?: return 0
        val metaRetriever = MediaMetadataRetriever()
        val context = AnkiDroidApp.instance.applicationContext
        return soundPaths
            .map { Uri.parse(it) }
            .sumOf { uri ->
                try {
                    metaRetriever.getDuration(context, uri)
                } catch (e: Exception) {
                    Timber.w(
                        e,
                        "metaRetriever - Error setting Data Source for mediaRetriever (media doesn't exist or forbidden?)."
                    )
                    0
                }
            }
    }

    /**
     * Class used to play all sounds for a given card side
     */
    private inner class PlayAllCompletionListener(
        private val side: SoundSide,
        private val errorListener: OnErrorListener?
    ) : OnCompletionListener {
        /** Index of next sound to play inside `getSounds(side)` */
        // this is a completion listener: [onCompletion] is first called after the first sound
        private var nextIndexToPlay = 1
        override fun onCompletion(mp: MediaPlayer) {
            val paths = getSounds(side) ?: emptyList() // emptyList -> stopSounds()
            // If there are still more sounds to play for the current card, play the next one
            if (nextIndexToPlay < paths.size) {
                Timber.i("Play all: Playing next sound")
                soundPlayer.playSound(paths[nextIndexToPlay++], this, errorListener)
            } else {
                Timber.i("Play all: Completed - releasing sound")
                soundPlayer.stopSounds()
            }
        }
    }

    fun stopSounds() {
        Timber.d("stopping sounds")
        soundPlayer.stopSounds()
        ReadText.stopTts() // TODO: Reconsider design
    }

    fun hasQuestion(): Boolean = getSounds(SoundSide.QUESTION) != null

    fun hasAnswer(): Boolean = getSounds(SoundSide.ANSWER) != null

    fun interface OnErrorListener {
        fun onError(mp: MediaPlayer?, which: Int, extra: Int, path: String?): ErrorHandling

        enum class ErrorHandling {
            /** Stop playing audio */
            STOP_AUDIO,

            /** Continue to the next audio (if any) */
            CONTINUE_AUDIO,

            /** Retry the current audio */
            RETRY_AUDIO
        }
    }

    companion object {
        /**
         * Pattern used to identify the markers for sound files
         */
        val SOUND_PATTERN: Pattern = Pattern.compile("\\[sound:([^\\[\\]]*)]")

        // TODO join with SOUND_PATTERN
        val SOUND_RE = SOUND_PATTERN.toRegex()

        /**
         * Pattern used to parse URI (according to http://tools.ietf.org/html/rfc3986#page-50)
         */
        private val sUriPattern =
            Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?$")

        /** Extract SoundOrVideoTag instances from content where sound tags are in the form: [sound:filename.mp3]  */
        @CheckResult
        fun extractTagsFromLegacyContent(content: String): List<SoundOrVideoTag> {
            val matcher = SOUND_PATTERN.matcher(content)
            // While there is matches of the pattern for sound markers
            val ret = mutableListOf<SoundOrVideoTag>()
            while (matcher.find()) {
                // Get the sound file name
                val sound = matcher.group(1)!!
                ret.add(SoundOrVideoTag(sound))
            }
            return ret
        }

        /**
         * expandSounds takes content with embedded sound file placeholders and expands them to reference the actual media
         * file
         *
         * @param content -- card content to be rendered that may contain embedded audio
         * @return -- the same content but in a format that will render working play buttons when audio was embedded
         */
        fun expandSounds(content: String): String {
            return addPlayIcons(content)
        }

        /**
         * @param soundDir -- base path to the media files.
         * @param sound -- path to the sound file from the card content.
         * @return absolute URI to the sound file.
         */
        fun getSoundPath(soundDir: String, sound: String): String {
            val trimmedSound = sound.trim { it <= ' ' }
            return if (hasURIScheme(trimmedSound)) {
                trimmedSound
            } else {
                soundDir + Uri.encode(sound.trimEnd())
            }
        }

        /**
         * @param path -- path to the sound file from the card content.
         * @return true if path is well-formed URI and contains URI scheme.
         */
        private fun hasURIScheme(path: String): Boolean {
            val uriMatcher = sUriPattern.matcher(path.trim { it <= ' ' })
            return uriMatcher.matches() && uriMatcher.group(2) != null
        }
    }
}

open class SoundPlayer {
    /**
     * Media player used to play the sounds. It's Nullable and that it is set only if a sound is playing or paused, otherwise it is null.
     */
    private var mMediaPlayer: MediaPlayer? = null

    /**
     * AudioManager to request/release audio focus
     */
    private var audioManager: AudioManager =
        AnkiDroidApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioFocusRequest: AudioFocusRequestCompat by lazy {
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
    }

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media.
     * If videoView is null and the media is a video, then a request is sent to start the VideoPlayer Activity
     */
    fun playSound(
        soundPath: String,
        onCompletionListener: OnCompletionListener?,
        errorListener: Sound.OnErrorListener?
    ) {
        Timber.d("Playing single sound")
        val completionListener = onCompletionListener ?: SingleSoundCompletionListener()
        val errorHandler = errorListener
            ?: Sound.OnErrorListener { _: MediaPlayer?, what: Int, extra: Int, _: String? ->
                Timber.w("Media Error: (%d, %d). Calling OnCompletionListener", what, extra)
                Sound.OnErrorListener.ErrorHandling.CONTINUE_AUDIO
            }
        playSoundInternal(soundPath, completionListener, errorHandler)
    }

    /**
     * Plays a sound without ensuring that the playAllListener will release the audio
     */
    private fun playSoundInternal(
        soundPath: String,
        completionListener: OnCompletionListener,
        errorHandler: Sound.OnErrorListener
    ) {
        Timber.d("Playing %s", soundPath)
        val soundUri = Uri.parse(soundPath)
        val context = AnkiDroidApp.instance.applicationContext

        // Play media
        fun playMedia() {
            try {
                // Create media player
                if (mMediaPlayer == null) {
                    Timber.d("Creating media player for playback")
                    mMediaPlayer = MediaPlayer()
                } else {
                    Timber.d("Resetting media for playback")
                    mMediaPlayer!!.reset()
                }
                val mediaPlayer = mMediaPlayer!!

                mediaPlayer.setOnErrorListener { mp: MediaPlayer?, which: Int, extra: Int ->
                    val errorHandling = errorHandler.onError(
                        mp,
                        which,
                        extra,
                        soundPath
                    )
                    // returning false calls onComplete()
                    return@setOnErrorListener when (errorHandling) {
                        Sound.OnErrorListener.ErrorHandling.CONTINUE_AUDIO -> false
                        Sound.OnErrorListener.ErrorHandling.RETRY_AUDIO -> {
                            playMedia()
                            true
                        }
                        Sound.OnErrorListener.ErrorHandling.STOP_AUDIO -> {
                            stopSounds()
                            true
                        }
                    }
                }
                // Setup the MediaPlayer
                mediaPlayer.setDataSource(context, soundUri)
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mediaPlayer.setOnPreparedListener {
                    Timber.d("Starting media player")
                    it.start()
                }
                mediaPlayer.setOnCompletionListener(completionListener)
                mediaPlayer.prepareAsync()
                Timber.d("Requesting audio focus")

                AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
            } catch (e: Exception) {
                Timber.e(e, "playSounds - Error reproducing sound %s", soundPath)
                when (
                    errorHandler.onError(
                        mMediaPlayer,
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED,
                        0,
                        soundPath
                    )
                ) {
                    Sound.OnErrorListener.ErrorHandling.CONTINUE_AUDIO -> {
                        Timber.d("Force playing next sound.")
                        completionListener.onCompletion(mMediaPlayer)
                    }
                    Sound.OnErrorListener.ErrorHandling.STOP_AUDIO -> stopSounds()
                    Sound.OnErrorListener.ErrorHandling.RETRY_AUDIO -> playMedia()
                }
            }
        }

        playMedia()
    }

    /**
     * Releases the sound.
     */
    private fun releaseSound() {
        Timber.d("Releasing sounds and abandoning audio focus")
        mMediaPlayer?.let {
            // Required to remove warning: "mediaplayer went away with unhandled events"
            // https://stackoverflow.com/questions/9609479/android-mediaplayer-went-away-with-unhandled-events
            it.reset()
            it.release()
            mMediaPlayer = null
        }
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }

    open fun stopSounds() {
        mMediaPlayer?.let {
            it.stop()
            // TODO: Inefficient. Determine whether we want to release or stop, don't do both
            // Ensure `currentAudioUri` etc... still work when we do this
            releaseSound()
        }
    }

    /** #5414 - Ensures playing a single sound performs cleanup  */
    private inner class SingleSoundCompletionListener : OnCompletionListener {
        override fun onCompletion(mp: MediaPlayer) {
            Timber.d("Single Sound completed")
            releaseSound()
        }
    }

    companion object {
        /**
         * Listener to handle audio focus. Currently blank because we're not respecting losing focus from other apps.
         */
        private val audioFocusChangeListener = OnAudioFocusChangeListener { }
    }
}

@Throws(Exception::class)
private fun MediaMetadataRetriever.getDuration(context: Context, uri: Uri): Long {
    this.setDataSource(context, uri)
    val duration = this.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    return duration!!.toLong()
}
