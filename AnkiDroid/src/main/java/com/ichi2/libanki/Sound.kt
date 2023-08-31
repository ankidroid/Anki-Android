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

package com.ichi2.libanki

import android.content.Context
import android.media.*
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer.OnCompletionListener
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.VideoView
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.ReadText
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Sound.OnErrorListener.ErrorHandling.CONTINUE_AUDIO
import com.ichi2.libanki.Sound.SoundSide.*
import com.ichi2.utils.DisplayUtils
import net.ankiweb.rsdroid.BackendFactory.defaultLegacySchema
import org.intellij.lang.annotations.Language
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

private typealias SoundPath = String
// NICE_TO_HAVE: Abstract, then add tests for #6111
/**
 * Parses, loads and plays sound & video files
 * Called `Sound` Anki uses `[sound:]` for both audio and video
 */
class Sound(private val soundPlayer: SoundPlayer, private val soundDir: String) : SoundPlayer by soundPlayer {
    /**
     * @param soundDir base path to the media files
     */
    constructor(soundDir: String) : this(SoundPlayerImpl(), soundDir)

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
        if (side == QUESTION_AND_ANSWER) {
            return getSoundList(QUESTION) + getSoundList(ANSWER)
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

    /** Plays all the sounds for the indicated side(s)  */
    fun playSounds(side: SoundSide, errorListener: OnErrorListener?) {
        // If there are sounds to play for the current card, start with the first one
        val soundPaths = getSounds(side) ?: return
        Timber.d("playSounds: playing $side")
        this.playSound(
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
                playSound(paths[nextIndexToPlay++], this, errorListener)
            } else {
                Timber.i("Play all: Completed - releasing sound")
                soundPlayer.stopSounds()
            }
        }
    }

    override fun stopSounds() {
        soundPlayer.stopSounds()
        ReadText.stopTts() // TODO: Reconsider design
    }

    fun hasQuestion(): Boolean = getSounds(QUESTION) != null

    fun hasAnswer(): Boolean = getSounds(ANSWER) != null

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
         * @param soundDir -- the base path of the media files
         * @param content -- card content to be rendered that may contain embedded audio
         * @return -- the same content but in a format that will render working play buttons when audio was embedded
         */
        fun expandSounds(soundDir: String, content: String): String {
            if (!defaultLegacySchema) {
                return addPlayIcons(content)
            }
            val stringBuilder = StringBuilder()
            var contentLeft = content
            Timber.d("expandSounds")
            val matcher = SOUND_PATTERN.matcher(content)
            // While there is matches of the pattern for sound markers
            while (matcher.find()) {
                // Get the sound file name
                val sound = matcher.group(1)!!

                // Construct the sound path
                val soundPath = getSoundPath(soundDir, sound)

                // Construct the new content, appending the substring from the beginning of the content left until the
                // beginning of the sound marker
                // and then appending the html code to add the play button
                @Language("HTML")
                val button =
                    "<svg viewBox=\"0 0 64 64\"><circle cx=\"32\" cy=\"32\" r=\"29\" fill = \"lightgrey\"/>" +
                        "<path d=\"M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z\" fill = \"" +
                        "black\"/>Replay</svg>"
                val soundMarker = matcher.group()
                val markerStart = contentLeft.indexOf(soundMarker)
                stringBuilder.append(contentLeft.substring(0, markerStart))
                // The <span> around the button (SVG or PNG image) is needed to make the vertical alignment work.
                stringBuilder.append("<a class='replay-button replaybutton' href=\"playsound:")
                    .append(soundPath).append("\">")
                    .append("<span>").append(button)
                    .append("</span></a>")
                contentLeft = contentLeft.substring(markerStart + soundMarker.length)
                Timber.v("Content left = %s", contentLeft)
            }

            // unused code related to tts support taken out after v2.2alpha55
            // if/when tts support is considered complete, these comment lines serve no purpose
            stringBuilder.append(contentLeft)
            return stringBuilder.toString()
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

interface SoundPlayer {
    /**
     * Plays the given sound or video.
     * Video requires a surface: [hasVideoSurface]
     * [playVideoExternallyCallback] will be called if this is unavailable
     */
    fun playSound(
        soundPath: String,
        onCompletionListener: OnCompletionListener?,
        errorListener: Sound.OnErrorListener?
    )

    val hasVideoSurface: Boolean

    // When an audio finishes and I'm trying to replay it again, this method should check if the mMediaPlayer is null which means
    // the audio finished to return true, so that I would be able to play the same sound again.
    val isCurrentAudioFinished: Boolean

    /**
     * The Uri of the currently playing audio (or `null` if no audio playing)
     */
    val currentAudioUri: String?

    /**
     * Stops the playing sounds.
     */
    fun stopSounds()

    /**
     * Play or Pause the running sound. Called on pressing the content inside span tag.
     */
    fun playOrPauseSound()

    /** @return Whether the video was handled externally. Only used if [hasVideoSurface] is false */
    var playVideoExternallyCallback: ((soundPath: String, onCompletion: OnCompletionListener) -> Boolean)?
}

open class SoundPlayerImpl : SoundPlayer {
    override val currentAudioUri: String?
        get() = mCurrentAudioUri?.toString()

    override val isCurrentAudioFinished: Boolean
        get() = mMediaPlayer == null

    /**
     * Media player used to play the sounds. It's Nullable and that it is set only if a sound is playing or paused, otherwise it is null.
     */
    protected var mMediaPlayer: MediaPlayer? = null

    private var mCurrentAudioUri: Uri? = null

    /**
     * AudioManager to request/release audio focus
     */
    private var mAudioManager: AudioManager? = null

    private var mAudioFocusRequest: AudioFocusRequest? = null

    override val hasVideoSurface: Boolean = false

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media.
     * If videoView is null and the media is a video, then a request is sent to start the VideoPlayer Activity
     */
    override fun playSound(
        soundPath: String,
        onCompletionListener: OnCompletionListener?,
        errorListener: Sound.OnErrorListener?
    ) {
        Timber.d("Playing single sound")
        val completionListener = onCompletionListener ?: SingleSoundCompletionListener()
        val errorHandler = errorListener
            ?: Sound.OnErrorListener { _: MediaPlayer?, what: Int, extra: Int, _: String? ->
                Timber.w("Media Error: (%d, %d). Calling OnCompletionListener", what, extra)
                CONTINUE_AUDIO
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
        val soundUriPath = soundUri.path.toString()
        mCurrentAudioUri = soundUri

        val context = AnkiDroidApp.instance.applicationContext

        val isVideo = isVideo(soundUriPath)
        if (isVideo && !hasVideoSurface && playVideoExternallyCallback?.invoke(soundPath, completionListener) == true) {
            return
        }
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
                mAudioManager =
                    mAudioManager ?: context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                if (isVideo) {
                    prepareVideo(mediaPlayer)
                }

                mediaPlayer.setOnErrorListener { mp: MediaPlayer?, which: Int, extra: Int ->
                    val errorHandling = errorHandler.onError(
                        mp,
                        which,
                        extra,
                        soundPath
                    )
                    // returning false calls onComplete()
                    return@setOnErrorListener when (errorHandling) {
                        CONTINUE_AUDIO -> false
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

                // Set mAudioFocusRequest for API 26 and above.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mAudioFocusRequest =
                        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setOnAudioFocusChangeListener(audioFocusChangeListener)
                            .build()
                }
                CompatHelper.compat.requestAudioFocus(
                    mAudioManager!!,
                    audioFocusChangeListener,
                    mAudioFocusRequest
                )
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
                    CONTINUE_AUDIO -> {
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

    open fun prepareVideo(mediaPlayer: MediaPlayer) {
        // in the base class: playVideoExternallyCallback should have been called
    }

    override var playVideoExternallyCallback: ((soundPath: String, onCompletion: OnCompletionListener) -> Boolean)? = null

    private fun isVideo(soundPath: String): Boolean {
        // Check if the file extension is that of a known video format
        val extension = soundPath.getFileExtension()
        val isVideoExtension = listOf(*VIDEO_WHITELIST).contains(extension) || extension.isVideoMimeTypeExtension()
        // Also check that there is a video thumbnail, as some formats like mp4 can be audio only
        // No thumbnail: no video after all. (Or maybe not a video we can handle on the specific device.)
        return isVideoExtension && CompatHelper.compat.hasVideoThumbnail(soundPath)
    }

    // TODO: This seems wrong
    private fun String.getFileExtension() = substring(this.lastIndexOf(".") + 1).lowercase(Locale.getDefault())
    private fun String.isVideoMimeTypeExtension(): Boolean {
        val guessedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(this) ?: return false
        return guessedType.startsWith("video/")
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
        mAudioManager?.let {
            // mAudioFocusRequest was initialised for API 26 and above in playSoundInternal().
            CompatHelper.compat.abandonAudioFocus(it, audioFocusChangeListener, mAudioFocusRequest)
            mAudioManager = null
        }
    }

    override fun stopSounds() {
        mMediaPlayer?.let {
            it.stop()
            // TODO: Inefficient. Determine whether we want to release or stop, don't do both
            // Ensure `currentAudioUri` etc... still work when we do this
            releaseSound()
        }
    }

    override fun playOrPauseSound() {
        mMediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.start()
            }
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
         * Whitelist for video extensions
         */
        private val VIDEO_WHITELIST = arrayOf("3gp", "mp4", "webm", "mkv", "flv")

        /**
         * Listener to handle audio focus. Currently blank because we're not respecting losing focus from other apps.
         */
        private val audioFocusChangeListener = OnAudioFocusChangeListener { }
    }
}

class VideoPlayer(private val videoView: VideoView) : SoundPlayerImpl() {

    // don't call out to external video players
    override val hasVideoSurface = true

    /** Plays the given video */
    fun play(
        path: String,
        onCompletionListener: OnCompletionListener?,
        onErrorListener: Sound.OnErrorListener?
    ) = playSound(path, onCompletionListener, onErrorListener)
    fun notifyConfigurationChanged() {
        mMediaPlayer?.let {
            configureVideo(videoView, it.videoWidth, it.videoHeight)
        }
    }

    override fun prepareVideo(mediaPlayer: MediaPlayer) {
        mediaPlayer.setDisplay(videoView.holder)
        mediaPlayer.setOnVideoSizeChangedListener { _, width: Int, height: Int ->
            configureVideo(videoView, width, height)
        }
    }

    private fun configureVideo(videoView: VideoView, videoWidth: Int, videoHeight: Int) {
        // get the display
        val context = AnkiDroidApp.instance.applicationContext
        // adjust the size of the video so it fits on the screen
        val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()
        val point = DisplayUtils.getDisplayDimensions(context)
        val screenWidth = point.x
        val screenHeight = point.y
        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()
        val lp = videoView.layoutParams
        if (videoProportion > screenProportion) {
            lp.width = screenWidth
            lp.height = (screenWidth.toFloat() / videoProportion).toInt()
        } else {
            lp.width = (videoProportion * screenHeight.toFloat()).toInt()
            lp.height = screenHeight
        }
        videoView.layoutParams = lp
    }
}

@Throws(Exception::class)
private fun MediaMetadataRetriever.getDuration(context: Context, uri: Uri): Long {
    this.setDataSource(context, uri)
    val duration = this.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    return duration!!.toLong()
}
