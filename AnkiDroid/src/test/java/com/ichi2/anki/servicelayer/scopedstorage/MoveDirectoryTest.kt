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
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.MigrationContext
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MoveDirectory
import com.ichi2.compat.Test21And26
import com.ichi2.testutils.*
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Test for [MoveDirectory]
 */
class MoveDirectoryTest : Test21And26(), OperationTest {
    override lateinit var executionContext: MockMigrationContext
    private val executor = MockExecutor { executionContext }

    @Before
    fun setUp() {
        executionContext = MockMigrationContext()
    }

    @Test
    fun test_success_integration_test_recursive() {
        val source = createTransientDirectory().withTempFile("tmp.txt")
        source.createTransientDirectory("more files").withTempFile("tmp-2.txt")
        val destinationFile = generateDestinationDirectoryRef()
        executionContext.attemptRename = false

        executeAll(moveDirectory(source, destinationFile))

        assertThat("source directory should not exist", source.exists(), equalTo(false))
        assertThat("destination directory should exist", destinationFile.exists(), equalTo(true))
        assertThat("file should be copied", File(destinationFile, "tmp.txt").exists(), equalTo(true))

        val subdirectory = File(destinationFile, "more files")
        assertThat("subdir was copied", subdirectory.exists(), equalTo(true))
        assertThat("subdir file was copied", File(subdirectory, "tmp-2.txt").exists(), equalTo(true))
    }

    @Test
    fun a_move_failure_is_not_fatal() {
        val source = createTransientDirectory()
            .withTempFile("foo.txt")
            .withTempFile("bar.txt")
            .withTempFile("baz.txt")

        val destinationDirectory = generateDestinationDirectoryRef()

        executionContext.attemptRename = false
        executionContext.logExceptions = true
        val moveDirectory = moveDirectory(source, destinationDirectory)
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
        val operation = adding_during_move_helper {
            return@adding_during_move_helper it.addTempFile("new_file.txt", "new file")
        }

        assertThat("source should not be deleted on retry", operation.source.exists(), equalTo(true))
        assertThat("additional file was not moved", File(operation.destination, "new_file.txt").exists(), equalTo(false))
    }

    @Test
    fun adding_directory_during_move_is_not_fatal() {
        val operation = adding_during_move_helper {
            val new_directory = File(it, "subdirectory")
            assertThat("Subdirectory is created", new_directory.mkdir())
            new_directory.deleteOnExit()
            return@adding_during_move_helper new_directory
        }

        assertThat("source should not be deleted on retry", operation.source.exists(), equalTo(true))
        assertThat("additional directory was not moved", File(operation.destination, "subdirectory").exists(), equalTo(false))
    }

    @Test
    fun succeeds_on_retry_after_adding_file_during_process() {
        executionContext = RetryMigrationContext { l -> executor.operations.addAll(0, l) }

        val operation = adding_during_move_helper {
            return@adding_during_move_helper it.addTempFile("new_file.txt", "new file")
        }

        assertThat("source should be deleted on retry", operation.source.exists(), equalTo(false))
        assertThat("additional file was moved", File(operation.destination, "new_file.txt").exists(), equalTo(true))
    }

    @Test
    fun succeeds_on_retry_after_adding_directory_during_process() {
        executionContext = RetryMigrationContext { l -> executor.operations.addAll(0, l) }

        val operation = adding_during_move_helper {
            val newDirectory = File(it, "subdirectory")
            assertThat("Subdirectory is created", newDirectory.mkdir())
            newDirectory.deleteOnExit()
            return@adding_during_move_helper newDirectory
        }

        assertThat("source should be deleted on retry", operation.source.exists(), equalTo(false))
        assertThat("additional directory was moved", File(operation.destination, "subdirectory").exists(), equalTo(true))
    }

    /**
     * Test moving a directory with two files. [toDoBetweenTwoFilesMove] is executed before moving the second file and return a new file/directory it generated in source directly (not in a subdirectory).
     * This new file/directory must be present in source or destination.
     *
     * @return The [MoveDirectory] which was executed
     */
    fun adding_during_move_helper(toDoBetweenTwoFilesMove: (source: File) -> File): MoveDirectory {
        val source = createTransientDirectory()
            .withTempFile("foo.txt")
            .withTempFile("bar.txt")

        val destinationDirectory = generateDestinationDirectoryRef()
        var new_file_name: String? = null

        executionContext.attemptRename = false
        executionContext.logExceptions = true
        var movesProcessed = 0
        val moveDirectory = moveDirectory(source, destinationDirectory)
        val subOperations = moveDirectory.execute()
        val moveDirectoryContent = subOperations[0] as MoveDirectoryContent
        val deleteDirectory = subOperations[1]
        val moveDirectoryContentSpied = spy(moveDirectoryContent) {
            doAnswer { op ->
                val operation = op.callRealMethod() as Operation
                if (movesProcessed++ == 1) {
                    return@doAnswer object : Operation() {
                        // Create a file in `source` and then execute the original operation.
                        // It ensures a file is added after some files where already copied.
                        override fun execute(context: MigrationContext): List<Operation> {
                            new_file_name = toDoBetweenTwoFilesMove(source).name
                            return operation.execute()
                        }
                    }
                }
                return@doAnswer operation
            }.whenever(it).toMoveOperation(any())
        }

        executor.execute(moveDirectoryContentSpied, deleteDirectory)

        assertThat(
            "new_file should be present in source or directory",
            File(source, new_file_name!!).exists() || File(destinationDirectory, new_file_name!!).exists()
        )
        return moveDirectory
    }

    @Test
    fun empty_directory_is_deleted() {
        val source = createTransientDirectory()
        val destinationFile = generateDestinationDirectoryRef()

        executionContext.attemptRename = false

        executeAll(moveDirectory(source, destinationFile))

        assertThat("source was deleted", source.exists(), equalTo(false))
    }

    @Test
    fun empty_directory_is_deleted_rename() {
        val source = createTransientDirectory()
        val destinationFile = generateDestinationDirectoryRef()

        executionContext.attemptRename = true

        executeAll(moveDirectory(source, destinationFile))

        assertThat("source was deleted", source.exists(), equalTo(false))
    }

    /**
     * Reproduces https://github.com/ankidroid/Anki-Android/issues/10358
     * Where for some reason, `listFiles` returned null on an existing directory and
     * newDirectoryStream returned `AccessDeniedException`.
     */
    @Test
    fun reproduce_10358() {
        val sourceWithPermissionDenied = createPermissionDenied()
        val destination = createTransientDirectory()
        sourceWithPermissionDenied.assertThrowsWhenPermissionDenied { executeAll(moveDirectory(sourceWithPermissionDenied.directory.directory, destination)) }
    }

    fun moveDirectory(source: File, destination: File): MoveDirectory {
        return MoveDirectory(Directory.createInstance(source)!!, destination)
    }
}
