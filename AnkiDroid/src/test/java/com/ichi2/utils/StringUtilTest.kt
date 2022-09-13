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
package com.ichi2.utils

import com.ichi2.utils.StringUtil.toTitleCase
import com.ichi2.utils.StringUtil.trimRight
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import kotlin.test.assertEquals

class StringUtilTest {
    @Test
    fun trimRightNullIsSetToNull() {
        assertThat(trimRight(null), nullValue())
    }

    @Test
    fun trimRightWhiteSpaceIsBlankString() {
        assertEquals(trimRight(" "), "")
    }

    @Test
    fun trimRightOnlyTrimsRight() {
        assertEquals(trimRight(" a "), " a")
    }

    @Test
    fun trimRightDoesNothingOnTrimmedString() {
        val input = " foo"
        assertThat(trimRight(input), sameInstance(input))
    }

    @Test
    fun toTitleCase_null_is_null() {
        assertThat(toTitleCase(null), nullValue())
    }

    @Test
    fun toTitleCase_single_letter() {
        assertEquals(toTitleCase("a"), "A")
    }

    @Test
    fun toTitleCase_single_upper_letter() {
        assertEquals(toTitleCase("A"), "A")
    }

    @Test
    fun toTitleCase_string() {
        assertEquals(toTitleCase("aB"), "Ab")
    }
}
