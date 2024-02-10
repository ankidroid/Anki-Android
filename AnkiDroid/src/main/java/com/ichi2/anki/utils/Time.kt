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
import com.ichi2.anki.R
import com.ichi2.libanki.utils.Time
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val SECONDS_PER_DAY = 86400L

private const val TIME_MINUTE_LONG: Long = 60 // seconds
private const val TIME_HOUR_LONG = 60 * TIME_MINUTE_LONG
private const val TIME_DAY_LONG = 24 * TIME_HOUR_LONG

// These are doubles on purpose because we want a rounded, not integer result later.
// Use values from Anki Desktop:
// https://github.com/ankitects/anki/blob/05cc47a5d3d48851267cda47f62af79f468eb028/rslib/src/sched/timespan.rs#L83
private const val TIME_MINUTE = 60.0 // seconds
private const val TIME_HOUR = 60.0 * TIME_MINUTE
private const val TIME_DAY = 24.0 * TIME_HOUR
private const val TIME_MONTH = 30.0 * TIME_DAY
private const val TIME_YEAR = 12.0 * TIME_MONTH

/**
 * Return a string representing how much time remains
 *
 * @param context The application's environment.
 * @param time_s The time to format, in seconds
 * @return The time quantity string. Something like "3 minutes left" or "2 hours left".
 */
fun remainingTime(context: Context, time_s: Long): String {
    val time_x: Int // Time in unit x
    val remaining_seconds: Int // Time not counted in the number in unit x
    val remaining: Int // Time in the unit smaller than x
    val res = context.resources
    return if (time_s < TIME_HOUR_LONG) {
        // get time remaining, but never less than 1
        time_x = max(
            (time_s / TIME_MINUTE).roundToInt(),
            1
        )
        res.getQuantityString(R.plurals.reviewer_window_title, time_x, time_x)
        // It used to be minutes only. So the word "minutes" is not
        // explicitly written in the ressource name.
    } else if (time_s < TIME_DAY_LONG) {
        time_x = (time_s / TIME_HOUR_LONG).toInt()
        remaining_seconds = (time_s % TIME_HOUR_LONG).toInt()
        remaining =
            (remaining_seconds.toFloat() / TIME_MINUTE).roundToInt()
        res.getQuantityString(
            R.plurals.reviewer_window_title_hours_new,
            time_x,
            time_x,
            remaining
        )
    } else {
        time_x = (time_s / TIME_DAY_LONG).toInt()
        remaining_seconds = (time_s.toFloat() % TIME_DAY_LONG).toInt()
        remaining =
            (remaining_seconds / TIME_HOUR).roundToInt()
        res.getQuantityString(
            R.plurals.reviewer_window_title_days_new,
            time_x,
            time_x,
            remaining
        )
    }
}

/**
 * Return a proper string for a time value in seconds
 *
 * Similar to Anki anki/utils.py's fmtTimeSpan.
 *
 * @param context The application's environment.
 * @param time_s The time to format, in seconds
 * @return The formatted, localized time string. The time is always a float. E.g. "27.0 days"
 */
fun roundedTimeSpanUnformatted(context: Context, time_s: Long): String {
    // As roundedTimeSpan, but without tags; for place where you don't use HTML
    return roundedTimeSpan(context, time_s).replace("<b>", "").replace("</b>", "")
}

/**
 * Return a proper string for a time value in seconds
 *
 * Similar to Anki anki/utils.py's fmtTimeSpan.
 *
 * @param context The application's environment.
 * @param time_s The time to format, in seconds
 * @return The formatted, localized time string. The time is always a float. E.g. "**27.0** days"
 */
fun roundedTimeSpan(context: Context, time_s: Long): String {
    return if (abs(time_s) < TIME_DAY) {
        context.resources.getString(
            R.string.stats_overview_hours,
            time_s / TIME_HOUR
        )
    } else if (abs(time_s) < TIME_MONTH) {
        context.resources.getString(
            R.string.stats_overview_days,
            time_s / TIME_DAY
        )
    } else if (abs(time_s) < TIME_YEAR) {
        context.resources.getString(
            R.string.stats_overview_months,
            time_s / TIME_MONTH
        )
    } else {
        context.resources.getString(
            R.string.stats_overview_years,
            time_s / TIME_YEAR
        )
    }
}

fun getTimestamp(time: Time): String {
    return SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(time.currentDate)
}

/** @see Handler.postDelayed */
fun Handler.postDelayed(runnable: Runnable, delay: Duration) =
    this.postDelayed(runnable, delay.inWholeMilliseconds)

/** Gets the current playback position */
val MediaPlayer.elapsed get() = this.currentPosition.milliseconds

/** Formats the time as '00:00.00' (m:s:ms), OR 00:00:00.00 (h:m:s.ms) */
fun Duration.formatAsString(): String {
    val milliseconds = this.inWholeMilliseconds
    val ms = milliseconds % 1000
    val s = (milliseconds / 1000) % 60
    val m = (milliseconds / (1000 * 60)) % 60
    val h = (milliseconds / (1000 * 60 * 60)) % 60
    return if (h > 0) {
        "%02d:%02d:%02d.%02d".format(h, m, s, ms / 10)
    } else {
        "%02d:%02d.%02d".format(m, s, ms / 10)
    }
}
