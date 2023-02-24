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

import android.app.Activity
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
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.ReadText
import com.ichi2.compat.CompatHelper
import com.ichi2.utils.DisplayUtils
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.BackendFactory.defaultLegacySchema
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*
import java.util.regex.Pattern

// NICE_TO_HAVE: Abstract, then add tests for #6111
/**
 * Class used to parse, load and play sound files on AnkiDroid.
 */
@KotlinCleanup("IDE Lint")
class Sound {
    /**
     * Media player used to play the sounds. It's Nullable and that it is set only if a sound is playing or paused, otherwise it is null.
     */
    private var mMediaPlayer: MediaPlayer? = null

    /**
     * It's used to store the Uri of the current Audio in case of running or pausing.
     */
    private var mCurrentAudioUri: Uri? = null

    /**
     * AudioManager to request/release audio focus
     */
    private var mAudioManager: AudioManager? = null

    /**
     * Weak reference to the activity which is attempting to play the sound
     */
    private var mCallingActivity: WeakReference<Activity?>? = null

    @VisibleForTesting
    fun getSounds(side: SoundSide): ArrayList<String>? {
        if (side == SoundSide.QUESTION_AND_ANSWER) {
            makeQuestionAnswerList()
        }
        return mSoundPaths[side]
    }

    /**
     * Subset Flags: Flags that indicate the subset of sounds to involve
     */
    enum class SoundSide(val int: Int) {
        QUESTION(0), ANSWER(1), QUESTION_AND_ANSWER(2);
    }

    /**
     * Stores sounds for the current card, key is one of the subset flags. It is intended that it not contain empty lists, and code assumes this will be true.
     */
    private val mSoundPaths: HashMap<SoundSide, ArrayList<String>> = HashMap()
    private var mAudioFocusRequest: AudioFocusRequest? = null

    // Clears current sound paths; call before parseSounds() calls
    fun resetSounds() {
        mSoundPaths.clear()
    }

    /**
     * Stores entries to the filepaths for sounds, categorized as belonging to the front (question) or back (answer) of cards.
     * Note that all sounds embedded in the content will be given the same base categorization of question or answer.
     * Additionally, the result is to be sorted by the order of appearance on the card.
     * @param soundDir -- base path to the media files
     * @param tags -- the entries expected in display order
     * @param qa -- the base categorization of the sounds in the content, SoundSide.SOUNDS_QUESTION or SoundSide.SOUNDS_ANSWER
     */
    fun addSounds(soundDir: String, tags: List<SoundOrVideoTag>, qa: SoundSide) {
        for ((filename) in tags) {
            // Create appropriate list if needed; list must not be empty so long as code does no check
            if (!mSoundPaths.containsKey(qa)) {
                mSoundPaths[qa] = ArrayList(0)
            }
            val soundPath = getSoundPath(soundDir, filename)
            // Construct the sound path and store it
            Timber.d("Adding Sound to side: %s", qa)
            mSoundPaths[qa]!!.add(soundPath)
        }
    }

    /**
     * makeQuestionAnswerSoundList creates a single list of both the question and answer audio only if it does not
     * already exist. It's intended for lazy evaluation, only in the rare cases when both sides are fully played
     * together, which even when configured as supported may not be instigated
     * @return True if a non-null list was created, or false otherwise
     */
    private fun makeQuestionAnswerList(): Boolean {
        // if combined list already exists, don't recreate
        if (mSoundPaths.containsKey(SoundSide.QUESTION_AND_ANSWER)) {
            return false // combined list already exists
        }

        // make combined list only if necessary to avoid an empty combined list
        if (mSoundPaths.containsKey(SoundSide.QUESTION) || mSoundPaths.containsKey(SoundSide.ANSWER)) {
            // some list exists to place into combined list
            mSoundPaths[SoundSide.QUESTION_AND_ANSWER] = ArrayList(0)
        } else { // no need to make list
            return false
        }
        val combinedSounds = mSoundPaths[SoundSide.QUESTION_AND_ANSWER]!!
        if (mSoundPaths.containsKey(SoundSide.QUESTION)) {
            combinedSounds.addAll(mSoundPaths[SoundSide.QUESTION]!!)
        }
        if (mSoundPaths.containsKey(SoundSide.ANSWER)) {
            combinedSounds.addAll(mSoundPaths[SoundSide.ANSWER]!!)
        }
        return true
    }

    /**
     * Plays the sounds for the indicated sides
     * @param qa -- One of SoundSide.SOUNDS_QUESTION, SoundSide.SOUNDS_ANSWER, or SoundSide.SOUNDS_QUESTION_AND_ANSWER
     */
    fun playSounds(qa: SoundSide, errorListener: OnErrorListener?) {
        // If there are sounds to play for the current card, start with the first one
        if (mSoundPaths.containsKey(qa)) {
            Timber.d("playSounds %s", qa)
            playSoundInternal(
                mSoundPaths[qa]!![0],
                PlayAllCompletionListener(qa, errorListener),
                null,
                errorListener
            )
        } else if (qa == SoundSide.QUESTION_AND_ANSWER) {
            if (makeQuestionAnswerList()) {
                Timber.d("playSounds: playing both question and answer")
                playSoundInternal(
                    mSoundPaths[qa]!![0],
                    PlayAllCompletionListener(qa, errorListener),
                    null,
                    errorListener
                )
            } else {
                Timber.d("playSounds: No question answer list, not playing sound")
            }
        }
    }

    /**
     * Returns length in milliseconds.
     * @param qa -- One of SoundSide.SOUNDS_QUESTION, SoundSide.SOUNDS_ANSWER, or SoundSide.SOUNDS_QUESTION_AND_ANSWER
     */
    fun getSoundsLength(qa: SoundSide): Long {
        var length: Long = 0
        if (qa == SoundSide.QUESTION_AND_ANSWER && makeQuestionAnswerList() || mSoundPaths.containsKey(
                qa
            )
        ) {
            val metaRetriever = MediaMetadataRetriever()
            for (uri_string in mSoundPaths[qa]!!) {
                val soundUri = Uri.parse(uri_string)
                try {
                    metaRetriever.setDataSource(
                        AnkiDroidApp.instance.applicationContext,
                        soundUri
                    )
                    length += metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                        .toLong()
                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "metaRetriever - Error setting Data Source for mediaRetriever (media doesn't exist or forbidden?)."
                    )
                }
            }
        }
        return length
    }

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media.
     * If videoView is null and the media is a video, then a request is sent to start the VideoPlayer Activity
     */
    fun playSound(
        soundPath: String,
        playAllListener: OnCompletionListener?,
        videoView: VideoView?,
        errorListener: OnErrorListener?
    ) {
        Timber.d("Playing single sound")
        val completionListener = SingleSoundCompletionListener(playAllListener)
        playSoundInternal(soundPath, completionListener, videoView, errorListener)
    }

    /**
     * Play or Pause the running sound. Called on pressing the content inside span tag.
     */
    @KotlinCleanup("?.let { }")
    fun playOrPauseSound() {
        mMediaPlayer ?: return
        if (mMediaPlayer!!.isPlaying) {
            mMediaPlayer!!.pause()
        } else {
            mMediaPlayer!!.start()
        }
    }

    // When an audio finishes and I'm trying to replay it again, this method should check if the mMediaPlayer is null which means
    // the audio finished to return true, so that I would be able to play the same sound again.
    @KotlinCleanup("simplify property with ?. ")
    val isCurrentAudioFinished: Boolean
        get() = mMediaPlayer == null

    /**
     * Plays a sound without ensuring that the playAllListener will release the audio
     */
    @KotlinCleanup("remove timber - always true")
    private fun playSoundInternal(
        soundPath: String,
        playAllListener: OnCompletionListener,
        videoView: VideoView?,
        errorListener: OnErrorListener?
    ) {
        Timber.d("Playing %s has listener? %b", soundPath, true)
        val soundUri = Uri.parse(soundPath)
        mCurrentAudioUri = soundUri
        val errorHandler = errorListener
            ?: OnErrorListener { _: MediaPlayer?, what: Int, extra: Int, _: String? ->
                Timber.w("Media Error: (%d, %d). Calling OnCompletionListener", what, extra)
                false
            }
        if ("tts" == soundPath.substring(0, 3)) {
            // TODO: give information about did
//            ReadText.textToSpeech(soundPath.substring(4, soundPath.length()),
//                    Integer.parseInt(soundPath.substring(3, 4)));
        } else {
            // Check if the file extension is that of a known video format
            val extension =
                soundPath.substring(soundPath.lastIndexOf(".") + 1).lowercase(Locale.getDefault())
            var isVideo = listOf(*VIDEO_WHITELIST).contains(extension)
            if (!isVideo) {
                val guessedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                isVideo = guessedType != null && guessedType.startsWith("video/")
            }
            // Also check that there is a video thumbnail, as some formats like mp4 can be audio only
            isVideo = isVideo && hasVideoThumbnail(soundUri)
            // No thumbnail: no video after all. (Or maybe not a video we can handle on the specific device.)
            // If video file but no SurfaceHolder provided then ask AbstractFlashcardViewer to provide a VideoView
            // holder
            if (isVideo && videoView == null && mCallingActivity != null && mCallingActivity!!.get() != null) {
                Timber.d("Requesting AbstractFlashcardViewer play video - no SurfaceHolder")
                mediaCompletionListener = playAllListener
                (mCallingActivity!!.get() as AbstractFlashcardViewer?)!!.playVideo(soundPath)
                return
            }
            // Play media
            try {
                // Create media player
                if (mMediaPlayer == null) {
                    Timber.d("Creating media player for playback")
                    mMediaPlayer = MediaPlayer()
                } else {
                    Timber.d("Resetting media for playback")
                    mMediaPlayer!!.reset()
                }
                if (mAudioManager == null) {
                    mAudioManager = AnkiDroidApp.instance.applicationContext.getSystemService(
                        Context.AUDIO_SERVICE
                    ) as AudioManager
                }
                // Provide a VideoView to the MediaPlayer if valid video file
                if (isVideo && videoView != null) {
                    mMediaPlayer!!.setDisplay(videoView.holder)
                    mMediaPlayer!!.setOnVideoSizeChangedListener { _: MediaPlayer?, width: Int, height: Int ->
                        configureVideo(
                            videoView,
                            width,
                            height
                        )
                    }
                }
                mMediaPlayer!!.setOnErrorListener { mp: MediaPlayer?, which: Int, extra: Int ->
                    errorHandler.onError(
                        mp,
                        which,
                        extra,
                        soundPath
                    )
                }
                // Setup the MediaPlayer
                @KotlinCleanup("simplify with scope function on mediaPlayer")
                mMediaPlayer!!.setDataSource(
                    AnkiDroidApp.instance.applicationContext,
                    soundUri
                )
                mMediaPlayer!!.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mMediaPlayer!!.setOnPreparedListener {
                    Timber.d("Starting media player")
                    mMediaPlayer!!.start()
                }
                mMediaPlayer!!.setOnCompletionListener(playAllListener)
                mMediaPlayer!!.prepareAsync()
                Timber.d("Requesting audio focus")

                // Set mAudioFocusRequest for API 26 and above.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mAudioFocusRequest =
                        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setOnAudioFocusChangeListener(afChangeListener)
                            .build()
                }
                CompatHelper.compat.requestAudioFocus(mAudioManager!!, afChangeListener, mAudioFocusRequest)
            } catch (e: Exception) {
                Timber.e(e, "playSounds - Error reproducing sound %s", soundPath)
                if (!errorHandler.onError(
                        mMediaPlayer,
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED,
                        0,
                        soundPath
                    )
                ) {
                    Timber.d("Force playing next sound.")
                    playAllListener.onCompletion(mMediaPlayer)
                }
            }
        }
    }

    @KotlinCleanup("simplify code with ?. or make uri non-null")
    private fun hasVideoThumbnail(soundUri: Uri?): Boolean {
        if (soundUri == null) {
            return false
        }
        val path = soundUri.path ?: return false
        return CompatHelper.compat.hasVideoThumbnail(path)
    }

    val currentAudioUri: String?
        get() = if (mCurrentAudioUri == null) {
            null
        } else {
            mCurrentAudioUri.toString()
        }

    fun notifyConfigurationChanged(videoView: VideoView) {
        if (mMediaPlayer != null) {
            configureVideo(videoView, mMediaPlayer!!.videoWidth, mMediaPlayer!!.videoHeight)
        }
    }

    /** #5414 - Ensures playing a single sound performs cleanup  */
    private inner class SingleSoundCompletionListener(private val userCallback: OnCompletionListener?) :
        OnCompletionListener {
        override fun onCompletion(mp: MediaPlayer) {
            Timber.d("Single Sound completed")
            if (userCallback != null) {
                userCallback.onCompletion(mp)
            } else {
                releaseSound()
            }
        }
    }

    /**
     * Class used to play all sounds for a given card side
     */
    private inner class PlayAllCompletionListener(
        /**
         * Question/Answer
         */
        private val qa: SoundSide,
        private val errorListener: OnErrorListener?
    ) : OnCompletionListener {
        /**
         * next sound to play (onCompletion() is first called after the first (0) has been played)
         */
        private var mNextToPlay = 1
        override fun onCompletion(mp: MediaPlayer) {
            // If there is still more sounds to play for the current card, play the next one
            if (mSoundPaths.containsKey(qa) && mNextToPlay < mSoundPaths[qa]!!.size) {
                Timber.i("Play all: Playing next sound")
                playSound(mSoundPaths[qa]!![mNextToPlay++], this, null, errorListener)
            } else {
                Timber.i("Play all: Completed - releasing sound")
                releaseSound()
            }
        }
    }

    /**
     * Releases the sound.
     */
    private fun releaseSound() {
        Timber.d("Releasing sounds and abandoning audio focus")
        if (mMediaPlayer != null) {
            // Required to remove warning: "mediaplayer went away with unhandled events"
            // https://stackoverflow.com/questions/9609479/android-mediaplayer-went-away-with-unhandled-events
            mMediaPlayer!!.reset()
            mMediaPlayer!!.release()
            mMediaPlayer = null
        }
        if (mAudioManager != null) {
            // mAudioFocusRequest was initialised for API 26 and above in playSoundInternal().
            CompatHelper.compat.abandonAudioFocus(mAudioManager!!, afChangeListener, mAudioFocusRequest)
            mAudioManager = null
        }
    }

    /**
     * Stops the playing sounds.
     */
    fun stopSounds() {
        if (mMediaPlayer != null) {
            mMediaPlayer!!.stop()
            releaseSound()
        }
        ReadText.stopTts()
    }

    /**
     * Set the context for the calling activity (necessary for playing videos)
     */
    fun setContext(activityRef: WeakReference<Activity?>?) {
        mCallingActivity = activityRef
    }

    fun hasQuestion(): Boolean {
        return mSoundPaths.containsKey(SoundSide.QUESTION)
    }

    fun hasAnswer(): Boolean {
        return mSoundPaths.containsKey(SoundSide.ANSWER)
    }

    fun interface OnErrorListener {
        fun onError(mp: MediaPlayer?, which: Int, extra: Int, path: String?): Boolean
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

        /**
         * OnCompletionListener so that external video player can notify to play next sound
         */
        var mediaCompletionListener: OnCompletionListener? = null
            private set

        /**
         * Whitelist for video extensions
         */
        private val VIDEO_WHITELIST = arrayOf("3gp", "mp4", "webm", "mkv", "flv")

        /**
         * Listener to handle audio focus. Currently blank because we're not respecting losing focus from other apps.
         */
        private val afChangeListener = OnAudioFocusChangeListener { }

        /** Extract SoundOrVideoTag instances from content where sound tags are in the form: [sound:filename.mp3]  */
        @CheckResult
        @KotlinCleanup("non-null param")
        fun extractTagsFromLegacyContent(content: String?): List<SoundOrVideoTag> {
            val matcher = SOUND_PATTERN.matcher(content!!)
            // While there is matches of the pattern for sound markers
            val ret: MutableList<SoundOrVideoTag> = ArrayList()
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
