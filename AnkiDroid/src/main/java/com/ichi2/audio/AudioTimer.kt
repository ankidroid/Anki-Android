/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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
package com.ichi2.audio

import android.os.Handler
import android.os.Looper

class AudioTimer(listener: OnTimerTickListener) {
    private var handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable

    private var duration = 0L
    private var delay = 100L
    init {
        runnable = object : Runnable {
            override fun run() {
                duration += delay
                handler.postDelayed(this, delay)
                listener.onTimerTick(formatTime())
            }
        }
    }

    fun start() {
        handler.postDelayed(runnable, delay)
    }

    fun pause() {
        handler.removeCallbacks(runnable)
    }

    fun stop() {
        handler.removeCallbacks(runnable)
        duration = 0L
    }

    fun start(customDuration: Long) {
        handler.removeCallbacks(runnable)
        duration = customDuration
        handler.postDelayed(runnable, delay)
    }

    fun formatTime(): String {
        val ms = duration % 1000
        val s = (duration / 1000) % 60
        val m = (duration / (1000 * 60)) % 60
        val h = (duration / (1000 * 60 * 60)) % 60
        return if (h > 0) {
            "%02d:%02d:%02d:%02d".format(h, m, s, ms / 10)
        } else {
            "%02d:%02d:%02d".format(m, s, ms / 10)
        }
    }

    fun formatTime(time: Long): String {
        val ms = time % 1000
        val s = (time / 1000) % 60
        val m = (time / (1000 * 60)) % 60
        val h = (time / (1000 * 60 * 60)) % 60
        return if (h > 0) {
            "%02d:%02d:%02d:%02d".format(h, m, s, ms / 10)
        } else {
            "%02d:%02d:%02d".format(m, s, ms / 10)
        }
    }

    interface OnTimerTickListener {
        fun onTimerTick(duration: String)
    }
}
