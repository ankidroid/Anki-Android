/*
 *  Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 *  Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 *  Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 *  Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
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
package com.ichi2.anki.multimediacard

import com.ichi2.anki.multimediacard.MediaPlayer.MediaPlayerState.*
import com.ichi2.audio.AudioRecordingController.Companion.DEFAULT_TIME
import timber.log.Timber
import java.io.IOException

class AudioPlayer {
    private var mPlayer: MediaPlayer? = null

    var onStoppingListener: (() -> Unit)? = null
    var onStoppedListener: (() -> Unit)? = null

    @Throws(IOException::class)
    fun play(audioPath: String?) {
        mPlayer = MediaPlayer()
        mPlayer!!.setDataSource(audioPath)
        mPlayer!!.setOnCompletionListener {
            onStopping()
            mPlayer!!.stop()
            onStopped()
        }
        mPlayer!!.prepare()
        mPlayer!!.start()
    }

    private fun onStopped() {
        onStoppedListener?.invoke()
    }

    private fun onStopping() {
        onStoppingListener?.invoke()
    }

    fun start() {
        if (arrayOf(INITIALIZED, STOPPED).contains(mPlayer!!.state)) {
            mPlayer!!.prepare()
        }
        mPlayer!!.start()
    }

    fun stop() {
        try {
            mPlayer!!.stop()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun pause() {
        mPlayer!!.pause()
    }

    fun prepareAudioPlayer(audioPath: String, onPrepared: (String) -> Unit, onCompletion: () -> Unit) {
        mPlayer = MediaPlayer()
        mPlayer!!.apply {
            setDataSource(audioPath)
            setOnPreparedListener {
                onPrepared.invoke(DEFAULT_TIME)
            }
            setOnCompletionListener {
                onCompletion.invoke()
            }
            prepareAsync()
        }
    }

    fun isAudioPlaying(): Boolean {
        return mPlayer!!.isPlaying
    }

    fun duration(): Int {
        return mPlayer!!.duration
    }

    fun startPlayer() {
        mPlayer!!.start()
    }

    fun audioSeekTo(sec: Int) {
        mPlayer!!.seekTo(sec)
    }

    fun currentPosition(): Int {
        return mPlayer!!.currentPosition
    }
}

interface OnAudioCompletionListener {
    fun onCompletion()
}
