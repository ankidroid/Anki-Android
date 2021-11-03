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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Allows injection of time dependencies */
public abstract class Time {

    /** Date of this time */
    public Date getCurrentDate() {
        return new Date(intTimeMS());
    }

    /**The time in integer seconds. */
    public long intTime() {
        return intTimeMS() / 1000L;
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

    public static Calendar calendar(long timeInMS) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMS);
        return calendar;
    }

    public static GregorianCalendar gregorianCalendar(long timeInMS) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(timeInMS);
        return calendar;
    }


    /**
     * Calculate the UTC offset
     */
    public static double utcOffset() {
        // Okay to use real time, as the result does not depends on time at all here
        Calendar cal = Calendar.getInstance();
        // 4am
        return 4 * 60 * 60 - (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 1000;
    }




    /**
     *  Returns the effective date of the present moment.
     *  If the time is prior the cut-off time (9:00am by default as of 11/02/10) return yesterday,
     *  otherwise today
     *  Note that the Date class is java.sql.Date whose constructor sets hours, minutes etc to zero
     *
     * @param utcOffset The UTC offset in seconds we are going to use to determine today or yesterday.
     * @return The date (with time set to 00:00:00) that corresponds to today in Anki terms
     */
    public java.sql.Date genToday(double utcOffset) {
        // The result is not adjusted for timezone anymore, following libanki model
        // Timezone adjustment happens explicitly in Deck.updateCutoff(), but not in Deck.checkDailyStats()
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Calendar cal = Time.gregorianCalendar( intTimeMS()- (long) utcOffset * 1000L);
        return java.sql.Date.valueOf(df.format(cal.getTime()));
    }
}
