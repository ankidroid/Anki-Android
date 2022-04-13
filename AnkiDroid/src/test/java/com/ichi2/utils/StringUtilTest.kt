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

import com.ichi2.utils.StringUtil.strip
import com.ichi2.utils.StringUtil.toTitleCase
import com.ichi2.utils.StringUtil.trimRight
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.junit.JUnitAsserter.assertNull

@KotlinCleanup("Change `is`() to equalTo()")
class StringUtilTest {
    @Test
    fun trimRightNullIsSetToNull() {
        assertThat(trimRight(null), `is`(nullValue()))
    }

    @Test
    fun trimRightWhiteSpaceIsBlankString() {
        assertThat(trimRight(" "), `is`(""))
    }

    @Test
    fun trimRightOnlyTrimsRight() {
        assertThat(trimRight(" a "), `is`(" a"))
    }

    @Test
    fun trimRightDoesNothingOnTrimmedString() {
        val input = " foo"
        assertThat(trimRight(input), sameInstance(input))
    }

    @Test
    fun strip_on_null_return_null() {
        assertNull("", strip(null))
    }

    @Test
    fun strip_on_empty_return_empty() {
        assertEquals("", strip(""))
    }

    @Test
    fun string_on_nonempty_full_of_whitespace_return_empty() {
        assertEquals("", strip(" \u0020\u2001  "))
    }

    @Test
    fun strip_leading_spaces() {
        assertEquals("Hello", strip("   \t\n Hello"))
    }

    @Test
    fun strip_trailing_spaces() {
        assertEquals("MyNameIs", strip("MyNameIs\n\u2009\u205F\t   \u3000 "))
    }

    @Test
    fun strip_trailing_and_leading_spaces() {
        assertEquals("Tarek", strip("\n\u2006 \r\n\t\u000CTarek   \u0009"))
    }

    @Test
    fun strip_does_nothing_on_stripped_string() {
        assertEquals("Java", strip("Java"))
    }

    @Test
    fun toTitleCase_null_is_null() {
        assertThat(toTitleCase(null), nullValue())
    }

    @Test
    fun toTitleCase_single_letter() {
        assertThat(toTitleCase("a"), `is`("A"))
    }

    @Test
    fun toTitleCase_single_upper_letter() {
        assertThat(toTitleCase("A"), `is`("A"))
    }

    @Test
    fun toTitleCase_string() {
        assertThat(toTitleCase("aB"), `is`("Ab"))
    }
}
