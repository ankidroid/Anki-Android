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

package com.ichi2.testutils;

import android.annotation.SuppressLint;

import com.ichi2.libanki.utils.Time;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class MockTime extends Time {

    /** Number of miliseconds between each call. */
    private final int mStep;
    /** Time since epoch in MS. */
    private long mTime;

    /** A clock at time Time, only changed explicitly*/
    public MockTime(long time) {
        this(time, 0);
    }

    /** A clock at time Time, each call advance by step ms.*/
    public MockTime(long time, int step) {
        this.mTime = time;
        this.mStep = step;
    }

    /** create a mock time whose initial value is this date. Month is 0-based, in order to stay close to calendar. MS are 0.*/
    public MockTime(int year, int month, int date, int hourOfDay, int minute,
                    int second, int milliseconds, int step) {
        mTime = timeStamp(year, month, date, hourOfDay, minute, second, milliseconds);
        mStep = step;
    }

    /** Time in milisecond since epoch. */
    @Override
    public long intTimeMS() {
        long time = this.mTime;
        this.mTime += mStep;
        return time;
    }

    protected long getTime() {
        return mTime;
    }

    /** Add ms milisecond*/
    public void addMs(long ms) {
        mTime += ms;
    }

    /** add s seconds */
    public void addS(long s) {
        addMs(s * 1000L);
    }

    /** add m minutes */
    public void addM(long m) {
        addS(m * 60);
    }

    /** add h hours*/
    public void addH(long h) {
        addM(h * 60);
    }

    /** add d days*/
    public void addD(long d) {
        addH(d * 24);
    }


    /**
     * Allow to get a timestamp which is independant of place where test occurs. MS are set to 0
     * @param year Year
     * @param month Month, 0-based
     * @param date, day of month
     * @param hourOfDay, hour, from 0 to 23
     * @param minute, from 0 to 59
     * @param second, From 0 to 59
     * @return the time stamp of this instant in GMT calendar
     */
    public static long timeStamp(int year, int month, int date, int hourOfDay, int minute, int second) {
        return timeStamp(year, month, date, hourOfDay, minute, second, 0);
    }


    /**
     * Allow to get a timestamp which is independant of place where test occurs.
     * @param year Year
     * @param month Month, 0-based
     * @param date, day of month
     * @param hourOfDay, hour, from 0 to 23
     * @param minute, from 0 to 59
     * @param second, From 0 to 59
     * @param miliseconds, from 0 to 999
     * @return the time stamp of this instant in GMT calendar
     */
    @SuppressLint("DirectGregorianInstantiation")
    public static long timeStamp(int year, int month, int date, int hourOfDay, int minute, int second, int miliseconds) {
        TimeZone timeZone = TimeZone.getTimeZone("GMT");
        Calendar gregorianCalendar = new GregorianCalendar(year, month, date, hourOfDay, minute, second);
        gregorianCalendar.setTimeZone(timeZone);
        gregorianCalendar.set(Calendar.MILLISECOND, miliseconds);
        return (gregorianCalendar.getTimeInMillis());
    }
}
