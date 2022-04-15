/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.testutils

import android.annotation.SuppressLint
import com.ichi2.libanki.utils.Time
import com.ichi2.utils.KotlinCleanup
import java.util.*
import kotlin.jvm.JvmOverloads

@KotlinCleanup("IDE lint")
open class MockTime : Time {
    /** Number of milliseconds between each call.  */
    private val mStep: Int

    /** Time since epoch in MS.  */
    protected var time: Long
        private set
    /** A clock at time Time, each call advance by step ms. */
    /** A clock at time Time, only changed explicitly */
    @JvmOverloads
    constructor(time: Long, step: Int = 0) {
        this.time = time
        mStep = step
    }

    /** create a mock time whose initial value is this date. Month is 0-based, in order to stay close to calendar. MS are 0. */
    constructor(
        year: Int,
        month: Int,
        date: Int,
        hourOfDay: Int,
        minute: Int,
        second: Int,
        milliseconds: Int,
        step: Int
    ) {
        time = timeStamp(year, month, date, hourOfDay, minute, second, milliseconds)
        mStep = step
    }

    /** Time in millisecond since epoch.  */
    override fun intTimeMS(): Long {
        val time = time
        this.time += mStep.toLong()
        return time
    }

    /** Add ms milliseconds  */
    fun addMs(ms: Long) {
        time += ms
    }

    /** add s seconds  */
    fun addS(s: Long) {
        addMs(s * 1000L)
    }

    /** add m minutes  */
    fun addM(m: Long) {
        addS(m * 60)
    }

    /** add h hours */
    fun addH(h: Long) {
        addM(h * 60)
    }

    /** add d days */
    fun addD(d: Long) {
        addH(d * 24)
    }

    companion object {
        /**
         * Allow to get a timestamp which is independent of place where test occurs. MS are set to 0
         * @param year Year
         * @param month Month, 0-based
         * @param date, day of month
         * @param hourOfDay, hour, from 0 to 23
         * @param minute, from 0 to 59
         * @param second, From 0 to 59
         * @return the time stamp of this instant in GMT calendar
         */
        @JvmStatic
        @KotlinCleanup("After Kotlin Conversion, use default argument and remove this")
        fun timeStamp(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int, second: Int): Long {
            return timeStamp(year, month, date, hourOfDay, minute, second, 0)
        }

        /**
         * Allow to get a timestamp which is independent of place where test occurs.
         * @param year Year
         * @param month Month, 0-based
         * @param date, day of month
         * @param hourOfDay, hour, from 0 to 23
         * @param minute, from 0 to 59
         * @param second, From 0 to 59
         * @param milliseconds, from 0 to 999
         * @return the time stamp of this instant in GMT calendar
         */
        @JvmStatic
        @SuppressLint("DirectGregorianInstantiation")
        fun timeStamp(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int, second: Int, milliseconds: Int): Long {
            val timeZone = TimeZone.getTimeZone("GMT")
            val gregorianCalendar: Calendar = GregorianCalendar(year, month, date, hourOfDay, minute, second)
            gregorianCalendar.timeZone = timeZone
            gregorianCalendar[Calendar.MILLISECOND] = milliseconds
            return gregorianCalendar.timeInMillis
        }
    }
}
