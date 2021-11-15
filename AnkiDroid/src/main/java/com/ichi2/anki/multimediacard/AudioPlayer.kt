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

import android.media.MediaPlayer
import timber.log.Timber
import java.io.IOException
import java.lang.Exception
import kotlin.Throws

class AudioPlayer {
    private var mPlayer: MediaPlayer? = null
    private var mOnStoppingListener: Runnable? = null
    private var mOnStoppedListener: Runnable? = null

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
        if (mOnStoppedListener == null) {
            return
        }
        mOnStoppedListener!!.run()
    }

    private fun onStopping() {
        if (mOnStoppingListener == null) {
            return
        }
        mOnStoppingListener!!.run()
    }

    fun start() {
        mPlayer!!.start()
    }

    fun stop() {
        try {
            mPlayer!!.prepare()
            mPlayer!!.seekTo(0)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun pause() {
        mPlayer!!.pause()
    }

    fun setOnStoppingListener(listener: Runnable?) {
        mOnStoppingListener = listener
    }

    fun setOnStoppedListener(listener: Runnable?) {
        mOnStoppedListener = listener
    }
}
