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
import com.ichi2.anki.utils.postDelayed
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * AudioTimer class is a utility for managing timing operations when playing audio.
 * It includes a Handler for scheduling tasks, and a Runnable for incrementing the duration and
 * triggering a callback to a listener at regular intervals.
 * [OnTimerTickListener.onTimerTick] notifies components about the timer's progress.
 **/
class AudioTimer(listener: OnTimerTickListener) {
    private var handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable

    private var duration = 0.milliseconds
    private var delay = 50.milliseconds
    init {
        runnable = object : Runnable {
            override fun run() {
                duration += delay
                handler.postDelayed(this, delay)
                listener.onTimerTick(duration)
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
        duration = 0.milliseconds
    }

    fun start(customDuration: Duration) {
        handler.removeCallbacks(runnable)
        duration = customDuration
        handler.postDelayed(runnable, delay)
    }

    interface OnTimerTickListener {
        fun onTimerTick(duration: Duration)
    }
}
