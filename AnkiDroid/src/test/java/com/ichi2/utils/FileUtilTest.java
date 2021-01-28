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

package com.ichi2.utils;

import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class FileUtilTest {

    @Test
    public void testFileNameNull() {
        assertThat(FileUtil.getFileNameAndExtension(null), nullValue());
    }

    @Test
    public void testFileNameEmpty() {
        assertThat(FileUtil.getFileNameAndExtension(""), nullValue());
    }

    @Test
    public void testFileNameNoDot() {
        assertThat(FileUtil.getFileNameAndExtension("abc"), nullValue());
    }

    @Test
    public void testFileNameNormal() {
        Map.Entry<String, String> fileNameAndExtension = FileUtil.getFileNameAndExtension("abc.jpg");
        assertThat(fileNameAndExtension.getKey(), is("abc"));
        assertThat(fileNameAndExtension.getValue(), is(".jpg"));
    }

    @Test
    public void testFileNameTwoDot() {
        Map.Entry<String, String> fileNameAndExtension = FileUtil.getFileNameAndExtension("a.b.c");
        assertThat(fileNameAndExtension.getKey(), is("a.b"));
        assertThat(fileNameAndExtension.getValue(), is(".c"));
    }
}
