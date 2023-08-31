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

package com.ichi2.compat

import com.ichi2.testutils.*
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.test.assertFailsWith

/** Tests for [Compat.deleteFile] */
@RunWith(Parameterized::class)
class CompatDeleteFileTest(
    val compat: Compat,
    /** Used in the "Test Results" Window */
    @Suppress("unused") private val unitTestDescription: String
) {
    companion object {
        @JvmStatic // required for Parameters
        @Parameterized.Parameters(name = "{1}")
        fun data(): Iterable<Array<Any>> = sequence {
            yield(arrayOf(CompatV21(), "CompatV21"))
            yield(arrayOf(CompatV26(), "CompatV26"))
        }.asIterable()
    }

    @Test
    fun delete_file_which_exists() {
        val file = createTransientFile()
        assertDoesNotThrow { deleteFile(file) }
        assertThat("file should no longer exist", file.exists(), equalTo(false))
    }

    @Test
    fun delete_directory_which_exists() {
        val dir = createTransientDirectory()
        assertDoesNotThrow { deleteFile(dir) }
        assertThat("directory should no longer exist", dir.exists(), equalTo(false))
    }

    @Test
    fun delete_fails_if_exists_is_false() {
        val dir = createTransientDirectory()
        dir.delete()
        assertFailsWith<FileNotFoundException> { deleteFile(dir) }
    }

    @Test
    fun delete_fails_if_not_empty_directory() {
        // Note: Exception is a DirectoryNotEmptyException in V26
        val dir = createTransientDirectory().withTempFile("foo.txt")
        assertFailsWith<IOException> { deleteFile(dir) }
    }

    private fun deleteFile(file: File) = compat.deleteFile(file)
}
