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

package com.ichi2.anki.common.time

import com.ichi2.anki.common.annotations.NeedsTest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration

const val SECONDS_PER_DAY = 86400L

// These are doubles on purpose because we want a rounded, not integer result later.
// Use values from Anki Desktop:
// https://github.com/ankitects/anki/blob/05cc47a5d3d48851267cda47f62af79f468eb028/rslib/src/sched/timespan.rs#L83
const val TIME_MINUTE = 60.0 // seconds
const val TIME_HOUR = 60.0 * TIME_MINUTE
const val TIME_DAY = 24.0 * TIME_HOUR
// private const val TIME_MONTH = 30.0 * TIME_DAY
// private const val TIME_YEAR = 12.0 * TIME_MONTH

fun getTimestamp(time: Time): String = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(time.currentDate)

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

fun getDayStart(time: Time): Long {
    val cal = time.calendar()
    if (cal[Calendar.HOUR_OF_DAY] < 4) {
        // Changed from roll() to add() to handle year boundaries correctly (e.g., Jan 1 -> Dec 31)
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    cal[Calendar.HOUR_OF_DAY] = 4
    cal[Calendar.MINUTE] = 0
    cal[Calendar.SECOND] = 0
    cal[Calendar.MILLISECOND] = 0
    return cal.timeInMillis
}
