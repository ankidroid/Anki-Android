/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.utils

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.view.View
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TIME_HOUR
import com.ichi2.anki.common.time.TIME_MINUTE
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TIME_MINUTE_LONG: Long = 60 // seconds
private const val TIME_HOUR_LONG = 60 * TIME_MINUTE_LONG
private const val TIME_DAY_LONG = 24 * TIME_HOUR_LONG

/**
 * Return a string representing how much time remains
 *
 * @param context The application's environment.
 * @param time_s The time to format, in seconds
 * @return The time quantity string. Something like "3 minutes left" or "2 hours left".
 */
fun remainingTime(
    context: Context,
    time_s: Long,
): String {
    val timeX: Int // Time in unit x
    val remainingSeconds: Int // Time not counted in the number in unit x
    val remaining: Int // Time in the unit smaller than x
    val res = context.resources
    return if (time_s < TIME_HOUR_LONG) {
        // get time remaining, but never less than 1
        timeX =
            max(
                (time_s / TIME_MINUTE).roundToInt(),
                1,
            )
        res.getQuantityString(R.plurals.reviewer_window_title, timeX, timeX)
        // It used to be minutes only. So the word "minutes" is not
        // explicitly written in the ressource name.
    } else if (time_s < TIME_DAY_LONG) {
        timeX = (time_s / TIME_HOUR_LONG).toInt()
        remainingSeconds = (time_s % TIME_HOUR_LONG).toInt()
        remaining =
            (remainingSeconds.toFloat() / TIME_MINUTE).roundToInt()
        res.getQuantityString(
            R.plurals.reviewer_window_title_hours_new,
            timeX,
            timeX,
            remaining,
        )
    } else {
        timeX = (time_s / TIME_DAY_LONG).toInt()
        remainingSeconds = (time_s.toFloat() % TIME_DAY_LONG).toInt()
        remaining =
            (remainingSeconds / TIME_HOUR).roundToInt()
        res.getQuantityString(
            R.plurals.reviewer_window_title_days_new,
            timeX,
            timeX,
            remaining,
        )
    }
}

/** @see Handler.postDelayed */
fun Handler.postDelayed(
    runnable: Runnable,
    delay: Duration,
) = this.postDelayed(runnable, delay.inWholeMilliseconds)

/** @see View.postDelayed */
fun View.postDelayed(
    action: Runnable,
    delay: Duration,
) {
    postDelayed(action, delay.inWholeMilliseconds)
}

/** Gets the current playback position */
val MediaPlayer.elapsed get() = this.currentPosition.milliseconds
