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

package com.ichi2.anki.cardviewer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.ensureActive
import com.ichi2.libanki.SoundOrVideoTag
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Player for the sounds of [SoundOrVideoTag] */
class SoundTagPlayer(private val soundUriBase: String) {
    private var mediaPlayer: MediaPlayer? = null

    private val music = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /**
     * AudioManager to request/release audio focus
     */
    private var audioManager: AudioManager =
        AnkiDroidApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // the same instance of an AudioFocusRequestCompat must be used to cancel focus
    private val audioFocusRequest: AudioFocusRequestCompat by lazy {
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setOnAudioFocusChangeListener { }
            .build()
    }

    /**
     * @throws SoundException if the file does not exist, or if media playing fails
     * @param soundErrorListener handles a sound error and returns how to continue playing sounds
     */
    suspend fun play(
        tag: SoundOrVideoTag,
        soundErrorListener: SoundErrorListener
    ) = suspendCancellableCoroutine { continuation ->
        Timber.d("Playing SoundOrVideoTag")
        requireNewMediaPlayer().apply {
            continuation.invokeOnCancellation {
                Timber.i("stopping MediaPlayer due to cancellation")
                stopSounds()
            }
            setOnCompletionListener {
                Timber.v("finished playing SoundOrVideoTag successfully")
                abandonAudioFocus()
                // guard against a potential issue: task cancellation
                if (!continuation.isCompleted) {
                    continuation.resume(Unit)
                }
            }

            setAudioAttributes(music)
            setOnErrorListener { mp, what, extra ->
                Timber.w("Media error %d", what)
                abandonAudioFocus()
                val continuationBehavior =
                    soundErrorListener.onMediaPlayerError(mp, what, extra, tag)
                // 15103: setOnErrorListener can be invoked after task cancellation
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(SoundException(continuationBehavior))
                }
                true // do not call onCompletionListen
            }

            val soundUri = Uri.parse(soundUriBase + tag.filename)
            try {
                awaitSetDataSource(soundUri)
            } catch (e: Exception) {
                continuation.ensureActive()
                val continuationBehavior = soundErrorListener.onError(soundUri)
                val exception = SoundException(continuationBehavior, e)
                return@suspendCancellableCoroutine continuation.resumeWithException(exception)
            }

            requestAudioFocus()
            continuation.ensureActive()
            Timber.d("starting sound tag")
            start()
        }
    }

    /**
     * Releases the sound.
     */
    fun releaseSound() {
        Timber.d("Releasing sounds and abandoning audio focus")
        mediaPlayer?.let {
            // Required to remove warning: "mediaplayer went away with unhandled events"
            // https://stackoverflow.com/questions/9609479/android-mediaplayer-went-away-with-unhandled-events
            it.reset()
            it.release()
            mediaPlayer = null
        }
        abandonAudioFocus()
    }

    fun stopSounds() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Timber.w(e, "stopSounds()")
        }
        abandonAudioFocus()
    }

    /**
     * Produces a usable [MediaPlayer], either creating a new instance or resetting the current
     * instance
     */
    private fun requireNewMediaPlayer(): MediaPlayer {
        if (mediaPlayer == null) {
            Timber.d("Creating media player for playback")
            // PERF: see if this is slow, maybe move to a task on instantiation
            mediaPlayer = MediaPlayer()
        } else {
            Timber.d("Resetting media for playback")
            mediaPlayer!!.reset()
        }
        return mediaPlayer!!
    }

    /**
     * @throws IllegalStateException if the sound player is in an invalid state
     * @throws java.io.FileNotFoundException file is not found
     * @throws java.io.IOException: Prepare failed.: status=0x1
     */
    private fun MediaPlayer.awaitSetDataSource(uri: Uri) {
        setDataSource(AnkiDroidApp.instance.applicationContext, uri)
        prepare()
    }

    private fun requestAudioFocus() {
        Timber.d("Requesting audio focus")
        AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
    }

    private fun abandonAudioFocus() {
        Timber.d("Abandoning audio focus")
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }
}
