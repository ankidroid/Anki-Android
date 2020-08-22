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

package com.ichi2.libanki.utils;

import com.ichi2.libanki.DB;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/** Allows injection of time dependencies */
public abstract class Time {

    /** Date of this time */
    public Date getCurrentDate() {
        return new Date(intTimeMS());
    }

    /**The time in integer seconds. */
    public long intTime() {
        return intTimeMS() / 1000L;
    };

    /**The time in milisecond. */
    public double now() {
        return (double) intTimeMS();
    }

    public abstract long intTimeMS();

    /** Calendar for this date */
    public Calendar calendar() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(getCurrentDate());
        return cal;
    }

    /** Gregorian calendar for this date */
    public GregorianCalendar gregorianCalendar() {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(getCurrentDate());
        return cal;
    }

    /** Return a non-conflicting timestamp for table. */
    public long timestampID(DB db, String table) {
        // be careful not to create multiple objects without flushing them, or they
        // may share an ID.
        long t = intTimeMS();
        while (db.queryScalar("SELECT id FROM " + table + " WHERE id = ?", t) != 0) {
            t += 1;
        }
        return t;
    }

    /** Return the first safe ID to use. */
    public long maxID(DB db) {
        long now = intTimeMS();
        now = Math.max(now, db.queryLongScalar("SELECT MAX(id) FROM cards"));
        now = Math.max(now, db.queryLongScalar("SELECT MAX(id) FROM notes"));
        return now + 1;
    }
}
