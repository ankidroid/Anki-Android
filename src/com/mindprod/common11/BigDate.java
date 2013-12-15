/*
 * @(#)BigDate.java
 *
 * Summary: Manipulate pure dates, without time or timezone.
 *
 * Copyright: (c) 1997-2010 Roedy Green, Canadian Mind Products, http://mindprod.com
 *
 * Licence: This software may be copied and used freely for any purpose but military.
 *          http://mindprod.com/contact/nonmil.html
 *
 * Requires: JDK 1.1+
 *
 * Created with: IntelliJ IDEA IDE.
 *
 * Version History:
 *  1.0 1997-05-03 - initial release.
 *  1.1 1997-05-04 - add setOrdinal, setYYYY, setMM, setDD.
 *  1.2 1997-05-05 - add public static toOrdinal.
 *  1.3 1997-05-06 - add getTimeStamp, warn about lead 0 meaning octal
 *  1.4 1997-05-06 - add getDayOfWeek, getDDD, rename NULL_ORDINAL, NULL_TIMESTAMP
 *  1.5 1997-05-11 - rename getJulian to getOrdinal to avoid confusion with
 *                   old Julian vs new Gregorian calendars. Redo 1582 code with named
 *                   constants to facilitate changing the switchover point between the old and
 *                   new calendars.
 *  1.6 1997-05-12 - shorten the names of constants.
 *  1.7 1997-06-21 - cache last result to avoid recalculation.
 *  1.8 1997-07-09 - add null constructor
 *  1.9 1997-12-17 - add documentation on how to convert to the British
 *                   calendar. Modify code to make it simple to switch to the British scheme.
 *                   Allow for fact British 100 and 400 Leap year rules kicked in at different
 *                   times. Add today method. Calculate constants based on others rather than
 *                   specifying individually to make change easier. expose default methods to
 *                   extenders of this class, making them protected. Fix bug. Null constructor
 *                   was not creating a properly initialised NULL date. There was confusion
 *                   then between a null date and a zero date = 1997-01-01.
 *  2.0 1998-01-26 - Peter V. Gadjokov" <pvg@infoscape.com> pointed out a
 *                   bug in isValid. The dd was not tested properly to make sure it was <= 31
 *                   The bug did not cause incorrect execution, just slowed it, since a finer
 *                   check was done later.
 *  2.1 1998-01-30 - implement Cloneable, Serializable, equals, compareTo,
 *                   hashCode add Javadoc comments. isBritish compile time switch.
 *                   alphabetise. private, protected status changed.
 *  2.2 1998-06-18 - add getISOWeekNumber, getISODayOfWeek.
 *  2.3 1998-11-19 - new mailing address and phone.
 *  2.4 1998-11-27 - more examples on how to use BigDate in TestDate.
 *                   warnings about today and time zones. today is now two methods localToday
 *                   and UTCToday. added age method to compute age in years, months and days.
 *                   exposed daysInMonth as public. made most methods final for speed. Since
 *                   you have source, you can always unfinal them if you need to.
 *  2.5 1998-11-28 - added test harness to prove age routine is working
 *                   correctly. Note about ISO 8601:1988 international standard for dates is
 *                   YYYY-MM-DD. Corrected major bugs in age. Now works for mixed BC/AD. Works
 *                   if as of date is not today. More comments in age routine on how it works.
 *                   Now accepts two BigDates instead of two ordinals. Added more examples of
 *                   how to use BigDate.
 *  2.6 1998-11-30 - Added more examples to TestDate.
 *  2.7 1999-08-23 - getTimeStamp renamed to getUTCTimeStamp add
 *                   getLocalTimeStamp, getUTCDate, getLocalDate today(TimeZone)
 *  2.8 1999-09-04 - add daysInMonth to take the year instead of a
 *                   boolean. That way you can usually avoid the isLeap calculation.
 *  2.9 1999-09-08 - add sample isHolidayimplementationn to TestDate. add
 *                   dayOfWeek and isoDayOfWeek static versions.
 *  3.0 1999-09-08 - add nthXXXDay and ordinalOfnthXXXDay.
 *  3.1 1999-09-15 - add constructor and access for Astronomer's
 *                   Proleptic Julian day numbers. to be consistent with the US Naval
 *                   observatory, BC leap years are now 1 5 9, not 4, 8, 12.
 *  3.2 1999-09-20 - add getWeekNumber to complement getISOWeekNumber.
 *  3.3 1999-09-22 - correct toString to display very old or very future
 *                   dates correctly
 *  3.4 1999-10-18 - speed up leap year calcs by replacing yyyy % 4 with
 *                   yyyy & 3
 *  3.5 1999-11-23 - give Timezone.getOffset a 1-based Sunday. Avoid
 *                   IllegalArgumentException in some JVMs. Place warnings about Sunday base
 *                   incompatibilities.
 *  3.6 1999-11-24 - add getCalendarDayOfWeek and calendarDayOfWeek
 *  3.7 2001-01-26 - properly implement Comparable with Object instead of
 *                   BigDate
 *  3.8 2001-03-17 - add code to TestDate to calculate how many sleeps until
 *                   Christmas.
 *  3.9 2002-03-16 - make toString non-final so you can override it.
 *  4.0 2002-04-10 - addDays convenience method toString now displays ISO
 *                   format yyyy-mm-dd TestDate examples changed to use addDays.
 *  4.1 2003-01-01
 *  4.2 2003-01-01
 *  4.3 2003-05-18 - Default is now British rather than Pope Gregory's rules
 *                   for when the missing days were dealt with.
 *  4.4 2003-08-05 - getDowMMDDYY -- alternative to toString, mainly to show
 *                   how to roll your own methods.
 *  4.5 2003-09-12 - Added constructor that takes a Date and TimeZone. added
 *                   setDateAtTime.
 *  4.6 2004-05-15 - getSeason
 *  4.7 2005-07-15 - isValid( yyyy-mm-dd), new package, com.mindprod.common11,
 *                   getCopyright
 *  4.8 2005-08-28 - added constructor that takes a String
 *                   argument.
 *  4.9 2006-03-04 - convert to Intellij. Make code more robust by moving
 *                   initialisation to a static block so field reordering will not screw it up.
 *  5.0 2007-10-15 - new methods, nearestXXXDay, dayOfWeek, calendarDayOfWeek.
 *                   Improved documentation.
 *  5.1 2008-02-01 - add named constants for the months and days of the week.
 *  5.2 2008-03-20 - add dayAbbr, dayName, monthAbbr, monthName
 *  5.3 2008-12-15 - toString now handles BC and non-4-digit years. toYYYYString, Constructor handles BC and non-4-digit years.
 *  5.4 2009-04-29 - add parseYYYYmmdd to parse incomplete date strings.
 *  5.5 2009-05-05 - add isAnniversary
 *  5.6 2009-12-09 - parsing of String dates is now more relaxed. Now ignores spaces, commas. Accepts single digit months and days.
 */

package com.mindprod.common11;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.TimeZone;

/**
 * Manipulate pure dates, without time or timezone.
 * <p/>
 * Convert Gregorian YYYY MM DD back and forth to ordinal days since 1970-01-01, Thursday (sometimes called Julian or
 * datestamp form). BigDate objects are not designed for <b>storing</b> dates in a database, just for conversion. Long
 * term storage should store the ordinal either as an int, or possibly as a short. The BigDate constructor stores the
 * date both in ordinal and Gregorian forms internally. If you store one, it creates the other.
 * <p/>
 * The standard Sun Date won't handle dates prior to 1970 among other problems. BigDate handles dates 999,999 BC Jan 1
 * to 999,999 AD Dec 31, 0 = 1970-01-01.
 * <p/>
 * Are the following quirks of the calendar considered in this code?
 * <p/>
 * 1900 is not a leap year (mod 100): <b>yes</b>.
 * <p/>
 * 2000 is a leap year (mod 400): <b>yes</b>.
 * <p/>
 * The 10 missing days in 1582 October.: <b>yes</b> (Pope Gregory's correction) 1582-10-05 to 1582-10-14 never happened.
 * <p/>
 * Britain and its territories (including the USA and Canada) adopted the Gregorian correction in 1752: <b>Yes</b>. By
 * then, 11 days had to be dropped. 1752-09-03 to 1752-09-13 never happened. However, you can modify constants in
 * BigDate to use the British calendar. Such a change only affects dates prior to 1753 since BigDate calendar is based
 * on 1970-01-01. toOrdinal with the Gregorian and British scheme will give the same number for recent dates. <b>You
 * must recompile BigDate with the <b>isBritish</b> boolean changed to true.</b>. It was used by Britain and its
 * colonies which later became the USA and Canada. However Nova Scotia used Pope Gregory's calendar. see
 * http://mindprod.com/jgloss/missingdays.html PLEASE CONFIGURE isBritish AND RECOMPILE BigDate IF YOU WISH TO USE THE
 * BRITISH CALENDAR.
 * <p/>
 * missing year 0 between 1 BC and 1 AD. <b>yes</b>.
 * <p/>
 * in Roman times leap years occurred at irregular intervals, Considered inauspicious, they were avoided during war.
 * <b>no</b>. Instead we presume leap years every 4 years even back to 999,999BC.
 * <p/>
 * leap seconds: <b>no</b>
 * <p/>
 * Normally all you need is one BigDate object that you use for all interconversions with set(ordinal), set(yyy,mm,dd)
 * and getOrdinal(), getYYYY(), getMM(), getDD().
 * <p/>
 * java.util.Date has some odd habits, using 101 to represent the year 2001, and 11 to represent December. BigDate is
 * more conventional. You use 2001 to represent the year 2001 and 12 to represent December.
 * <p/>
 * BigDate implements proleptic Gregorian and Julian calendars. That is, dates are computed by extrapolating the current
 * rules indefinitely far backward and forward in time. As a result, BigDate may be used for all years to generate
 * meaningful and consistent results. However, dates obtained using BigDate are historically accurate only from March 1,
 * 4 AD onward, when modern Julian calendar rules were adopted. Before this date, leap year rules were applied
 * irregularly, and before 45 BC the Julian calendar did not even exist. Prior to the institution of the Gregorian
 * calendar, New Year's Day was March 25. To avoid confusion, this calendar always uses January 1.
 * <p/>
 * TODO Future enhancements: - handle time, and timezones, interconversion with GregorianCalendar dates.
 * <p/>
 * 
 * @author Roedy Green, Canadian Mind Products
 * @version 5.6 2009-12-09 - parsing of String dates is now more relaxed. Now ignores spaces, commas. Accepts single
 *          digit months and days.
 * @noinspection WeakerAccess,UnusedDeclaration
 * @since 1997-05-03
 */
public final class BigDate implements Cloneable, Serializable, Comparable<BigDate> {
    // ------------------------------ CONSTANTS ------------------------------

    /**
     * PLEASE CONFIGURE isBritish BEFORE COMPILING. Mysterious missing days in the calendar. Pope Gregory: 1582-10-04
     * Thursday, was followed immediately by 1582-10-15 Friday dropping 10 days. British: 1752-09-02 Wednesday was
     * followed immediately by 1752-09-14 Thursday dropping 12 days. Constant: true if you want the British calender,
     * false if Pope Gregory's. You must recompile for it to have effect. For Britain, the USA and Canada it should be
     * true.
     */
    public static final boolean isBritish = true;

    /**
     * Constant: adjustment to make ordinal 0 come out on 1970-01-01 for AD date calculations. This number was computed
     * by making an estimate, seeing what value toOrdinal gave for 1970-01-01 and then adjusting this constant so that
     * 1970-01-01 would come out Ordinal 0.
     */
    private static final int AD_epochAdjustment;

    /**
     * April is 4
     */
    public static final int APR = 4;

    /**
     * April is 4
     */
    public static final int APRIL = 4;

    /**
     * August is 8
     */
    public static final int AUG = 8;

    /**
     * August is 8
     */
    public static final int AUGUST = 8;

    /**
     * Constant: adjustment to make ordinal 0 come out to 1970-01-01 for BC date calculations. Account for missing year
     * 0.
     */
    private static final int BC_epochAdjustment;

    /**
     * Constant: when passed to a constructor, it means caller guarantees YYYY MM DD are valid including leap year
     * effects and missing day effects. BigDate will not bother to check them.
     * 
     * @noinspection WeakerAccess
     * @see #CHECK
     * @see #NORMALIZE
     * @see #NORMALISE
     */
    public static final int BYPASSCHECK = 1;

    /**
     * constant: when passed to a contructor it means BigDate should check that YYYY MM DD are valid.
     * 
     * @noinspection WeakerAccess
     * @see #BYPASSCHECK
     * @see #NORMALIZE
     * @see #NORMALISE
     */
    public static final int CHECK = 0;

    /**
     * December is 12
     */
    public static final int DEC = 12;

    /**
     * December is 12
     */
    public static final int DECEMBER = 12;

    /**
     * February is 2
     */
    public static final int FEB = 2;

    /**
     * February is
     */
    public static final int FEBRUARY = 2;

    /**
     * Friday in BigDate is 5
     */
    public static final int FRI = 5;

    /**
     * Friday in BigDate is 5
     */
    public static final int FRIDAY = 5;

    /**
     * Constant: day of the first day of the month of the Gregorian Calendar Pope Gregory: 1582-10-15, British: 1752 Sep
     * 14
     */
    private static final int GC_firstDD;

    /**
     * Constant: Ordinal for 1582 Dec 31, the last day of first year of the Gregorian calendar.
     */
    private static final int GC_firstDec_31;

    /**
     * Constant: month of the first date of the Gregorian Calendar. Pope Gregory: 1582-10-15, British: 1752-09-14
     * 
     * @noinspection FieldCanBeLocal
     */
    private static final int GC_firstMM;

    /**
     * Constant: Ordinal for 1582-10-15, the first day of the Gregorian calendar.
     */
    private static final int GC_firstOrdinal;

    /**
     * Constant: year of the first date (1752-10-15) of the Gregorian Calendar = 1582. Just after the missing daysp.
     * Different parts of the world made the transition at different times. Pope Gregory: 1582-10-04, British:
     * 1752-09-02.
     */
    private static final int GC_firstYYYY;

    /**
     * January is 1
     */
    public static final int JAN = 1;

    /**
     * Constant: Ordinal for 1 AD Jan 01
     */
    private static final int Jan_01_0001;

    // don't move this up with other publics since it requires privates
    // for its definition.

    /**
     * Constant: Ordinal for 1 BC Jan 01
     */
    private static final int Jan_01_0001BC;

    /**
     * Constant: Ordinal for 4 AD Jan 01
     */
    private static final int Jan_01_0004;

    /**
     * Constant: ordinal of 1600 Jan 01, the first year when the mod 100 leap year rule first had any effect.
     */
    private static final int Jan_01_Leap100RuleYear;

    /**
     * Constant: ordinal of 1600 Jan 01, the year when the mod 400 leap year rule first had any effect.
     */
    private static final int Jan_01_Leap400RuleYear;

    /**
     * January is 1
     */
    public static final int JANUARY = 1;

    /**
     * July is 7
     */
    public static final int JUL = 7;

    /**
     * July is 7
     */
    public static final int JULY = 7;

    /**
     * June is 6
     */
    public static final int JUN = 6;

    /**
     * June is 6
     */
    public static final int JUNE = 6;

    /**
     * Constant: year that the mod 100 rule first had any effect. For The Gregorian Calendar, 1600. For the British
     * Calendar, 1800. Round up to next 100 years after the missing day anomaly.
     */
    private static final int Leap100RuleYYYY;

    /**
     * Constant: year that the mod 400 rule first had any effect. For The Gregorian Calendar, 1600. For the British
     * Calendar, 2000. Round up to next 400 years after the missing day anomaly.
     */
    private static final int Leap400RuleYYYY;

    /**
     * March is 3
     */
    public static final int MAR = 3;

    /**
     * March is 3
     */
    public static final int MARCH = 3;

    /**
     * Constant: biggest ordinal that BigDate will accept, corresponds to 999,999 Dec 31 AD.
     */
    public static final int MAX_ORDINAL;

    /**
     * Constant: biggest year that BigDate handles, 999,999 AD.
     */
    public static final int MAX_YEAR = 999999;

    /**
     * May is 5
     */
    public static final int MAY = 5;

    /**
     * Constant: earliest ordinal that BigDate handles; corresponds to 999,999 Jan 01 BC.
     */
    public static final int MIN_ORDINAL;

    /**
     * Constant: earliest year that BigDate handles, 999,999 BC.
     * 
     * @noinspection WeakerAccess
     */
    public static final int MIN_YEAR = -999999;

    /**
     * Constant: how many days were lost during the Gregorian correction, 10 for the Gregorian calendar, 11 for the
     * British.
     */
    private static final int missingDays;

    /**
     * Monday in BigDate is 1
     */
    public static final int MON = 1;

    /**
     * Monday in BigDate is 1
     */
    public static final int MONDAY = 1;

    /**
     * Adjustment to make Monday come out as day 0 after doing 7 modulus. Accounts for fact MIN_ORDINAL was not a Monday
     */
    private static final int MondayIsZeroAdjustment = 3;

    /**
     * Constant: when passed to a constructor, it means any invalid dates are converted into the equivalent valid ones.
     * e.g. 1954-09-31 -> 1954-10-01. 1954-10- minus 1 -> 1954-09-30 1954-13-01 -> 1955-01-01.
     * 
     * @see #CHECK
     * @see #BYPASSCHECK
     * @see #NORMALIZE
     */
    public static final int NORMALISE = 2;

    /**
     * Constant: American spelling alias for NORMALISE.
     * 
     * @see #CHECK
     * @see #BYPASSCHECK
     * @see #NORMALISE
     */
    public static final int NORMALIZE;

    /**
     * November is 11
     */
    public static final int NOV = 11;

    /**
     * November is 11
     */
    public static final int NOVEMBER = 11;

    /**
     * Constant: ordinal to represent a null date -2,147,483,648, null Gregorian is 0,0,0.
     */
    public static final int NULL_ORDINAL = Integer.MIN_VALUE;

    /**
     * October is 10
     */
    public static final int OCT = 10;

    /**
     * October is 10
     */
    public static final int OCTOBER = 10;

    /**
     * Constant: day of the last day of the month of the old Julian calendar. Pope Gregory: 1582-10-04, British: 1752
     * Sep 2
     */
    private static final int OJC_lastDD;

    /**
     * Constant: month of the last date of the old Julian Calendar Pope Gregory: 1582-10-04, British: 1752-09-02
     */
    private static final int OJC_lastMM;

    /**
     * Constant: year of the last date (1582-10-04) of the old Julian calendar, just prior to the missing 10 days, =
     * 1852. Different parts of the world made the transition at different times. Usually 1582-10-04. For British
     * calender it would be 1752-09-02 .
     */
    private static final int OJC_lastYYYY;

    /**
     * Saturday in BigDate is 6
     */
    public static final int SAT = 6;

    /**
     * Saturday in BigDate is 6
     */
    public static final int SATURDAY = 6;

    /**
     * September is 9
     */
    public static final int SEP = 9;

    /**
     * September is 9
     */
    public static final int SEPTEMBER = 9;

    /**
     * Sunday is 0
     */
    public static final int SUN = 0;

    /**
     * Sunday is 0
     */
    public static final int SUNDAY = 0;

    /**
     * Adjustment to make Sunday come out as day 0 after doing 7 modulus. Accounts for fact MIN_ORDINAL was not a
     * Sunday.
     */
    private static final int SundayIsZeroAdjustment = 4;

    /**
     * Thursday in BigDate is 4
     */
    public static final int THU = 4;

    /**
     * Thursday in BigDate is 4
     */
    public static final int THURSDAY = 4;

    /**
     * Tuesday in BigDate is 2
     */
    public static final int TUE = 2;

    /**
     * Tuesday in BigDate is 2
     */
    public static final int TUESDAY = 2;

    /**
     * Wednesday in BigDate is 3
     */
    public static final int WED = 3;

    /**
     * Wednesday in BigDate is 3
     */
    public static final int WEDNESDAY = 3;

    /**
     * Constant : value for a null TimeStamp -9,223,372,036,854,775,808
     * 
     * @noinspection WeakerAccess
     */
    public static final long NULL_TIMESTAMP = Long.MIN_VALUE;

    /**
     * used to identify this version of serialised BigDate objects
     */
    static final long serialVersionUID = 34L;

    /**
     * days of the week 3 letter abbreviations in English. Index Sunday = 0.
     */
    private static final String[] dayAbbr = { "sun", "mon", "tue", "wed", "thu", "fri", "sat" };

    /**
     * full days of the week in English. Index Sunday = 0.
     */
    private static final String[] dayName = { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
            "Saturday" };

    /**
     * 3-letter Months of the year in English. index January = 1
     */
    private static final String[] monthAbbr = { "???", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
            "Oct", "Nov", "Dec" };

    /**
     * full Months of the year in English. index January = 1
     */
    private static final String[] monthName = { "unknown", "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December" };

    /**
     * Constant array: how many days in the year prior to the first of the given month in a leap year. Indexed by Jan=0.
     */
    private static final int[] leap_daysInYearPriorToMonthTable = { 0, 31, 60, /* J F M */
    91, 121, 152, /* A M J */
    182, 213, 244, /* J A S */
    274, 305, 335 };/* O N D */

    /**
     * Constant array: how many days in the year prior to the first of the given month in a non-leap year. Indexed by
     * Jan=0.
     */
    private static final int[] usual_daysInYearPriorToMonthTable = { 0, 31, 59, /* J F M */
    90, 120, 151, /* A M J */
    181, 212, 243, /* J A S */
    273, 304, 334 };/* O N D */

    // P R I V A T E _ T A B L E S _ F O R _ D A T E _ C O N V E R S I O N

    /**
     * Constant array: how many days are in there in a month, (not a leap year). Indexed by Jan = 0.
     */
    private static final int[] usual_DaysPerMonthTable = { 31, 28, 31, /* J F M */
    30, 31, 30, /* A M J */
    31, 31, 30, /* J A S */
    31, 30, 31 };/* O N D */

    /**
     * Constant array: what month does the indexing day number fall in a leap year? Indexed by ddd Jan 1 = 0.
     */
    private static int[] leap_dddToMMTable;

    /**
     * Constant array: what month does the indexing day number fall in a non-leap year? Indexed by ddd Jan 1 = 0.
     */
    private static int[] usual_dddToMMTable;

    // ------------------------------ FIELDS ------------------------------

    /**
     * Day, 1 to 31. If size of BigDate objects were a consideration, you could make this a byte.
     * 
     * @noinspection WeakerAccess
     */
    protected transient int dd = 0;

    /**
     * Month, 1 to 12. If size of BigDate objects were a consideration, you could make this a byte.
     * 
     * @noinspection WeakerAccess
     */
    protected transient int mm = 0;

    /**
     * Ordinal days since Jan 01, 1970. -365968798 to 364522971. i.e. 999,999 BC to 999,999 AD.
     */
    protected int ordinal = NULL_ORDINAL;

    /**
     * Year, -999,999 to +999,999, negative is BC, positive is AD, 0 is null. If I were rewriting this, I would likely
     * encode year 1 BC as 0, and convert on output. That would simplify calculation over the 1AD -1BC barrrier.
     * 
     * @noinspection WeakerAccess
     */
    protected transient int yyyy = 0;


    // -------------------------- PUBLIC STATIC METHODS --------------------------

    /**
     * Returns a BigDate object initialised to today's UTC (Greenwich GMT) date, in other words the date and time in
     * Greenwich England right now without any summer time correction. It works even if Java's default Timezone is not
     * configured correctly, but it requires your system clock accurately set to UTC time. Experiment setting your
     * system date/time to various values and making sure you are getting the expected results. Note the date in the
     * created object does not keep updating every time you reference it with methods like getOrdinal or getDD. You
     * always get the date the object was created.
     * 
     * @return BigDate object initialised to today, in Greenwich.
     * @see #localToday
     * @see #today
     */
    public static BigDate UTCToday() {
        // 86,400,000 = 1000 * 60 * 60 * 24 = milliseconds per day
        return new BigDate((int) (System.currentTimeMillis() / 86400000L));
    }// end UTCToday


    /**
     * calculate the age in years, months and days. To compute elapsed time between two dates, use the first as the
     * birthDate and the second as the asOf. DeathDate for asOf gives age at time of death. Today for asOf gives age
     * today.
     * 
     * @param birthDate usually the birth of a person.
     * @param asOf usually today, the day you want the age as of. asOf must come after birthDate to get a meaningful
     *            result. Usually asOf > birthDate. Difference is always positive no matter if asOf is > or < birthDate
     * @return array of three ints (not Integers). [0]=age in years, [1]=age in months, [2]=age in days.
     * @see #localToday
     * @see #today
     * @see #UTCToday
     * @see #isAnniversary
     */
    public static int[] age(BigDate birthDate, BigDate asOf) {
        if (birthDate.getOrdinal() >= asOf.getOrdinal()) {
            if (birthDate.getOrdinal() == asOf.getOrdinal()) {
                return new int[] { 0, 0, 0 };
            } else {
                // age to date in future, exchange dates so difference positive
                final BigDate temp = asOf;
                asOf = birthDate;
                birthDate = temp;
            }
        }
        int birthYYYY = birthDate.getYYYY();
        int birthMM = birthDate.getMM();
        int birthDD = birthDate.getDD();

        int asOfYYYY = asOf.getYYYY();
        int asOfMM = asOf.getMM();
        int asOfDD = asOf.getDD();

        int ageInYears = asOfYYYY - birthYYYY;
        int ageInMonths = asOfMM - birthMM;
        int ageInDays = asOfDD - birthDD;

        if (ageInDays < 0) {
            // This does not need to be a while loop because
            // birthDD is always less than daysInbirthMM month.
            // Guaranteed after this single treatment, ageInDays will be >= 0.
            // i.e. ageInDays = asOfDD - birthDD + daysInBirthMM.
            ageInDays += BigDate.daysInMonth(birthMM, birthYYYY);
            ageInMonths--;
        }

        if (ageInMonths < 0) {
            ageInMonths += 12;
            ageInYears--;
        }
        if (birthYYYY < 0 && asOfYYYY > 0) {
            ageInYears--;
        }

        if (ageInYears < 0) {
            ageInYears = 0;
            ageInMonths = 0;
            ageInDays = 0;
        }
        int[] result = new int[3];
        result[0] = ageInYears;
        result[1] = ageInMonths;
        result[2] = ageInDays;
        return result;
    }// end age


    /**
     * Get day of week for given ordinal. It is one-based starting with Sunday.
     * 
     * @param ordinal days since 1970-01-01 to test.
     * @return day of week 1=Sunday 2=Monday 3=Tuesday 4=Wednesday 5=Thursday 6=Friday 7=Saturday Compatible with Sun's
     *         1=Calendar.SUNDAY Not compatible with BigDate.getDayOfWeek.
     * @see #isoDayOfWeek
     * @see #dayOfWeek
     * @see #getCalendarDayOfWeek
     */
    public static int calendarDayOfWeek(int ordinal) {
        return dayOfWeek(ordinal) + 1;
    }


    /**
     * Get 3-char abbreviation of a given day of the week
     * 
     * @param dayOfWeek sunday = 0
     * @return abbreviation for day of week, e.g. "sun", all lower case.
     */
    public static String dayAbbr(int dayOfWeek) {
        return dayAbbr[dayOfWeek];
    }


    /**
     * Get full name of given day of the week
     * 
     * @param dayOfWeek sunday = 0
     * @return name of day of week e.g. "Sunday"
     */
    public static String dayName(int dayOfWeek) {
        return dayName[dayOfWeek];
    }


    /**
     * Get day of week for given ordinal. Is it zero-based starting with Sunday.
     * 
     * @param ordinal days since 1970-01-01 to test.
     * @return day of week 0=Sunday 1=Monday 2=Tuesday 3=Wednesday 4=Thursday 5=Friday 6=Saturday WARNING: not compatible
     *         with 1=Calendar.SUNDAY
     * @see #calendarDayOfWeek
     * @see #isoDayOfWeek
     * @see #getDayOfWeek
     */
    public static int dayOfWeek(int ordinal) {
        // modulus in Java is "broken" for negative numbers
        // so we adjust to make the dividend positive.
        // By "broken" I mean the official rules for what
        // is supposed to happen for negative dividends
        // won't give the desired result in this case.
        // See modulus in the Java glossary for more details.
        return (ordinal == NULL_ORDINAL) ? 0 : ((ordinal + SundayIsZeroAdjustment - MIN_ORDINAL) % 7);
    }


    /**
     * How many days are there in a given month?
     * 
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param leap true if you are interested in a leap year
     * @return how many days are in that month
     */
    public static int daysInMonth(int mm, boolean leap) {
        if (mm != 2) {
            return usual_DaysPerMonthTable[mm - 1];
        } else {
            return leap ? 29 : 28;
        }
    }// end daysInMonth


    /**
     * How many days are there in a given month? o
     * 
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param yyyy year of interest.
     * @return how many days are in that month
     */
    public static int daysInMonth(int mm, int yyyy) {
        if (mm != 2) {
            return usual_DaysPerMonthTable[mm - 1];
        } else {
            return isLeap(yyyy) ? 29 : 28;
        }
    }// end daysInMonth


    /**
     * Multiply then divide using floored rather than the usual truncated arithmetic, using a long intermediate.
     * 
     * @param multiplicand one of two numbers to multiply together
     * @param multiplier one of two numbers to multiply together
     * @param divisor number to divide by
     * @return (multiplicand * multiplier) / divisor
     */
    public static int flooredMulDiv(int multiplicand, int multiplier, int divisor) {
        long result = (long) multiplicand * (long) multiplier;
        if (result >= 0) {
            return (int) (result / divisor);
        } else {
            return (int) ((result - divisor + 1) / divisor);
        }
    }// end flooredMulDiv


    /**
     * Embeds copyright notice
     * 
     * @return copyright notice
     * @noinspection SameReturnValue
     */
    public static String getCopyright() {
        return "BigDate 5.1 freeware copyright (c) 1997-2010 Roedy Green, Canadian Mind Products, http://mindprod.com roedyg@mindprod.com";
    }


    // p u b l i c m e t h o d s

    /**
     * Determine if this date is someone's birthdate, or the aniversary of their death, or the anniversary of a marriage
     * etc. For example boolean todayIsYourBirthday = BigDate.isAnniversary( born, BigDate.localToday() ); boolean
     * todayIsYourWeddingAnniversary = BigDate.isAnniversary( weddingDate, BigDate.localToday() ); boolean
     * todayAnniversaryDeathOfBach = BigDate.isAnniversary( new BigDate("1750-07-28"), BigDate.localToday());
     * <p/>
     * To compute relative anniversary between two dates, use the first as the birthDate and the second as the asof.
     * <p/>
     * February 29 is treated specially, to handle the way people people born on Feb 29 celebrate their birth days on
     * Feb 28 in non-leap years. If the asOf year is not a leap year then Feb 28 is considered a match for Feb 29.
     * However, if the asOf date is Feb 29 then Feb 28 in the birth year is not considered a match.
     * 
     * @param birthDate usually the birth of a person, or possibly a date.
     * @param asOf usually today, the day you want the to know if it is an anniversary. asof must come after birthDate
     *            to get a meaningful result.
     * @return true if asOf date in as even number of years after the birthdate. If the asOf date is <= birthDate it will
     *         return false.
     * @see #age
     */
    public static boolean isAnniversary(BigDate birthDate, BigDate asOf) {
        if (asOf.compareTo(birthDate) < 0) {
            return false;
        }
        int asOfMM = asOf.getMM();
        int birthMM = birthDate.getMM();
        if (birthMM != asOfMM) {
            return false;
        }
        int birthDD = birthDate.getDD();
        int asOfDD = asOf.getDD();
        if (birthDD == asOfDD) {
            return true;
        }
        // at this point, months matched, but days did not.
        // celebrate feb 29 birthday on feb 28 in non leap year.
        return birthDD == 29 && asOfDD == 28 && birthMM == 2 /* && asOfMM == 2 */&& !isLeap(asOf.getYYYY());
    }


    /**
     * Is the given year a leap year, considering history, mod 100 and mod 400 rules? By 1582, this excess of leap years
     * had built up noticeably. At the suggestion of astronomers Luigi Lilio and Christopher Clavius, Pope Gregory XIII
     * dropped 10 days from the calendar. Thursday 1582 October 4 Julian was followed immediately by Friday 1582 October
     * 15 Gregorian. He decreed that every 100 years, a leap year should be dropped except that every 400 years the leap
     * year should be restored. Only Italy, Poland, Portugual and Spain went along with the new calendar immediately.
     * One by one other countries adopted it in different years. Britain and its territories (including the USA and
     * Canada) adopted it in 1752. By then, 11 days had to be dropped. 1752-09-02 was followed immediately by
     * 1752-09-14. The Gregorian calendar is the most widely used scheme. This is the scheme endorsed by the US Naval
     * observatory. It corrects the year to 365.2425. It gets ahead 1 day every 3289 years. For BC dates, the years the
     * years 1, 5, 9 are leap years, not 4, 8, 12 as you might expect, from the general rule.
     * 
     * @param yyyy year to test.
     * @return true if the year is a leap year.
     * @noinspection SimplifiableIfStatement
     */
    public static boolean isLeap(int yyyy) {
        // if you change this code, make sure you make corresponding changes to
        // jan01OfYear, toGregorian, MondayIsZeroAdjustment,
        // SundayIsZeroAdjustment,
        // AD_epochAdjustment and BC_epochAdjustment
        // yyyy & 3 is a fast way of saying yyyy % 4
        if (yyyy < Leap100RuleYYYY) {
            if (yyyy < 0) {
                return ((yyyy + 1) & 3) == 0;
            } else {
                return (yyyy & 3) == 0;
            }
        }
        if ((yyyy & 3) != 0) {
            return false;
        }
        if (yyyy % 100 != 0) {
            return true;
        }
        if (yyyy < Leap400RuleYYYY) {
            return false;
        }
        return yyyy % 400 == 0;
    }


    /**
     * Test to see if the given yyyy-mm-dd is a date as a String is legitimate. must have 4-digit years, and use dashes
     * between the number and no sign Does extensive checks considering leap years, missing days etc.
     * 
     * @param yyyy_mm_dd string of form "yyyy-mm-dd". yyyy-mm-dd y-mm-ddBC 2009-12-31 1234-5-31AD 1970/3/3 AD 3.01.12 bc
     *            2000_12_31 It will not accept a date missing the year, month or day field. Spaces and commas are
     *            ignored.
     * @return true if that represents a valid date.
     */
    public static boolean isValid(String yyyy_mm_dd) {
        final int[] ymd = relaxedParse(yyyy_mm_dd);
        return ymd != null && BigDate.isValid(ymd[0], ymd[1], ymd[2]);
    }


    /**
     * Test to see if the given yyyy, mm, dd date is legitimate. Does extensive checks considering leap years, missing
     * days etc.
     * 
     * @param yyyy -999,999 (BC) to +999,999 (AD)
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param dd day 1 to 31, no lead 0.
     * @return true if yyyy mm dd is a valid date.
     * @noinspection SimplifiableIfStatement
     */
    public static boolean isValid(int yyyy, int mm, int dd) {
        // null date 0000 00 00 is considered valid
        // but otherwise year 0000 never happened.
        if (yyyy == 0) {
            return (mm == 0) && (dd == 0);
        }
        if ((yyyy < MIN_YEAR) || (yyyy > MAX_YEAR) || (mm < 1) || (mm > 12) || (dd < 1) || (dd > 31)) {
            return false;
        }
        // account for missing 10 days in 1582.
        // Thursday 1582 October 4 Julian was followed
        // immediately by Friday 1582 October 15
        // Similarly for the British Calendar
        if (yyyy == OJC_lastYYYY && mm == OJC_lastMM && OJC_lastDD < dd && dd < GC_firstDD) {
            return false;
        }
        return dd <= daysInMonth(mm, yyyy);
    }// end isValid


    /**
     * Get day of week 1 to 7 for this ordinal according to the ISO standard IS-8601. It is one-based starting with
     * Monday.
     * 
     * @param ordinal days since 1970-01-01 to test.
     * @return day of week 1=Monday to 7=Sunday, 0 for null date. WARNING: not compatible with 1=Calendar.SUNDAY.
     * @see BigDate#dayOfWeek(int)
     */
    public static int isoDayOfWeek(int ordinal) {
        // modulus in Java is "broken" for negative numbers
        // so we adjust to make the dividend positive.
        // By "broken" I mean the official rules for what
        // is supposed to happen for negative dividends
        // won't give the desired result in this case.
        // See modulus in the Java glossary for more details.
        return (ordinal == NULL_ORDINAL) ? 0 : ((ordinal + MondayIsZeroAdjustment - MIN_ORDINAL) % 7) + 1;
    }


    /**
     * Returns a BigDate object initialised to today's local date. It depends on Java's default Timezone being
     * configured, and your system clock accurately set to UTC time. Experiment setting your system date/time to various
     * values and making sure you are getting the expected results. Note the date in the created object does not keep
     * updating every time you reference it with methods like getOrdinal or getDD. You always get the date the object
     * was created. It is quite a production to get the local date. Best to ask once and save the today object.
     * 
     * @return BigDate object initialised to today, local time.
     * @see #today #see #UTCToday
     */
    public static BigDate localToday() {
        return today(TimeZone.getDefault());
    }// end localToday


    /**
     * Get 3-char abbreviation of a given month of the year.
     * 
     * @param mm month number jan = 1
     * @return abbreviation for month, e.g. "Jan"
     */
    public static String monthAbbr(int mm) {
        return monthAbbr[mm];
    }


    /**
     * Get full name of a given month of the year.
     * 
     * @param mm month number jan = 1
     * @return name of month e.g. "January"
     */
    public static String monthName(int mm) {
        return monthName[mm];
    }


    /**
     * Find the first monday in a given month, the 3rd monday or the last Thursday...
     * 
     * @param which 1=first 2=second 3=third 4=fourth 5=last (might be 4th or 5th)
     * @param dayOfWeek 0=Sunday 1=Monday 2=Tuesday 3=Wednesday 4=Thursday 5=Friday 6=Saturday WARNING: not compatible
     *            with 1=Calendar.SUNDAY.
     * @param yyyy year of interest.
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @return day of month 1..31
     */
    public static int nthXXXDay(int which, int dayOfWeek, int yyyy, int mm) {
        int dayOfWeekOf1st = BigDate.dayOfWeek(BigDate.toOrdinal(yyyy, mm, 1));
        int dayOfMonthOfFirstDesiredDay = (dayOfWeek - dayOfWeekOf1st + 7) % 7 + 1;
        int dayOfMonthOfNthDesiredDay = dayOfMonthOfFirstDesiredDay + (which - 1) * 7;
        if (which >= 5 && dayOfMonthOfNthDesiredDay > daysInMonth(mm, yyyy)) {
            dayOfMonthOfNthDesiredDay -= 7;
        }
        return dayOfMonthOfNthDesiredDay;
    }// end nthXXXDay


    /**
     * Find the first monday in a given month, the 3rd monday or the last Thursday...
     * 
     * @param which 1=first 2=second 3=third 4=fourth 5=last (might be 4th or 5th)
     * @param dayOfWeek 0=Sunday 1=Monday 2=Tuesday 3=Wednesday 4=Thursday 5=Friday 6=Saturday WARNING: not compatible
     *            with 1=Calendar.SUNDAY.
     * @param yyyy year of interest.
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @return day of month 1..31
     */
    public static int ordinalOfnthXXXDay(int which, int dayOfWeek, int yyyy, int mm) {
        int dayOfMonthOfNthDesiredDay = nthXXXDay(which, dayOfWeek, yyyy, mm);
        return BigDate.toOrdinal(yyyy, mm, dayOfMonthOfNthDesiredDay);
    }// end ordinalOfnthXXXDay


    /**
     * parse a string into yyyy which may have form: 1948 230BC 1234AD It does not allow a space before the BC or AD.
     * 
     * @param yyyyString date string.
     * @return yyyy, negative for BC.
     * @throws NumberFormatException
     */
    public static int parseYYYY(String yyyyString) throws NumberFormatException {
        int ye = yyyyString.length();
        boolean bc = false;
        if (yyyyString.endsWith("BC")) {
            ye -= 2;
            bc = true;
        } else if (yyyyString.endsWith("AD")) {
            ye -= 2;
        }
        int yyyy = Integer.parseInt(yyyyString.substring(0, ye));
        if (bc) {
            yyyy = -yyyy;
        }
        return yyyy;
    }


    /**
     * Parse a, possibly incomplete, date String of form yyyy-yy-dd or yyyy-mm or yyyy into a BigDate Also handles
     * AD/BC, 1-digit months, 1-digit days and 1, 2 or 3-digit years. Date must be a valid normalised date.
     * 
     * @param incomplete Date string, usually of form YYYY-MM-DD, possibly just YYYY-MM or YYYY.
     * @return BigDate first of year for YYYY or first of month for YYYY-MM, or precise date for YYYY-MM-DD
     * @see #BigDate(String)
     */
    public static BigDate parseYYYYmmdd(String incomplete) {
        final String complete;
        // count dashes in string
        switch (StringTools.countInstances(incomplete, '-')) {
            case 2:
                // date already yyyy-mm-dd
                complete = incomplete;
                break;

            case 1:
                // was yyyy-mm
                if (incomplete.endsWith("BC") || incomplete.endsWith("AD")) {
                    complete = incomplete.substring(0, incomplete.length() - 2) + "-01"
                            + incomplete.substring(incomplete.length() - 2);
                } else {
                    complete = incomplete + "-01";
                }
                break;

            case 0:
                // was yyyy
                if (incomplete.endsWith("BC") || incomplete.endsWith("AD")) {
                    complete = incomplete.substring(0, incomplete.length() - 2) + "-01-01"
                            + incomplete.substring(incomplete.length() - 2);
                } else {
                    complete = incomplete + "-01-01";
                }

                break;

            default:
                throw new IllegalArgumentException("BigDate.parseYYYYmmdd expects YYYY-MM-DD or YYYY-MM or YYYY.");
        }
        return new BigDate(complete);
    }


    /**
     * Convert date in form YYYY MM DD into days since the 1970-01-01. This method lets you convert directly from
     * Gregorian to ordinal without creating a BigDate object. yyyy mm dd must be a valid date.
     * 
     * @param yyyy -999,999 (BC) to +999,999 (AD)
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param dd day 1 to 31, no lead 0.
     * @return ordinal, days since 1970-01-01.
     */
    public static int toOrdinal(int yyyy, int mm, int dd) {
        // treat null date as a special case
        if ((yyyy == 0) && (mm == 0) && (dd == 0)) {
            return NULL_ORDINAL;
        }

        // jan01OfYear handles missing day adjustment for years > 1582
        // We only need to handle year = 1582 here.
        int missingDayAdjust = (yyyy == OJC_lastYYYY && ((mm == OJC_lastMM && dd > OJC_lastDD) || mm > OJC_lastMM)) ? missingDays
                : 0;

        return jan01OfYear(yyyy) + daysInYearPriorToMonth(mm, isLeap(yyyy)) - missingDayAdjust + dd - 1;
    }// end toOrdinal


    /**
     * Returns a BigDate object initialised to the date right now in the given timezone. It depends on your system clock
     * accurately set to UTC time. Experiment setting your system date/time to various values and making sure you are
     * getting the expected results. Note the date in the created object does not keep updating every time you reference
     * it with methods like getOrdinal or getDD. You always get the date the object was created. It is quite a
     * production to get the this date. Best to ask once and save the today object.
     * 
     * @param timeZone in which we want to know the today's date.
     * @return BigDate object initialised to today at given timezone.
     * @see #localToday
     * @see #UTCToday
     */
    public static BigDate today(TimeZone timeZone) {
        BigDate d = new BigDate();
        d.setDateAtTime(System.currentTimeMillis(), timeZone);
        return d;
    }// end today


    // -------------------------- PUBLIC INSTANCE METHODS --------------------------
    // don't move this up with other publics since it requires privates
    // for its definition.
    // c o n s t r u c t o r s

    /**
     * Constructor for the null date. Gets set to null date, NOT current date like Java Date!!. BigDate.localToday()
     * will create an object initialised to today's date.
     * 
     * @see #localToday
     * @see #UTCToday
     * @see #today
     */
    public BigDate() {
    }


    /**
     * Construct a BigDate object given the Proleptic Julian day number. The Proleptic Julian calendar that astronomers
     * use starts with 0 at noon on 4713 BCE January 1. In contrast, BigDate's Ordinal base is 1970-01-01.
     * 
     * @param prolepticJulianDay days since 4713 BC Jan 1 noon. Such numbers usually arise in astronomical calculation.
     *            You don't need to concern yourself with the strangeness of the Julian calendar, just its simple day
     *            numbering. BEWARE! after adjusting for noon, fractional parts are discarded. BigDate tracks only
     *            dates, not dates and times. e.g. 2000-03-20 noon is 2,451,624 in proleptic day numbers. 1970-1-1 is
     *            2,440,588 1600-1-1 is 2,305,448 1500-1-1 is 2,268,933 0001-1-1 is 1,721,424 -0001-12-31 is 1,721,423
     *            -0006-1-1 is 1,719,232 -4713-1-1 is 0
     */
    public BigDate(double prolepticJulianDay) {
        setOrdinal((int) ((long) (prolepticJulianDay + 0.5/*
                                                           * noon adjust, .5 or bigger really part of next day
                                                           */) - 2440588L/*
                                                                          * i.e. diff in base epochs of calendars
                                                                          */));
    }// end BigDate constructor


    /**
     * Ordinal constructor. The ordinal must be NULL_ORDINAL or in the range -365968798 to 364522971 i.e. 999,999 BC to
     * 999,999 AD
     * 
     * @param ordinal days since 1970-01-01.
     */
    public BigDate(int ordinal) {
        // save ordinal field and compute Gregorian equivalent
        set(ordinal);
    }// end BigDate constructor


    /**
     * Create a BigDate object from a String of the form: yyyy-mm-dd must have 4-digit years, and use dashes between the
     * number and no sign Does extensive checks considering leap years, missing days etc.
     * 
     * @param yyyy_mm_dd string of form "yyyy-mm-dd". yyyy-mm-dd y-mm-ddBC 2009-12-31 1234-5-31AD 1970/3/3 AD 3.01.12 bc
     *            2000_12_31 It will not accept a date missing the year, month or day field. Spaces and commas are
     *            ignored.
     */
    public BigDate(String yyyy_mm_dd) {
        final int[] ymd = relaxedParse(yyyy_mm_dd);
        if (ymd == null) {
            throw new IllegalArgumentException("unparseable date: " + yyyy_mm_dd);
        }
        // will throw IllegalArgument Exception for an invalid date.
        set(ymd[0], ymd[1], ymd[2], CHECK);
    }


    /**
     * Copy constructor
     * 
     * @param b an existing BigDate object to use as a model for cloning another.
     */
    public BigDate(BigDate b) {
        ordinal = b.ordinal;
        yyyy = b.yyyy;
        mm = b.mm;
        dd = b.dd;
    }


    /**
     * Constructor from Date, loses time information.
     * 
     * @param utc Date ( UTC date/time stamp )
     * @param timeZone Which timeZone do you want to know the date for that UTC time. e.g. TimeZone.getDefault(), new
     *            TimeZone("GMT")
     */
    public BigDate(Date utc, TimeZone timeZone) {
        setDateAtTime(utc.getTime(), timeZone);
    }


    /**
     * Construct a BigDate object given a Gregorian date yyyy, mm, dd; always rejects invalid dates. A null date is
     * yyyy,mm,dd=0. BEWARE! In Java a lead 0 on an integer implies OCTAL.
     * 
     * @param yyyy -999,999 (BC) to +999,999 (AD)
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param dd day 1 to 31, no lead 0.
     * @throws IllegalArgumentException for invalid yyyy mm dd
     */
    public BigDate(int yyyy, int mm, int dd) {
        set(yyyy, mm, dd, CHECK);
    }// end BigDate constructor


    /**
     * Construct a BigDate object given a Gregorian date yyyy, mm, dd; allows control of how invalid dates are handled.
     * A null date is yyyy,mm,dd=0. BEWARE! In Java a lead 0 on an integer implies OCTAL.
     * 
     * @param yyyy -999,999 (BC) to +999,999 (AD)
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param dd day 1 to 31, no lead 0.
     * @param how one of CHECK BYPASSCHECK NORMALIZE NORMALISE
     */
    public BigDate(int yyyy, int mm, int dd, int how) {
        // save yyyy, mm, dd, and compete the ordinal equivalent
        set(yyyy, mm, dd, how);
    }// end BigDate constructor


    /**
     * increment this date by a number of days.
     * 
     * @param days positive or negative, -1 gets day before this one.
     */
    public final void addDays(int days) {
        if (days == 0) {
            return;
        }
        ordinal += days;
        toGregorian();
    }// end addDays


    /**
     * Get day of week for this BigDate. It is one-based starting with Sunday.
     * 
     * @return day of week 1=Sunday 2=Monday 3=Tuesday 4=Wednesday 5=Thursday 6=Friday 7=Saturday Compatible with Sun's
     *         1=Calendar.SUNDAY Not compatible with BigDate.getDayOfWeek.
     * @see #isoDayOfWeek
     * @see #dayOfWeek
     * @see #getCalendarDayOfWeek
     */
    public int calendarDayOfWeek() {
        return calendarDayOfWeek(ordinal);
    }


    /**
     * Compare BigDates in ascending date order. Defines default the sort order for BigDate Objects. Compare this
     * BigDate with another BigDate. Compares ordinal. Informally, returns (this-other) or +ve if this is more positive
     * than other.
     * 
     * @param other other BigDate to compare with this one
     * @return +ve if this&gt;other, 0 if this==other, -ve if this&lt;other
     */
    @Override
    public final int compareTo(BigDate other) {
        return ordinal - other.ordinal;
    }


    /**
     * Get day of week for this BigDate. Is it zero-based starting with Sunday.
     * 
     * @return day of week 0=Sunday 1=Monday 2=Tuesday 3=Wednesday 4=Thursday 5=Friday 6=Saturday WARNING: not
     *         compatible with 1=Calendar.SUNDAY
     * @see #calendarDayOfWeek
     * @see #isoDayOfWeek
     * @see #getDayOfWeek
     */
    public int dayOfWeek() {
        return dayOfWeek(ordinal);
    }


    /**
     * Compares with another BigDate to see if they refer to the same date.
     * 
     * @param d other BigDate to compare with this one.
     * @return true if BigDate d refers to the same date
     */
    @Override
    public final boolean equals(Object d) {
        return d == this || d instanceof BigDate && (ordinal == ((BigDate) d).getOrdinal());
    }


    /**
     * Get day of week for this BigDate. Is it one-based starting with Sunday.
     * 
     * @return day of week 1=Sunday 2=Monday 3=Tuesday 4=Wednesday 5=Thursday 6=Friday 7=Saturday Compatible with Sun's
     *         1=Calendar.SUNDAY Not compatible with BigDate.getDayOfWee
     * @see #getISODayOfWeek
     * @see #getDayOfWeek
     * @see #calendarDayOfWeek
     */
    public final int getCalendarDayOfWeek() {
        return calendarDayOfWeek(ordinal);
    }


    /**
     * get day of month for this BigDate.
     * 
     * @return day 1 to 31, 0 for null date.
     */
    public final int getDD() {
        return dd;
    }


    /**
     * Get day number in the year for this BigDate.
     * 
     * @return day number Jan 01 = 1, 1 to 366
     */
    public final int getDDD() {
        return (ordinal == NULL_ORDINAL) ? 0 : (ordinal - jan01OfYear(yyyy) + 1);
    }


    /**
     * Get java.util.Date object corresponding to this BigDate,
     * 
     * @param timeZone We consider this BigDate to have an implied time of 0:00 in this timeZone.
     * @return Date or null Result is undefined if BigDate is outside the range handled by Date.
     * @see #getLocalTimeStamp
     * @see #getUTCTimeStamp
     * @see #getTimeStamp
     * @see #getUTCDate
     * @see #getLocalDate
     */
    public final java.util.Date getDate(TimeZone timeZone) {
        return ordinal == NULL_ORDINAL ? null : new java.util.Date(getTimeStamp(timeZone));
    }


    /**
     * Get day of week for this BigDate. It is zero-based starting with Sunday.
     * 
     * @return day of week 0=Sunday 1=Monday 2=Tuesday 3=Wednesday 4=Thursday 5=Friday 6=Saturday WARNING: not
     *         compatible with 1=Calendar.SUNDAY
     * @see #getCalendarDayOfWeek
     * @see #getISODayOfWeek
     * @see #dayOfWeek
     */
    public final int getDayOfWeek() {
        return dayOfWeek(ordinal);
    }


    /**
     * Get day of week 1 to 7 for this BigDate according to the ISO standard IS-8601. It is one-based starting with
     * Monday.
     * 
     * @return day of week 1=Monday to 7=Sunday, 0 for null date. WARNING: not compatible with 1=Calendar.SUNDAY
     * @see #getCalendarDayOfWeek
     * @see #getDayOfWeek
     * @see #isoDayOfWeek
     */
    public final int getISODayOfWeek() {
        return isoDayOfWeek(ordinal);
    }


    /**
     * Get week number 1 to 53 of the year this date falls in, according to the rules of ISO standard IS-8601. A week
     * that lies partly in one year and partly in another is assigned a number in the year in which most of its days
     * lie. This means that week 1 of any year is the week that contains 4 January, or equivalently week 1 of any year
     * is the week that contains the first Thursday in January. Most years have 52 weeks, but years that start on a
     * Thursday and leap years that start on a Wednesday have 53 weeks. Jan 1 may well be in week 53 of the previous
     * year! Only defined for dates on or after 1600 Jan 01. You can find out how many ISO weeks there are per year with
     * new BigDate( year, 12, 31).getISOWeekNumber();
     * 
     * @return week number 1..53, 0 for null or invalid date.
     * @see <a href="http://www.pip.dknet.dk/~pip10160/calendar.faq3.txt">Calendar FAQ</a>
     */
    public final int getISOWeekNumber() {
        if (ordinal < Jan_01_Leap100RuleYear) {
            return 0;
        }
        int jan04Ordinal = jan01OfYear(yyyy) + 3;
        int jan04DayOfWeek = (jan04Ordinal + MondayIsZeroAdjustment - MIN_ORDINAL) % 7;// 0=Monday
        // 6=Sunday
        int week1StartOrdinal = jan04Ordinal - jan04DayOfWeek;
        if (ordinal < week1StartOrdinal) {// we are part of the previous year. Don't worry about year 0.
            jan04Ordinal = jan01OfYear(yyyy - 1) + 3;
            jan04DayOfWeek = (jan04Ordinal + MondayIsZeroAdjustment - MIN_ORDINAL) % 7;// 0=Monday
            // 6=Sunday
            week1StartOrdinal = jan04Ordinal - jan04DayOfWeek;
        } else if (mm == 12) {// see if we are part of next year. Don't worry about year 0.
            jan04Ordinal = jan01OfYear(yyyy + 1) + 3;
            jan04DayOfWeek = (jan04Ordinal + MondayIsZeroAdjustment - MIN_ORDINAL) % 7;// 0=Monday
            // 6=Sunday
            int week1StartNextOrdinal = jan04Ordinal - jan04DayOfWeek;
            if (ordinal >= week1StartNextOrdinal) {
                week1StartOrdinal = week1StartNextOrdinal;
            }
        }
        return ((ordinal - week1StartOrdinal) / 7) + 1;
    }// end getISOWeekNumber


    /**
     * Get java.util.Date object corresponding to this BigDate. We consider this BigDate to have an implied time of 0:00
     * local time.
     * 
     * @return Date or null Result is undefined if BigDate is outside the range handled by Date.
     * @see #getLocalTimeStamp
     */
    public final java.util.Date getLocalDate() {
        return ordinal == NULL_ORDINAL ? null : new java.util.Date(getLocalTimeStamp());
    }


    /**
     * Get milliseconds since 1970-01-01 00:00 GMT for this BigDate. Does not account for leap seconds primarily because
     * we do not know them in advance. N.B. returns long, not int as in many Unix implementations. This the long that a
     * Sun Date constructor wants. We consider this BigDate to have an implied time of 0:00 local time.
     * 
     * @return milliseconds since 1970-01-01 00:00 GMT, or NULL_TIMESTAMP. This is NOT a JDBC Timestamp! You can use
     *         this timestamp with java.util.Date.setTime, java.sql.TimeStamp.setTime or java.sql.Date.setTime or in the
     *         constructors. To interconvert, just cast.
     * @see #getLocalDate
     */
    public final long getLocalTimeStamp() {
        return getTimeStamp(TimeZone.getDefault());
    }


    /**
     * Get month of year for this BigDate.
     * 
     * @return month 1 to 12, 0 for null date.
     */
    public final int getMM() {
        return mm;
    }


    /**
     * get days since 1970-01-01 for this BigDate. 1970-01-01 = day 0.
     * 
     * @return days since 1970-01-01. This is NOT what you want for the java.util.Date constructor.
     * @see #getLocalTimeStamp
     * @see #getUTCTimeStamp
     * @see #getTimeStamp
     */
    public final int getOrdinal() {
        return ordinal;
    }


    /**
     * @return Julian day number of noon of the date BigDate represents. See notes on the Julian Proleptic calendar
     *         under the constructor.
     */
    public final double getProlepticJulianDay() {
        if (ordinal == NULL_ORDINAL) {
            return Double.MIN_VALUE;
        }

        return ordinal + 2440588L/* diff in base epochs of calendars */
        /* no need for noon adjust */;
    }// getProlepticJulianDay


    /**
     * Get season of year for this BigDate.
     * 
     * @return 0=spring (Mar, Apr, May) 1=summer (Jun, Jul, Aug) 2=fall (Sep, Oct, Dec) 3=winter Dec, Jan, Feb
     */
    public final int getSeason() {
        // 1 > 3, 2 > 3, 3 > 0, 4 > 0, 5 > 0, 6 > 1, 7 > 1, 8 > 1, 9 > 2, 10 >
        // 2, 11 > 2, 12 > 3
        return ((mm + (12 - 3)) % 12) / 3;
    }


    /**
     * Get milliseconds since 1970-01-01 00:00 GMT for this BigDate, as at the start of day 0:00. Does not account for
     * leap seconds primarily because we do not know them in advance. N.B. returns long, not int as in many Unix
     * implementations. This the long that a Sun Date constructor wants.
     * 
     * @param timeZone We consider this BigDate to have an implied time of 0:00 in this timeZone.
     * @return milliseconds since 1970-01-01 00:00 GMT, or NULL_TIMESTAMP. This is NOT a JDBC Timestamp! You can use this
     *         timestamp with java.util.Date.setTime, java.sql.TimeStamp.setTime or java.sql.Date.setTime or in the
     *         constructors. To interconvert, just cast.
     * @see #getDate
     * @see #getUTCTimeStamp
     * @see #getLocalTimeStamp
     */
    public final long getTimeStamp(TimeZone timeZone) {
        if (ordinal == NULL_ORDINAL) {
            return NULL_TIMESTAMP;
        }
        /*
         * Gets the time zone offset, for current date, modified in case of daylight savings. This is the offset to add
         * *to* UTC to get local time. Places east of Greenwich have a positive offset. :era the era of the given date,
         * AD = 1 BC = 0 :year the year in the given date. Docs are unclear if 1900=0 or 1900. Does not use year in
         * calcs anyway. :month the month in the given date. Month is 0-based. e.g., 0 for January. :day the
         * day-in-month of the given date. :dayOfWeek the day-of-week of the given date. 1=Calendar.SUNDAY.
         * :milliseconds the millis in day in <em>standard</em> local time. :return the offset in millis to add *to* GMT
         * to get local time.
         */
        int offsetInMillis = timeZone.getOffset(ordinal >= Jan_01_0001 ? 1 : 0, yyyy, mm - 1, dd,
                getCalendarDayOfWeek(), 0);
        // 86,400,000 = 1000 * 60 * 60 * 24 = milliseconds per day
        return ordinal * 86400000L - offsetInMillis;
    }


    /**
     * Get java.util.Date object corresponding to this BigDate. We consider this BigDate to have an implied time of 0:00
     * UTC (Greenwich GMT).
     * 
     * @return Date or null Result is undefined if BigDate is outside the range handled by Date.
     * @see #getUTCTimeStamp
     */
    public final java.util.Date getUTCDate() {
        return ordinal == NULL_ORDINAL ? null : new java.util.Date(getUTCTimeStamp());
    }


    /**
     * Get milliseconds since 1970-01-01 00:00 GMT for this BigDate. Does not account for leap seconds primarily because
     * we do not know them in advance. N.B. returns long, not int as in many Unix implementations. This the long that a
     * Sun Date constructor wants. We consider this BigDate to have an implied time of 0:00 UTC (Greenwich GMT).
     * 
     * @return milliseconds since 1970-01-01 00:00 GMT, or NULL_TIMESTAMP. This is NOT a JDBC Timestamp! You can use
     *         this timestamp with java.util.Date.setTime, java.sql.TimeStamp.setTime or java.sql.Date.setTime or in the
     *         constructors. To interconvert, just cast.
     * @see #getUTCDate
     */
    public final long getUTCTimeStamp() {
        // 86,400,000 = 1000 * 60 * 60 * 24 = milliseconds per day
        return ordinal == NULL_ORDINAL ? NULL_TIMESTAMP : (ordinal * 86400000L);
    }


    /**
     * Get week number 1 to 53 of the year this date falls in. This does NOT follow the rules of ISO standard IS-8601
     * section 5.5. Week 1 is the first week with any days in the current year. Weeks start on Sunday. Jan 1 and Dec 31
     * are always considered part of the current year. Only defined for dates on or after 1600 Jan 01.
     * 
     * @return week number 1..53, 0 for null or invalid date.
     * @see #getISOWeekNumber
     */
    public final int getWeekNumber() {
        if (ordinal < Jan_01_Leap100RuleYear) {
            return 0;
        }
        int jan01 = jan01OfYear(yyyy);
        int jan01DayOfWeek = dayOfWeek(jan01);// 0=Sunday 1=Monday
        // 2=Tuesday 3=Wednesday
        // 4=Thursday 5=Friday
        // 6=Saturday
        int sundayOnOrBeforeJan01Ordinal = jan01 - jan01DayOfWeek;
        return ((ordinal - sundayOnOrBeforeJan01Ordinal) / 7) + 1;
    }// end getWeekNumber


    /**
     * Get year for this BigDate.
     * 
     * @return year -999,999 to 999,999. 0 for null date. negative is BC, positive AD.
     */
    public final int getYYYY() {
        return yyyy;
    }


    /**
     * hashCode for use in Hashtable lookup
     * 
     * @return the ordinal which is perfectly unique for the date.
     */
    @Override
    public final int hashCode() {
        return ordinal;
    }


    /**
     * Find the BigDate with date closest to this one with the given day of week.
     * 
     * @param dayOfWeek 0=Sunday 1=Monday 2=Tuesday 3=Wednesday 4=Thursday 5=Friday 6=Saturday WARNING: not compatible
     *            with 1=Calendar.SUNDAY.
     * @return BigDate object with nearest date to this one with the given day of week.
     */
    public BigDate nearestXXXDay(int dayOfWeek) {
        // positive or negative difference -6..0..+6
        int diff = dayOfWeek - this.dayOfWeek();
        if (diff >= 4) {
            diff -= 7;
        } else if (diff <= -4) {
            diff += 7;
        }
        return new BigDate(ordinal + diff);
    }


    /**
     * Set the ordinal field, and compute the equivalent internal Gregorian yyyy mm dd fields. alias setOrdinal.
     * 
     * @param ordinal days since 1970-01-01.
     */
    public final void set(int ordinal) {
        if (this.ordinal == ordinal) {
            return;
        }
        this.ordinal = ordinal;
        toGregorian();
    }// end set


    /**
     * Set the yyyy mm dd Gregorian fields, and compute the internal ordinal equivalent. yyyy mm dd are checked for
     * validity.
     * 
     * @param yyyy -999,999 (BC) to +999,999 (AD)
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param dd day 1 to 31, no lead 0.
     */
    public final void set(int yyyy, int mm, int dd) {
        set(yyyy, mm, dd, CHECK);
    }


    /**
     * Set the Gregorian fields, and compute the ordinal equivalent with the same modifiers CHECK, NORMALIZE,
     * BYPASSCHECK as the constructor. BEWARE! In Java a lead 0 on an integer implies OCTAL.
     * 
     * @param yyyy -999,999 (BC) to +999,999 (AD)
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param dd day 1 to 31, no lead 0.
     * @param how one of CHECK BYPASSCHECK NORMALIZE NORMALISE
     * @see #CHECK
     * @see #BYPASSCHECK
     * @see #NORMALIZE
     * @see #NORMALISE
     */
    public final void set(int yyyy, int mm, int dd, int how) {
        if (this.yyyy == yyyy && this.mm == mm && this.dd == dd) {
            return;
        }
        this.yyyy = yyyy;
        this.mm = mm;
        this.dd = dd;
        switch (how) {
            case CHECK:
                if (!isValid(yyyy, mm, dd)) {
                    throw new IllegalArgumentException("invalid date: " + yyyy + "/" + mm + "/" + dd);
                }
                break;

            case NORMALISE:
                normalise();
                break;

            case BYPASSCHECK:
                break;
        }// end switch

        toOrdinal();
    }// end set


    /**
     * Sets the date that corresponding to a given utc timestamp at a given TimeZone.
     * 
     * @param utcTimestamp milliseconds since 1970 in UTC time. E.g. Date.getTime
     * @param timeZone Timezone you want to know the date in at that time. TimeZone.getDefault() TimeZone.getU
     */
    public void setDateAtTime(long utcTimestamp, TimeZone timeZone) {
        // get date approximately right.
        setOrdinal((int) (utcTimestamp / 86400000L));

        /*
         * Gets the time zone offset, for current date, modified in case of daylight savings. This is the offset to add
         * *to* UTC to get local time. Places east of Greenwich have a positive offset. :era the era of the given date,
         * AD = 1 BC = 0 :year the year in the given date. Docs are unclear if 1900=0 or 1900. Does not use year in
         * calcs anyway. :month the month in the given date. Month is 0-based. e.g., 0 for January. :day the
         * day-in-month of the given date. :dayOfWeek the day-of-week of the given date. 1=Calendar.SUNDAY :milliseconds
         * the millis in day in <em>standard</em> local time. :return the offset in millis to add *to* GMT to get local
         * time.
         */
        int offsetInMillis = timeZone.getOffset(getOrdinal() >= Jan_01_0001 ? 1 : 0, getYYYY(), getMM() - 1, getDD(),
                getCalendarDayOfWeek(), (int) (utcTimestamp % 86400000L)
        /* wrong! Should be local standard time. How to get it?? */);
        // do a correction
        setOrdinal((int) ((utcTimestamp + offsetInMillis) / 86400000L));
    }


    /**
     * Set the ordinal field, and compute the equivalent internal Gregorian yyyy mm dd fields. alias set.
     * 
     * @param ordinal days since 1970-01-01.
     */
    public final void setOrdinal(int ordinal) {
        if (this.ordinal == ordinal) {
            return;
        }
        this.ordinal = ordinal;
        toGregorian();
    }// end setOrdinal


    /**
     * Convert date to a human-readable String [wed mm/dd/yy]
     * 
     * @return this BigDate as a String in form fri 12/31/03
     */
    public String toDowMMDDYY() {
        if (ordinal == NULL_ORDINAL) {
            return "";
        }
        return dayAbbr[getDayOfWeek()] + " " + StringTools.toLZ(mm, 2) + "/" + StringTools.toLZ(dd, 2) + "/"
                + StringTools.toLZ(yyyy % 100, 2);
    }// end toDowMMDDYY


    /**
     * Convert date to a human-readable String. Handles BC with trailing _BC.
     * 
     * @return this BigDate as a String in form YYYY-MM-DD ISO 8601:1988 international standard format. NOT final so you
     *         can override to suit yourself.
     */
    @Override
    public String toString() {
        if (ordinal == NULL_ORDINAL) {
            return "";
        }
        StringBuilder sb = new StringBuilder(13);
        sb.append(Integer.toString(Math.abs(yyyy)));
        sb.append("-");
        sb.append(StringTools.toLZ(mm, 2));
        sb.append("-");
        sb.append(StringTools.toLZ(dd, 2));
        if (yyyy < 1000) {
            if (yyyy < 0) {
                sb.append(" BC");
            } else {
                // 0 .. 999
                sb.append(" AD");
            }
        }

        return sb.toString();
    }// end toString


    /**
     * Convert just year part of date to a human-readable String. Handles BC with trailing BC.
     * 
     * @return this BigDate as a String in form YYYYBC or yyyyy
     */
    public String toYYYYString() {
        if (ordinal == NULL_ORDINAL) {
            return "";
        }
        StringBuilder sb = new StringBuilder(7);
        sb.append(Integer.toString(Math.abs(yyyy)));
        if (yyyy < 1000) {
            if (yyyy < 0) {
                sb.append(" BC");
            } else {
                // 0 .. 999
                sb.append(" AD");
            }
        }
        return sb.toString();
    }// end toYYYYString

    // -------------------------- STATIC METHODS --------------------------

    static {
        /*
         * Initialisation order is tricky so we collect all the dependencies here. This way no matter how fields are
         * ordered, initialisation still works.
         */
        NORMALIZE = NORMALISE;
        OJC_lastYYYY = isBritish ? 1752 : 1582;
        OJC_lastMM = isBritish ? 9 : 10;
        OJC_lastDD = isBritish ? 2 : 4;
        GC_firstYYYY = isBritish ? 1752 : 1582;
        GC_firstMM = isBritish ? 9 : 10;
        GC_firstDD = isBritish ? 14 : 15;
        Leap100RuleYYYY = ((GC_firstYYYY + 99) / 100) * 100;
        Leap400RuleYYYY = ((GC_firstYYYY + 399) / 400) * 400;
        missingDays = GC_firstDD - OJC_lastDD - 1;
        AD_epochAdjustment = -719529;
        BC_epochAdjustment = AD_epochAdjustment + 365;
        MIN_ORDINAL = BigDate.toOrdinal(MIN_YEAR, 1, 1);
        Jan_01_0001BC = BigDate.toOrdinal(-1, 1, 1);
        Jan_01_0001 = BigDate.toOrdinal(1, 1, 1);
        Jan_01_0004 = BigDate.toOrdinal(4, 1, 1);
        GC_firstOrdinal = BigDate.toOrdinal(GC_firstYYYY, GC_firstMM, GC_firstDD);
        GC_firstDec_31 = BigDate.toOrdinal(GC_firstYYYY, 12, 31);
        Jan_01_Leap100RuleYear = BigDate.toOrdinal(Leap100RuleYYYY, 1, 1);
        Jan_01_Leap400RuleYear = BigDate.toOrdinal(Leap400RuleYYYY, 1, 1);
        MAX_ORDINAL = BigDate.toOrdinal(MAX_YEAR, 12, 31);
    }

    static {
        // <clinit> static initialisation code for usual_dddToMMTable
        usual_dddToMMTable = new int[365];
        int dddi = 0;
        for (int mmi = 1; mmi <= 12; mmi++) {
            int last = daysInMonth(mmi, false);
            for (int ddi = 0; ddi < last; ddi++) {
                usual_dddToMMTable[dddi++] = mmi;
            }// end for dd
        }// end for mmm
    }// end static init

    static {
        // <clinit> static initialisation code for leap_dddToMMTable
        leap_dddToMMTable = new int[366];
        int dddi = 0;
        for (int mmi = 1; mmi <= 12; mmi++) {
            int last = daysInMonth(mmi, true);
            for (int ddi = 0; ddi < last; ddi++) {
                leap_dddToMMTable[dddi++] = mmi;
            }
        }
    }// end static init


    // P R O T E C T E D M E T H O D S

    /**
     * How many days were there in the year prior to the first day of the given month?
     * 
     * @param mm month 1 to 12 (not 0 to 11 as in Sun's Date), no lead 0.
     * @param leap true if you are interested in a leap year.
     * @return how many days in year prior to the start of that month.
     */
    protected static int daysInYearPriorToMonth(int mm, boolean leap) {
        return leap ? leap_daysInYearPriorToMonthTable[mm - 1] : usual_daysInYearPriorToMonthTable[mm - 1];
    }// end daysPriorToMonth


    /**
     * Convert day number ddd in year to month.
     * 
     * @param ddd day number in year Jan 01 = 1, 1 to 366.
     * @param leap true if year of interest is boolean.
     * @return month that day number would fall in.
     */
    protected static int dddToMM(int ddd, boolean leap) {
        return leap ? leap_dddToMMTable[ddd - 1] : usual_dddToMMTable[ddd - 1];
    }


    /**
     * Ordinal date of Jan 01 of the given year.
     * 
     * @param yyyy year of interest
     * @return ordinal of Jan 01 of that year.
     */
    protected static int jan01OfYear(int yyyy) {
        int leapAdjustment;
        if (yyyy < 0) {
            // years -1 -5 -9 were leap years.
            // adjustment -1->1 -2->1 -3->1 -4->1 -5->2 -6->2
            leapAdjustment = (3 - yyyy) / 4;
            return (yyyy * 365) - leapAdjustment + BC_epochAdjustment;
        }

        // years 4 8 12 were leap years
        // adjustment 1->0 2->0, 3->0, 4->0, 5->1, 6->1, 7->1, 8->1, 9->2
        leapAdjustment = (yyyy - 1) / 4;

        int missingDayAdjust = (yyyy > GC_firstYYYY) ? missingDays : 0;

        // mod 100 and mod 400 rules started in 1600 for Gregorian,
        // but 1800/2000 in the British scheme
        if (yyyy > Leap100RuleYYYY) {
            leapAdjustment -= (yyyy - Leap100RuleYYYY + 99) / 100;
        }
        if (yyyy > Leap400RuleYYYY) {
            leapAdjustment += (yyyy - Leap400RuleYYYY + 399) / 400;
        }

        return yyyy * 365 + leapAdjustment - missingDayAdjust + AD_epochAdjustment;
    }// end jan01OfYear


    /**
     * parse a string into yyyy mm dd, where date may have form: yyyy-mm-dd y-mm-ddBC 2009-12-31 1234-5-31AD 1970/3/3 AD
     * 3.01.12 bc 2000_12_31 It will not accept a date missing the year, month or day field. Spaces and commas are
     * ignored. It does does not check leap year validity, max days in month.
     * 
     * @param yyyy_mm_dd date string.
     * @return array[] yyyy, mm, dd, with yyyy negative for BC. null for unparseable date.
     */
    private static int[] relaxedParse(final String yyyy_mm_dd) {
        try {
            // A regex would be lot simpler, but we can't use it since we are constrained to JDK 1.1

            // extract the year
            final StringBuilder yearSB = new StringBuilder(6);
            final int yyyy_mm_dd_length = yyyy_mm_dd.length();
            String mm_dd = "";
            extractYearLoop: for (int i = 0; i < yyyy_mm_dd_length; i++) {
                char c = yyyy_mm_dd.charAt(i);
                switch (c) {
                    case '-':
                    case '.':
                    case '/':
                    case '_':
                        // hit field terminator
                        mm_dd = yyyy_mm_dd.substring(i + 1);
                        break extractYearLoop;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        yearSB.append(c);
                        break;
                    case ' ':
                    case ',':
                        // ignore spaces and commas
                        break;
                    default:
                        // hit something that should not be there
                        return null;
                }
            }
            if (!(1 <= yearSB.length() && yearSB.length() <= 6)) {
                return null;
            }
            final int yyyy = Integer.parseInt(yearSB.toString());

            // extract the month
            final StringBuilder monthSB = new StringBuilder(2);
            final int mm_dd_length = mm_dd.length();
            String dd_ad = "";
            extractMonthLoop: for (int i = 0; i < mm_dd_length; i++) {
                char c = mm_dd.charAt(i);
                switch (c) {
                    case '-':
                    case '.':
                    case '/':
                    case '_':
                        // hit field terminator
                        dd_ad = mm_dd.substring(i + 1);
                        break extractMonthLoop;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        monthSB.append(c);
                        break;
                    case ' ':
                    case ',':
                        // ignore spaces and commas
                        break;
                    default:
                        // hit something that should not be there
                        return null;
                }
            }
            if (!(1 <= monthSB.length() && monthSB.length() <= 2)) {
                return null;
            }
            final int mm = Integer.parseInt(monthSB.toString());

            // extract the day
            final StringBuilder daySB = new StringBuilder(2);
            final int dd_ad_length = dd_ad.length();
            String ad = "";
            extractDayLoop: for (int i = 0; i < dd_ad_length; i++) {
                char c = dd_ad.charAt(i);
                switch (c) {
                    case 'a':
                    case 'A':
                    case 'b':
                    case 'B':
                        // hit AD/BC
                        ad = dd_ad.substring(i);
                        break extractDayLoop;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        daySB.append(c);
                        break;
                    case ' ':
                    case ',':
                        // ignore spaces and commas
                        break;
                    default:
                        // hit something that should not be there
                        return null;
                }
            }
            if (!(1 <= daySB.length() && daySB.length() <= 2)) {
                return null;
            }
            final int dd = Integer.parseInt(daySB.toString());
            if (!(1 <= dd && dd <= 31)) {
                return null;
            }

            // extract the ad/bc
            final StringBuilder adSB = new StringBuilder(2);
            final int ad_length = ad.length();

            // extract AD loop
            for (int i = 0; i < ad_length; i++) {
                char c = ad.charAt(i);
                switch (c) {
                    case 'A':
                    case 'B':
                    case 'C':
                    case 'D':
                        // AD BC
                        adSB.append(c);
                        break;
                    case 'a':
                    case 'b':
                    case 'c':
                    case 'd':
                        // ad bc convert to AD BC
                        adSB.append(Character.toUpperCase(c));
                        break;
                    case ' ':
                    case ',':
                        // ignore spaces and commas
                        break;
                    default:
                        // hit something that should not be there
                        return null;
                }
            }
            final String adString = adSB.toString();
            if (adString.length() == 0 || adString.equals("AD")) {
                return new int[] { yyyy, mm, dd };
            } else if (adString.equals("BC")) {
                return new int[] { -yyyy, mm, dd };
            } else {
                return null;
            }
        } catch (NumberFormatException e) {
            // should never happen since we have already filtered out any junk
            return null;
        }
    }


    // -------------------------- OTHER METHODS --------------------------

    /**
     * Clean up an invalid date, leaving the results internally e.g. 1954-09-31 -> 1954-10-01. 1954-10-(minus 1) ->
     * 1954-09-30 1954-13-01 -> 1955-01-01. This lets you do year, month or day arithmetic. normalise does not recompute
     * the ordinal.
     */
    protected final void normalise() {
        // yyyy, mm, dd must be set at this point
        if (isValid(yyyy, mm, dd)) {
            return;
        } else if (mm > 12) {
            yyyy += (mm - 1) / 12;
            mm = ((mm - 1) % 12) + 1;
            if (isValid(yyyy, mm, dd)) {
                return;
            }
        } else if (mm <= 0) {
            // Java's definition of modulus means we need to handle negatives as
            // a special case.
            yyyy -= -mm / 12 + 1;
            mm = 12 - (-mm % 12);
            if (isValid(yyyy, mm, dd)) {
                return;
            }
        }
        if (isValid(yyyy, mm, 1)) {
            int olddd = dd;
            dd = 1;
            toOrdinal();
            ordinal += olddd - 1;
            toGregorian();
            if (isValid(yyyy, mm, dd)) {
                return;
            }
        }

        throw new IllegalArgumentException("date cannot be normalised: " + yyyy + "/" + mm + "/" + dd);
    }// end normalise


    /**
     * read back a serialized BigDate object and reconstruct the missing transient fields. readObject leaves results in
     * this. It does not create a new object.
     * 
     * @param s stream to read from.
     * @throws IOException if can't read object
     * @throws ClassNotFoundException if unexpected class in the stream.
     */
    private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
        s.defaultReadObject();
        try {
            toGregorian();// restore transient fields
        } catch (IllegalArgumentException e) {
            throw new java.io.IOException("bad serialized BigDate");
        }
    }// end readObject


    /**
     * converts ordinal to YYYY MM DD, leaving results internally.
     */
    protected final void toGregorian() {
        // ordinal must be set at this point.

        // handle the null date as a special case
        if (ordinal == NULL_ORDINAL) {
            yyyy = 0;
            mm = 0;
            dd = 0;
            return;
        }
        if (ordinal > MAX_ORDINAL) {
            throw new IllegalArgumentException("invalid ordinal date: " + ordinal);
        } else if (ordinal >= GC_firstOrdinal) {
            yyyy = Leap400RuleYYYY + flooredMulDiv(ordinal - Jan_01_Leap400RuleYear, 10000, 3652425);/*
                                                                                                      * 365 + 0.25 -
                                                                                                      * 0.01 - 0.0025
                                                                                                      */
            /*
             * division may be done on a negative number. That's ok. We don't need to mess with the 100RuleYear. The
             * 400RuleYear handles it all.
             */
        } else if (ordinal >= Jan_01_0001) {
            // Jan_01_0001 to Oct_04_1582
            // year 4 was first AD leap year.
            yyyy = 4 + flooredMulDiv(ordinal - Jan_01_0004, 100, 36525);
            /* 365 + 0.25 */
        } else if (ordinal >= MIN_ORDINAL) {
            // LowestDate to Dec_31_0001BC
            // -1 was first BC leap year.
            // dividend will be negative
            yyyy = -1 + flooredMulDiv(ordinal - Jan_01_0001BC, 100, 36525);
            /* 365 + 0.25 */
        } else {
            throw new IllegalArgumentException("invalid ordinal date: " + ordinal);
        }

        int aim = ordinal + 1;

        // Oct_15_1582 <= ordinal && ordinal <= Dec_31_1582
        if (GC_firstOrdinal <= ordinal && ordinal <= GC_firstDec_31) {
            aim += missingDays;
        }

        // ddd should be 1..366
        int ddd = aim - jan01OfYear(yyyy);
        while (ddd <= 0) {
            // our approximation was too high
            yyyy--;
            ddd = aim - jan01OfYear(yyyy);
        }
        boolean leap = isLeap(yyyy);
        while (ddd > (leap ? 366 : 365)) {
            // our approximation was too low
            yyyy++;
            ddd = aim - jan01OfYear(yyyy);
            leap = isLeap(yyyy);
        }

        mm = dddToMM(ddd, leap);
        dd = ddd - daysInYearPriorToMonth(mm, leap);

        // at this point yyyy, mm and dd have been computed.
    }// end toGregorian


    /**
     * Convert date in form YYYY MM DD into days since the epoch, leaving results internally.
     * 
     * @see #getOrdinal()
     */
    protected final void toOrdinal() {
        ordinal = toOrdinal(yyyy, mm, dd);
    }// end toOrdinal


    /**
     * Serialize and write the BigDate object. Only the ordinal will be written. Other fields are transient.
     * 
     * @param s stream to write to
     * @throws IOException standard
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
    }// end writeObject
}
