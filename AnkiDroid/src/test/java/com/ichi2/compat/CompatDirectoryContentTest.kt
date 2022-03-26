/*
 *  Copyright (c) 2022 Arthur Milchior <Arthur@Milchior.fr>
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

package com.ichi2.compat

import android.annotation.SuppressLint
import com.ichi2.testutils.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NotDirectoryException

class CompatDirectoryContentTest : Test21And26() {

    @Test
    fun empty_dir_test() {
        val directory = createTransientDirectory()
        compat.contentOfDirectory(directory).use {
            assertThat("Iterator should not have next", it.hasNext(), equalTo(false))
        }
    }

    @Test
    fun ensure_absolute_path() {
        // Relative paths caused me hours of debugging. Never again.
        val directory = createTransientDirectory()
            .withTempFile("zero")
        val iterator = compat.contentOfDirectory(directory)
        val file = iterator.next()
        assertThat("Paths should be absolute", file.path, equalTo(file.absolutePath))
    }

    @Test
    fun dir_test_three_files() {
        val directory = createTransientDirectory()
            .withTempFile("zero")
            .withTempFile("one")
            .withTempFile("two")
        val iterator = compat.contentOfDirectory(directory)
        val found = Array(3) { false }
        for (i in 1..3) {
            assertThat("Iterator should have a $i-th element", iterator.hasNext(), equalTo(true))
            val file = iterator.next()
            val fileNumber = when (file.name) {
                "zero" -> 0
                "one" -> 1
                "two" -> 2
                else -> -1
            }
            assertThat("File ${file.name} should not be in ${directory.path}", fileNumber, not(equalTo(-1)))
            assertThat("File ${file.name} should not be listed twice", found[fileNumber], equalTo(false))
            found[fileNumber] = true
        }
        assertThat("Iterator should not have next anymore", iterator.hasNext(), equalTo(false))
        iterator.close()
    }

    @Test
    fun non_existent_dir_test() {
        val directory = createTransientDirectory()
        directory.delete()
        assertThrows<FileNotFoundException>({
            compat.contentOfDirectory(directory)
        }
        )
    }

    @SuppressLint("NewApi")
    @Test
    fun file_test() {
        val file = createTransientFile("foo")
        val exception = assertThrowsSubclass<IOException>({
            compat.contentOfDirectory(file)
        }
        )
        if (isV26) {
            assertThat("Starting at API 26, this should be a NotDirectoryException", exception, instanceOf(NotDirectoryException::class.java))
        }
    }

    /**
     * Reproduces https://github.com/ankidroid/Anki-Android/issues/10358
     * Where for some reason, `listFiles` returned null on an existing directory and
     * newDirectoryStream returned `AccessDeniedException`.
     */
    @Test
    fun reproduce_10358() {
        val permissionDenied = createPermissionDenied()
        assertThrowsSubclass<IOException> { permissionDenied.compat.contentOfDirectory(permissionDenied.directory.directory) }
    }
}
