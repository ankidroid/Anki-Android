/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.Preferences.Companion.getDayOffset
import com.ichi2.anki.exception.ConfirmModSchemaException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesTest : RobolectricTest() {
    private lateinit var preferences: Preferences

    @Before
    fun setup() {
        preferences = Preferences()
        preferences.attachBaseContext(targetContext)
    }

    @Test
    fun testDayOffsetExhaustive() {
        for (i in 0..23) {
            preferences.setDayOffset(i)
            assertThat(getDayOffset(col), equalTo(i))
        }
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun testDayOffsetExhaustiveV2() {
        col.changeSchedulerVer(2)
        for (i in 0..23) {
            preferences.setDayOffset(i)
            assertThat(getDayOffset(col), equalTo(i))
        }
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun setDayOffsetSetsConfig() {
        col.changeSchedulerVer(2)
        val offset = getDayOffset(col)
        assertThat("Default offset should be 4", offset, equalTo(4))
        preferences.setDayOffset(2)
        assertThat("rollover config should be set to new value", col.get_config("rollover", 4.toInt()), equalTo(2))
    }
}
