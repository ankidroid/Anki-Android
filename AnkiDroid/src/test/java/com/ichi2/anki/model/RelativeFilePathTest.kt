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
import org.hamcrest.Matchers.*
import org.junit.Test
import java.io.File

class RelativeFilePathTest {
    @Test
    fun test_distinct_base() {
        val file = DiskFile.createInstance(createTransientDirectory().addTempFile("fileName"))!!
        val dir = Directory.createInstance(createTransientDirectory())!!
        assertThat("If file is not in dir, fromPaths should return null.", RelativeFilePath.fromPaths(dir, file), nullValue())
    }

    @Test
    fun test_recursive_file() {
        val base = createTransientDirectory()
        val subDir = base.createTransientDirectory("sub")
        val file = subDir.addTempFile("fileName")
        val relative = RelativeFilePath.fromPaths(base, file)!!
        assertThat(relative.fileName, equalTo("fileName"))
        assertThat(relative.path, hasSize(1))
        assertThat(relative.path[0], equalTo("sub"))
        checkBasePlusRelativeEqualsExpected(base, relative, file)
    }

    @Test
    fun test_move_file() {
        val source = createTransientDirectory()
        val destination = createTransientDirectory()
        val subDir = source.createTransientDirectory("sub")
        val file = subDir.addTempFile("fileName")
        val relative = RelativeFilePath.fromPaths(source, file)!!
        assertThat(relative.fileName, equalTo("fileName"))
        assertThat(relative.path, hasSize(1))
        assertThat(relative.path[0], equalTo("sub"))
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
        assertThat(prepended.fileName, equalTo("fileName"))
        assertThat(prepended.path, hasSize(2))
        assertThat(prepended.path[0], equalTo("testing"))
        assertThat(prepended.path[1], equalTo("sub"))
        checkBasePlusRelativeEqualsExpected(destination, prepended, File(File(File(destination, "testing"), "sub"), "fileName"))
    }

    companion object {
        fun checkBasePlusRelativeEqualsExpected(
            baseDir: File,
            relative: RelativeFilePath,
            expected: File,
        ) {
            assertThat(relative.toFile(Directory.createInstance(baseDir)!!), equalTo(expected))
        }
    }
}
