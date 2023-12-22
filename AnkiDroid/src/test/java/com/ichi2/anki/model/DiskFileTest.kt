/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.createTransientFile
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File

/**
 * Tests for [DiskFile]
 */
class DiskFileTest {
    @Test
    fun passes_if_existing_file() {
        val filePath = createTransientFile()
        assertThat(
            "DiskFile should work with valid file",
            DiskFile.createInstance(filePath),
            not(nullValue()),
        )
    }

    @Test
    fun fails_if_does_not_exist() {
        val dir = createTransientDirectory()
        assertThat("DiskFile requires an existing file", DiskFile.createInstance(File(dir, "aa.txt")), nullValue())
    }

    @Test
    fun fails_if_directory() {
        val dir = createTransientDirectory()
        assertThat("directory should not be a DiskFile", DiskFile.createInstance(dir), nullValue())
    }

    @Test
    fun content_equal_different() {
        val left = createTransientDiskFile("hello")
        val right = createTransientFile("world")

        assertThat("files should not be equal", left.contentEquals(right), equalTo(false))
    }

    @Test
    fun if_both_equal_then_content_equal() {
        val left = createTransientDiskFile("hello")
        val right = createTransientFile("hello")

        assertThat("files should be equal", left.contentEquals(right), equalTo(true))
    }

    @Test
    fun content_equal_right_not_exist() {
        // we don't need to worry about "left" not existing, as this is an instance method
        val left = createTransientDiskFile("hello")
        val right = File(createTransientDirectory(), "not_exist.txt")

        assertThat("files should not be equal: right doesn't exist", left.contentEquals(right), equalTo(false))
    }

    private fun createTransientDiskFile(
        @Suppress("SameParameterValue") content: String,
    ): DiskFile {
        val left = createTransientFile(content)
        return DiskFile.createInstance(left)!!
    }
}
