/*
 *  Copyright (c) 2026 Dhanush Sugganahalli <dhanush41230@gmail.com>
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

import android.icu.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class TimeUtilsTest {
    @Test
    fun getDayStart_AtJanuary1_returnsDayStart() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val input = MockTime(2021, Calendar.JANUARY, 1, 3, 0, 0, 0, 0)
            val expected = MockTime(2020, Calendar.DECEMBER, 31, 4, 0, 0, 0, 0).calendar().timeInMillis
            assertEquals(expected, getDayStart(input))
        } finally {
            TimeZone.setDefault(null)
        }
    }
}
