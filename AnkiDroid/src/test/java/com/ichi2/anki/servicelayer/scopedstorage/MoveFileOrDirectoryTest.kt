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

package com.ichi2.anki.servicelayer.scopedstorage

import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MoveDirectory
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.createTransientFile
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import java.io.File

/** Tests for [MoveFileOrDirectory] */
class MoveFileOrDirectoryTest : OperationTest {
    override val executionContext = MockMigrationContext()
    private val destinationDir = createTransientDirectory()

    @Test
    fun applied_to_file_returns_file() {
        val file = createTransientFile()
        val nextOperations = moveFromAndExecute(file)
        assertThat("Only one operation should be next", nextOperations, hasSize(1))
        val nextOperation = nextOperations[0]
        assertThat("A file as input should return a file operation", nextOperation, instanceOf(MoveFile::class.java))
        val moveFile = nextOperation as MoveFile
        assertThat("Move file source should be file", moveFile.sourceFile.file, equalTo(file))
        assertThat("Destination file source should be file", moveFile.destinationFile, equalTo(File(destinationDir, file.name)))
    }

    @Test
    fun applied_to_directory_returns_directory() {
        val directory = createTransientDirectory()
        val nextOperations = moveFromAndExecute(directory)
        assertThat("Only one operation should be next", nextOperations, hasSize(1))
        val nextOperation = nextOperations[0]
        assertThat("A file as input should return a file operation", nextOperation, instanceOf(MoveDirectory::class.java))
        val moveDirectory = nextOperation as MoveDirectory
        assertThat("Move file source should be file", moveDirectory.source.directory, equalTo(directory))
        assertThat("Destination file source should be file", moveDirectory.destination, equalTo(File(destinationDir, directory.name)))
    }

    @Test
    fun applied_to_deleted_file_returns_nothing() {
        val file = createTransientFile()
        file.delete()
        val nextOperations = moveFromAndExecute(file)
        assertThat("No operations should be next as file is deleted", nextOperations, hasSize(0))
    }

    private fun moveFromAndExecute(file: File) = MoveFileOrDirectory(file, File(destinationDir, file.name)).execute()
}
