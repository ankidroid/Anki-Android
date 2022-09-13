/*
 *  Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>
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

import com.ichi2.testutils.addTempFile
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelativeFilePathTest {
    @Test
    fun test_distinct_base() {
        val file = DiskFile.createInstance(createTransientDirectory().addTempFile("fileName"))!!
        val dir = Directory.createInstance(createTransientDirectory())!!
        assertNull(RelativeFilePath.fromPaths(dir, file), "If file is not in dir, fromPaths should return null.")
    }

    @Test
    fun test_recursive_file() {
        val base = createTransientDirectory()
        val subDir = base.createTransientDirectory("sub")
        val file = subDir.addTempFile("fileName")
        val relative = RelativeFilePath.fromPaths(base, file)!!
        assertEquals(relative.fileName, "fileName")
        assertThat(relative.path, hasSize(1))
        assertEquals(relative.path[0], "sub")
        checkBasePlusRelativeEqualsExpected(base, relative, file)
    }

    @Test
    fun test_move_file() {
        val source = createTransientDirectory()
        val destination = createTransientDirectory()
        val subDir = source.createTransientDirectory("sub")
        val file = subDir.addTempFile("fileName")
        val relative = RelativeFilePath.fromPaths(source, file)!!
        assertEquals(relative.fileName, "fileName")
        assertThat(relative.path, hasSize(1))
        assertEquals(relative.path[0], "sub")
        checkBasePlusRelativeEqualsExpected(destination, relative, File(File(destination, "sub"), "fileName"))
    }

    @Test
    fun test_prepend_directory() {
        val source = createTransientDirectory()
        val destination = createTransientDirectory()
        val subDir = source.createTransientDirectory("sub")
        val file = subDir.addTempFile("fileName")
        val relative = RelativeFilePath.fromPaths(source, file)!!
        val prepended = relative.unsafePrependDirectory("testing")
        assertEquals(prepended.fileName, "fileName")
        assertThat(prepended.path, hasSize(2))
        assertEquals(prepended.path[0], "testing")
        assertEquals(prepended.path[1], "sub")
        checkBasePlusRelativeEqualsExpected(destination, prepended, File(File(File(destination, "testing"), "sub"), "fileName"))
    }

    companion object {
        fun checkBasePlusRelativeEqualsExpected(baseDir: File, relative: RelativeFilePath, expected: File) {
            assertEquals(relative.toFile(Directory.createInstance(baseDir)!!), expected)
        }
    }
}
