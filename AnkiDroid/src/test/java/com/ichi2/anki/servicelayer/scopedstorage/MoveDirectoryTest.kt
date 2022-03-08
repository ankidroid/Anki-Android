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

import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.Operation
import com.ichi2.testutils.*
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import java.io.File

/**
 * Test for [MoveDirectory]
 */
class MoveDirectoryTest : OperationTest {
    override val executionContext: MockMigrationContext = MockMigrationContext()

    @Test
    fun test_success_integration_test_recursive() {
        val source = createDirectory().withTempFile("tmp.txt")
        source.createTransientDirectory("more files").withTempFile("tmp-2.txt")
        val destinationFile = generateDestinationDirectoryRef()
        executionContext.attemptRename = false

        executeAll(MoveDirectory(source, destinationFile))

        assertThat("source directory should not exist", source.exists(), equalTo(false))
        assertThat("destination directory should exist", destinationFile.exists(), equalTo(true))
        assertThat("file should be copied", File(destinationFile, "tmp.txt").exists(), equalTo(true))

        val subdirectory = File(destinationFile, "more files")
        assertThat("subdir was copied", subdirectory.exists(), equalTo(true))
        assertThat("subdir file was copied", File(subdirectory, "tmp-2.txt").exists(), equalTo(true))
    }

    @Test
    fun test_fast_rename() {
        val source = createDirectory().withTempFile("tmp.txt")
        val destinationFile = generateDestinationDirectoryRef()
        executionContext.attemptRename = true

        val ret = MoveDirectory(source, destinationFile).execute()

        assertThat("fast rename returns no operations", ret, empty())
        assertThat("source directory should not exist", source.exists(), equalTo(false))
        assertThat("destination directory should exist", destinationFile.exists(), equalTo(true))
        assertThat("file should be copied", File(destinationFile, "tmp.txt").exists(), equalTo(true))
    }

    @Test
    fun failed_rename_avoids_further_renames() {
        // This is a performance optimization,
        val source = createDirectory().withTempFile("tmp.txt")
        val destinationFile = generateDestinationDirectoryRef()
        var renameCalled = 0

        executionContext.logExceptions = true
        // don't actually move the directory, or we'd have a problem
        val moveDirectory = spy(MoveDirectory(source, destinationFile)) {
            doAnswer { renameCalled++; return@doAnswer false }.`when`(it).rename(any(), any())
        }

        assertThat("rename was true", executionContext.attemptRename, equalTo(true))

        moveDirectory.execute()

        assertThat("rename is now false", executionContext.attemptRename, equalTo(false))
        assertThat("rename was called", renameCalled, equalTo(1))

        moveDirectory.execute()

        assertThat("rename was not called again", renameCalled, equalTo(1))

        assertThat(executionContext.exceptions, hasSize(0))
        assertThat("source was not copied", File(source.directory, "tmp.txt").exists(), equalTo(true))
    }

    @Test
    fun a_move_failure_is_not_fatal() {
        val source = createDirectory()
            .withTempFile("foo.txt")
            .withTempFile("bar.txt")
            .withTempFile("baz.txt")

        val destinationDirectory = generateDestinationDirectoryRef()

        executionContext.attemptRename = false
        executionContext.logExceptions = true
        val moveDirectory = MoveDirectory(source, destinationDirectory)
        val subOperations = moveDirectory.execute()
        val moveDirectoryContent = subOperations[0] as MoveDirectoryContent
        val deleteDirectory = subOperations[1]
        val spyMoveDirectoryContent = OperationTest.SpyMoveDirectoryContent(moveDirectoryContent)
        val moveDirectoryContentSpied = spyMoveDirectoryContent.spy
        executeAll(moveDirectoryContentSpied, deleteDirectory)

        assertThat(executionContext.exceptions, hasSize(2))
        executionContext.exceptions[0].run {
            assertThat(this, instanceOf(TestException::class.java))
        }
        executionContext.exceptions[1].run {
            assertThat(this.message, containsString("directory was not empty"))
        }

        assertThat("source directory should not be deleted", source.exists(), equalTo(true))
        assertThat("fail was not copied", spyMoveDirectoryContent.failedFile!!.exists(), equalTo(true))
        assertThat("file before failure was copied", spyMoveDirectoryContent.beforeFile!!.exists(), equalTo(false))
        assertThat("file after failure was copied", spyMoveDirectoryContent.afterFile!!.exists(), equalTo(false))
    }

    @Test
    fun adding_file_during_move_is_not_fatal() {
        adding_during_move_helper() {
            return@adding_during_move_helper it.directory.addTempFile("new_file.txt", "new file")
        }
    }

    @Test
    fun adding_directory_during_move_is_not_fatal() {
        adding_during_move_helper() {
            val new_directory = File(it.directory, "subdirectory")
            assertThat("Subdirectory is created", new_directory.mkdir())
            new_directory.deleteOnExit()
            return@adding_during_move_helper new_directory
        }
    }

    /**
     * Test moving a directory with two files. [toDoBetweenTwoFilesMove] is executed before moving the second file and return a new file/directory it generated in source directly (not in a subfolder).
     * This new file/directory must be present in source or destination.
     *
     */
    fun adding_during_move_helper(toDoBetweenTwoFilesMove: (source: Directory) -> File) {
        val source = createDirectory()
            .withTempFile("foo.txt")
            .withTempFile("bar.txt")

        val destinationDirectory = generateDestinationDirectoryRef()
        var new_file_name: String? = null

        executionContext.attemptRename = false
        executionContext.logExceptions = true
        var movesProcessed = 0
        val moveDirectory = MoveDirectory(source, destinationDirectory)
        val suboperations = moveDirectory.execute()
        val moveDirectoryContent = suboperations[0] as MoveDirectoryContent
        val deleteDirectory = suboperations[1]
        val moveDirectoryContentSpied = spy(moveDirectoryContent) {
            doAnswer { op ->
                val operation = op.callRealMethod() as Operation
                if (movesProcessed++ == 1) {
                    return@doAnswer object : Operation() {
                        // Create a file in `source` and then execute the original operation.
                        // It ensures a file is added after some files where already copied.
                        override fun execute(context: MigrateUserData.MigrationContext): List<Operation> {
                            new_file_name = toDoBetweenTwoFilesMove(source).name
                            return operation.execute()
                        }
                    }
                }
                return@doAnswer operation
            }.`when`(it).toMoveOperation(any())
        }

        executeAll(moveDirectoryContentSpied, deleteDirectory)

        assertThat(
            "new_file should be present in source or directory",
            File(source.directory, new_file_name!!).exists() || File(destinationDirectory, new_file_name!!).exists()
        )
    }

    @Test
    fun empty_directory_is_deleted() {
        val source = createDirectory()
        val destinationFile = generateDestinationDirectoryRef()

        executionContext.attemptRename = false

        executeAll(MoveDirectory(source, destinationFile))

        assertThat("source was deleted", source.directory.exists(), equalTo(false))
    }

    @Test
    fun empty_directory_is_deleted_rename() {
        val source = createDirectory()
        val destinationFile = generateDestinationDirectoryRef()

        executionContext.attemptRename = true

        executeAll(MoveDirectory(source, destinationFile))

        assertThat("source was deleted", source.directory.exists(), equalTo(false))
    }
}
