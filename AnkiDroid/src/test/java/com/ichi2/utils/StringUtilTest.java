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
}
