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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.HtmlUtils.escape
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class HtmlUtilsTest {
    @Test
    fun japaneseIsNotEscaped() {
        assertThat(escape("飲む"), equalTo("飲む"))
    }

    @Test
    fun angleBraceIsEscaped() {
        assertThat(escape("<"), equalTo("&lt;"))
    }

    @Test
    fun ampersandIsEscaped() {
        assertThat(escape("&"), equalTo("&amp;"))
    }

    @Test
    fun newLineIsNotEscaped() {
        assertThat(escape("\n"), equalTo("\n"))
    }

    @Test
    fun returnIsNotEscaped() {
        assertThat(escape("\r"), equalTo("\r"))
    }
}
