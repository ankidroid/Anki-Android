/**
 * Copyright (c) 2021 Diego Rodriguez <diego.vincent.rodriguez@gmail.com>
 *
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

@RunWith(Parameterized::class)
class TimePreferenceTest(private val parsableHour: String, private val expectedHour: Int) {
    @Test
    fun shouldParseHours() {
        val actualHour = TimePreference.parseHours(parsableHour)

        assertEquals(expectedHour, actualHour)
    }

    companion object {
        @JvmStatic // required for Parameters
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("00:00", 0),
                arrayOf("01:00", 1),
                arrayOf("24:00", 24)
            )
        }
    }
}
