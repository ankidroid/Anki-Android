/*
 *  Copyright (c) 2026 ialwaysbeatmywifi <ialwaysbeatmywifi@gmail.com>
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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.matchesPattern
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimeUtilsTest {
    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        // We set GMT timezone for consistent test results
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"))
    }

    @After
    fun tearDown() {
        // We restore original timezone
        TimeZone.setDefault(originalTimeZone)
    }

    private val mockTime =
        MockTime(
            year = 2023,
            month = 0, // We use January (0-based)
            date = 15,
            hourOfDay = 10,
            minute = 30,
            second = 45,
            milliseconds = 500,
            step = 0,
        )

    @Test
    fun getTimestamp_format_is_correct() {
        val timestamp = getTimestamp(mockTime)
        // We expect format: yyyyMMddHHmmss
        assertThat(timestamp, matchesPattern("^\\d{14}$"))
        // We verify it starts with 20230115 for the date we set
        assertThat(timestamp.startsWith("20230115"), equalTo(true))
    }

    @Test
    fun formatAsString_less_than_one_hour() {
        val duration = 5.minutes + 30.seconds + 200.milliseconds
        val formatted = duration.formatAsString()
        assertThat(formatted, equalTo("05:30.20"))
    }

    @Test
    fun formatAsString_one_hour_or_more() {
        val duration = 1.hours + 5.minutes + 30.seconds + 200.milliseconds
        val formatted = duration.formatAsString()
        assertThat(formatted, equalTo("01:05:30.20"))
    }

    @Test
    fun formatAsString_zero_duration() {
        val duration = 0.milliseconds
        val formatted = duration.formatAsString()
        assertThat(formatted, equalTo("00:00.00"))
    }

    @Test
    fun formatAsString_milliseconds_only() {
        val duration = 500.milliseconds
        val formatted = duration.formatAsString()
        assertThat(formatted, equalTo("00:00.50"))
    }

    @Test
    fun formatAsString_seconds_only() {
        val duration = 45.seconds
        val formatted = duration.formatAsString()
        assertThat(formatted, equalTo("00:45.00"))
    }

    @Test
    fun formatAsString_multiple_hours() {
        val duration = 3.hours + 15.minutes + 45.seconds + 100.milliseconds
        val formatted = duration.formatAsString()
        assertThat(formatted, equalTo("03:15:45.10"))
    }

    @Test
    fun getDayStart_before_cutoff_returns_yesterday() {
        // We create a mock time at 3:00 AM (before 4:00 AM cutoff)
        val earlyMorningTime =
            MockTime(
                year = 2023,
                month = 0,
                date = 15,
                hourOfDay = 3, // 3 AM - before 4 AM cutoff
                minute = 30,
                second = 0,
                milliseconds = 0,
                step = 0,
            )
        val dayStart = getDayStart(earlyMorningTime)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        calendar.timeInMillis = dayStart

        // We expect it to be set to 4:00 AM of the previous day (14th)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH), equalTo(14))
        assertThat(calendar.get(Calendar.HOUR_OF_DAY), equalTo(4))
        assertThat(calendar.get(Calendar.MINUTE), equalTo(0))
        assertThat(calendar.get(Calendar.SECOND), equalTo(0))
        assertThat(calendar.get(Calendar.MILLISECOND), equalTo(0))
    }

    @Test
    fun getDayStart_after_cutoff_returns_today() {
        // We create a mock time at 5:00 AM (after 4:00 AM cutoff)
        val lateTime =
            MockTime(
                year = 2023,
                month = 0,
                date = 15,
                hourOfDay = 5, // 5 AM - after 4 AM cutoff
                minute = 30,
                second = 0,
                milliseconds = 0,
                step = 0,
            )
        val dayStart = getDayStart(lateTime)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        calendar.timeInMillis = dayStart

        // We expect it to be set to 4:00 AM of the same day (15th)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH), equalTo(15))
        assertThat(calendar.get(Calendar.HOUR_OF_DAY), equalTo(4))
        assertThat(calendar.get(Calendar.MINUTE), equalTo(0))
        assertThat(calendar.get(Calendar.SECOND), equalTo(0))
        assertThat(calendar.get(Calendar.MILLISECOND), equalTo(0))
    }

    @Test
    fun getDayStart_at_exactly_cutoff_time() {
        // We create a mock time at exactly 4:00 AM
        val cutoffTime =
            MockTime(
                year = 2023,
                month = 0,
                date = 15,
                hourOfDay = 4, // Exactly 4 AM
                minute = 0,
                second = 0,
                milliseconds = 0,
                step = 0,
            )
        val dayStart = getDayStart(cutoffTime)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        calendar.timeInMillis = dayStart

        // We expect it to be set to 4:00 AM of the same day (15th)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH), equalTo(15))
        assertThat(calendar.get(Calendar.HOUR_OF_DAY), equalTo(4))
    }

    @Test
    fun getDayStart_with_late_hour() {
        // We create a mock time at 23:59 (11:59 PM)
        val lateTime =
            MockTime(
                year = 2023,
                month = 0,
                date = 15,
                hourOfDay = 23,
                minute = 59,
                second = 59,
                milliseconds = 999,
                step = 0,
            )
        val dayStart = getDayStart(lateTime)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        calendar.timeInMillis = dayStart

        // We expect it to be set to 4:00 AM of the same day (15th)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH), equalTo(15))
        assertThat(calendar.get(Calendar.HOUR_OF_DAY), equalTo(4))
        assertThat(calendar.get(Calendar.MINUTE), equalTo(0))
    }

    @Test
    fun getDayStart_on_new_years_day_before_cutoff() {
        // We create a mock time at Jan 1st, 2026 at 3:00 AM GMT
        val newYearsMorning =
            MockTime(
                year = 2026,
                month = 0, // January
                date = 1,
                hourOfDay = 3,
                minute = 0,
                second = 0,
                milliseconds = 0,
                step = 0,
            )

        val dayStart = getDayStart(newYearsMorning)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        calendar.timeInMillis = dayStart

        // We expect Dec 31st, 2025 at 4:00 AM GMT
        assertThat(calendar.get(Calendar.YEAR), equalTo(2025))
        assertThat(calendar.get(Calendar.MONTH), equalTo(11)) // December (0-based)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH), equalTo(31))
        assertThat(calendar.get(Calendar.HOUR_OF_DAY), equalTo(4))
        assertThat(calendar.get(Calendar.MINUTE), equalTo(0))
        assertThat(calendar.get(Calendar.SECOND), equalTo(0))
        assertThat(calendar.get(Calendar.MILLISECOND), equalTo(0))
    }
}
