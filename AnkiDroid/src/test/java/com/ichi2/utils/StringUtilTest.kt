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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test

class StringUtilTest {
    @Test
    fun toTitleCase_null_is_null() {
        assertThat(toTitleCase(null), nullValue())
    }

    @Test
    fun toTitleCase_single_letter() {
        assertThat(toTitleCase("a"), equalTo("A"))
    }

    @Test
    fun toTitleCase_single_upper_letter() {
        assertThat(toTitleCase("A"), equalTo("A"))
    }

    @Test
    fun toTitleCase_string() {
        assertThat(toTitleCase("aB"), equalTo("Ab"))
    }
}
