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
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.utils.HtmlUtils.escape;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class HtmlUtilsTest {
    @Test
    public void japaneseIsNotEscaped() {
        assertThat(escape("飲む"), is("飲む"));
    }

    @Test
    public void angleBraceIsEscaped() {
        assertThat(escape("<"), is("&lt;"));
    }

    @Test
    public void ampersandIsEscaped() {
        assertThat(escape("&"), is("&amp;"));
    }

    @Test
    public void newLineIsNotEscaped() {
        assertThat(escape("\n"), is("\n"));
    }

    @Test
    public void returnIsNotEscaped() {
        assertThat(escape("\r"), is("\r"));
    }
}
