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

package com.ichi2.utils;

import org.junit.Test;

import static com.ichi2.utils.StringUtil.trimRight;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StringUtilTest {

    @Test
    public void trimRightNullIsSetToNull() {
        assertThat(trimRight(null), is(nullValue()));
    }

    @Test
    public void trimRightWhiteSpaceIsBlankString() {
        assertThat(trimRight(" "), is(""));
    }

    @Test
    public void trimRightOnlyTrimsRight() {
        assertThat(trimRight(" a "), is(" a"));
    }

    @Test
    public void trimRightDoesNothingOnTrimmedString() {
        String input = " foo";
        assertThat(trimRight(input), sameInstance(input));
    }

    @Test
    public void strip_on_null_return_null() {
        assertNull(StringUtil.strip(null));
    }

    @Test
    public void strip_on_empty_return_empty() {
        assertEquals("", StringUtil.strip(""));
    }


    @Test
    public void string_on_nonempty_full_of_whitespace_return_empty() {
        assertEquals("", StringUtil.strip(" \u0020\u2001  "));
    }


    @Test
    public void strip_leading_spaces() {
        assertEquals("Hello", StringUtil.strip("   \t\n Hello"));
    }

    @Test
    public void strip_trailing_spaces() {
        assertEquals("MyNameIs", StringUtil.strip("MyNameIs\n\u2009\u205F\t   \u3000 "));
    }


    @Test
    public void strip_trailing_and_leading_spaces() {
        assertEquals("Tarek", StringUtil.strip("\n\u2006 \r\n\t\u000CTarek   \u0009"));
    }

    @Test
    public void strip_does_nothing_on_stripped_string() {
        assertEquals("Java", StringUtil.strip("Java"));
    }

    @Test
    public void toTitleCase_null_is_null() {
        assertThat(StringUtil.toTitleCase(null), nullValue());
    }

    @Test
    public void toTitleCase_single_letter() {
        assertThat(StringUtil.toTitleCase("a"), is("A"));
    }

    @Test
    public void toTitleCase_single_upper_letter() {
        assertThat(StringUtil.toTitleCase("A"), is("A"));
    }

    @Test
    public void toTitleCase_string() {
        assertThat(StringUtil.toTitleCase("aB"), is("Ab"));
    }
}
