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
package com.ichi2.compat

import android.os.Build
import androidx.annotation.RequiresApi
import com.ichi2.testutils.assertThrowsSubclass
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.createTransientFile
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@RequiresApi(api = Build.VERSION_CODES.O) // This requirement is necessary for compilation. However, it still allows to test CompatV21
@RunWith(Parameterized::class)
class createDirectoriesTest(compat: Compat, unitTestDescription: String) : Test21And26(compat, unitTestDescription) {

    @Test
    fun creating() {
        val dir = createTransientDirectory()
        dir.delete()
        val subfolder = File(File(dir, "subdirectory"), "subsubdirectory")
        compat.createDirectories(subfolder)
        MatcherAssert.assertThat("subfolder should exists", subfolder.exists())
        MatcherAssert.assertThat("subfolder should be a directory", subfolder.isDirectory)
    }

    @Test
    fun createFile() {
        val file = createTransientFile()
        val exception = assertThrowsSubclass<IOException> { compat.createDirectories(file) }
        if (compat is CompatV26) {
            MatcherAssert.assertThat("The exception should be NotDirectoryException", exception, Matchers.instanceOf(java.nio.file.FileAlreadyExistsException::class.java))
            val notDirectoryException = exception as java.nio.file.FileAlreadyExistsException
            MatcherAssert.assertThat("The file that is not a directory should be [file]", notDirectoryException.file, Matchers.equalTo(file.toString()))
        } else {
            // V21
            MatcherAssert.assertThat("The exception should be NotDirectoryException", exception, Matchers.instanceOf(Compat.NotDirectoryException::class.java))
            val notDirectoryException = exception as Compat.NotDirectoryException
            MatcherAssert.assertThat("The file that is not a directory should be [file]", notDirectoryException.file, Matchers.equalTo(file))
        }
    }

    @Test
    fun createFileSubfolder() {
        val file = createTransientFile()
        val subfolder = spy(File(File(file, "subdirectory"), "subsubdirectory"))
        val exception = assertThrowsSubclass<IOException> { compat.createDirectories(subfolder) }
        MatcherAssert.assertThat("subfolder should not exists", !subfolder.exists())
        MatcherAssert.assertThat("The exception should be IOException", exception, Matchers.instanceOf(IOException::class.java))
    }

    @Test
    fun NoDirectoryParent() {
        val dir = createTransientDirectory()
        val subfolder = spy(File(File(dir, "subdirectory"), "subsubdirectory")) {
            doReturn(null).whenever(it).parentFile
            val path = spy(it.toPath()) {
                doReturn(null).whenever(it).parent
            }
            doReturn(path).whenever(it).toPath()
        }
        val exception = assertThrowsSubclass<IOException> { compat.createDirectories(subfolder) }
        MatcherAssert.assertThat("subfolder should not exists", !subfolder.exists())
        MatcherAssert.assertThat("The exception should be IOException", exception, Matchers.instanceOf(IOException::class.java))
    }

    @Test
    fun creationFailure() {
        val dir = createTransientDirectory()
        lateinit var path: Path
        val subfolder = spy(File(File(dir, "subdirectory"), "subsubdirectory")) {
            doReturn(false).whenever(it).mkdir()
            path = spy(it.toPath()) {
                doReturn(null).whenever(it).parent
            }
            doReturn(path).whenever(it).toPath()
        }
        val exception = Mockito.mockStatic(Files::class.java).use { filesMock ->
            filesMock.`when`<Files> { Files.createDirectories(path) }.doThrow(IOException())
            assertThrowsSubclass<IOException> { compat.createDirectories(subfolder) }
        }
        MatcherAssert.assertThat("The exception should be IOException", exception, Matchers.instanceOf(IOException::class.java))
    }

    @Test
    fun existingDirectory() {
        val dir = createTransientDirectory()
        compat.createDirectories(dir)
        // This should not throw
    }
}
