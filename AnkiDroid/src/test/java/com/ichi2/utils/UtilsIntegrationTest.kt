/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.utils

import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.timeQuantityNextIvl
import com.ichi2.anki.utils.timeQuantityTopDeckPicker
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class, qualifiers = "en")
class UtilsIntegrationTest : RobolectricTest() {
    @Test
    fun deckPickerTimeOneAndHalfHours() {
        val oneAndAHalfHours = 60 * 90
        val actual = deckPickerTime(oneAndAHalfHours.toLong())
        MatcherAssert.assertThat(actual, Matchers.equalTo("1 h 30 m"))
    }

    @Test
    fun deckPickerTimeOneHour() {
        val oneAndAHalfHours = 60 * 60
        val actual = deckPickerTime(oneAndAHalfHours.toLong())
        MatcherAssert.assertThat(actual, Matchers.equalTo("1 h 0 m"))
    }

    @Test
    fun deckPickerTime60Seconds() {
        val oneAndAHalfHours = 60
        val actual = deckPickerTime(oneAndAHalfHours.toLong())
        MatcherAssert.assertThat(actual, Matchers.equalTo("1 min"))
    }

    @Test
    fun deckPickerTimeOneAndAHalfDays() {
        val oneAndAHalfHours = 60 * 60 * 36
        val actual = deckPickerTime(oneAndAHalfHours.toLong())
        MatcherAssert.assertThat(actual, Matchers.equalTo("1 d 12 h"))
    }

    @Test
    @Config(qualifiers = "en")
    fun timeQuantityMonths() {
        // Anki Desktop 2.1.30: '\u206810.8\u2069 months'
        MatcherAssert.assertThat(timeQuantityNextInterval(28080000), Matchers.equalTo("10.8 mo"))
    }

    private fun timeQuantityNextInterval(time_s: Int): String {
        return timeQuantityNextIvl(targetContext, time_s.toLong())
    }

    @CheckResult
    private fun deckPickerTime(time: Long): String {
        return timeQuantityTopDeckPicker(targetContext, time)
    }
}
