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

import android.annotation.SuppressLint
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.compat.Test21And26
import com.ichi2.testutils.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NotDirectoryException
import kotlin.test.assertFailsWith

/**
 * Test for [MoveDirectoryContent]
 */
class MoveDirectoryContentTest : Test21And26(), OperationTest {

    override val executionContext = MockMigrationContext()

    @Test
    fun test_one_operation() {
        val source = createTransientDirectory()
            .withTempFile("foo.txt")
        val destinationDirectory = createTransientDirectory()

        val moveOperation = moveDirectoryContent(source, destinationDirectory)
        val result1 = moveOperation.execute()
        assertThat("First result should have two operations", result1, hasSize(2))
        assertThat("First result's first operation should be MoveFileOrDirectory", result1[0], instanceOf(MoveFileOrDirectory::class.java))
        val moveFoo = result1[0] as MoveFileOrDirectory
        assertThat("First result's second operation should have source foo.txt", moveFoo.sourceFile.name, equalTo("foo.txt"))
        assertThat("First result's second operation should be move_operation", result1[1], equalTo(moveOperation))

        val result2 = moveOperation.execute()
        assertThat("Second result should have no operation", result2, hasSize(0))
    }

    @Test
    fun test_success_integration_test_recursive() {
        val source = createTransientDirectory().withTempFile("tmp.txt")
        val moreFiles = source.createTransientDirectory("more files").withTempFile("tmp-2.txt")
        val destinationDirectory = createTransientDirectory()

        executeAll(moveDirectoryContent(source, destinationDirectory))

        assertThat("source directory should exist", source.exists(), equalTo(true))
        assertThat("destination directory should exist", destinationDirectory.exists(), equalTo(true))
        assertThat("tmp.txt should be deleted at source", File(source, "tmp.txt").exists(), equalTo(false))
        assertThat("tmp.txt should be copied", File(destinationDirectory, "tmp.txt").exists(), equalTo(true))

        val subdirectory = File(destinationDirectory, "more files")
        assertThat("'more file' should be deleted at source", moreFiles.exists(), equalTo(false))
        assertThat("subdir was copied", subdirectory.exists(), equalTo(true))
        assertThat("tmp-2.txt file was deleted at source", File(moreFiles, "tmp-2.txt").exists(), equalTo(false))
        assertThat("tmp-2.txt file was copied", File(subdirectory, "tmp-2.txt").exists(), equalTo(true))
    }

    @Test
    fun a_move_failure_is_not_fatal() {
        val source = createTransientDirectory()
            .withTempFile("foo.txt")
            .withTempFile("bar.txt")
            .withTempFile("baz.txt")

        assertThat("foo should exists", File(source, "foo.txt").exists(), equalTo(true))
        val destinationDirectory = createTransientDirectory()

        // Use variables as we don't know which file will be returned in the middle from listFiles()
        executionContext.logExceptions = true
        val spyMoveDirectoryContent = OperationTest.SpyMoveDirectoryContent(moveDirectoryContent(source, destinationDirectory))
        val moveDirectoryContent = spyMoveDirectoryContent.spy
        executeAll(moveDirectoryContent)
        assertThat("Should have done three moves", spyMoveDirectoryContent.movesProcessed, equalTo(3))

        assertThat(executionContext.exceptions, hasSize(1))
        executionContext.exceptions[0].run {
            assertThat(this, instanceOf(TestException::class.java))
        }

        assertThat("source directory should not be deleted", source.exists(), equalTo(true))
        assertThat("fail (${spyMoveDirectoryContent.failedFile!!.absolutePath}) was not copied", spyMoveDirectoryContent.failedFile!!.exists(), equalTo(true))
        assertThat("file before failure was copied", spyMoveDirectoryContent.beforeFile!!.exists(), equalTo(false))
        assertThat("file after failure was copied", spyMoveDirectoryContent.afterFile!!.exists(), equalTo(false))
    }

    @Test
    fun adding_file_during_move_is_not_fatal() {
        adding_during_move_helper {
            return@adding_during_move_helper it.addTempFile("new_file.txt", "new file")
        }
    }

    @Test
    fun adding_directory_during_move_is_not_fatal() {
        adding_during_move_helper {
            val new_directory = File(it, "subdirectory")
            assertThat("Subdirectory is created", new_directory.mkdir())
            new_directory.deleteOnExit()
            return@adding_during_move_helper new_directory
        }
    }

    /**
     * Test moving a directory with two files. [toDoBetweenTwoFilesMove] is executed before moving the second file and return a new file/directory it generated in source directly (not in a subdirectory).
     * This new file/directory must be present in source or destination.
     *
     */
    fun adding_during_move_helper(toDoBetweenTwoFilesMove: (source: File) -> File) {
        val source = createTransientDirectory()
            .withTempFile("foo.txt")
            .withTempFile("bar.txt")

        val destinationDirectory = generateDestinationDirectoryRef()
        var new_file_name: String? = null

        executionContext.attemptRename = false
        executionContext.logExceptions = true
        var movesProcessed = 0
        val operation = spy(MoveDirectoryContent.createInstance(Directory.createInstance(source)!!, destinationDirectory)) {
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
        executeAll(operation)

        assertThat(
            "new_file should be present in source or directory",
            File(source, new_file_name!!).exists() || File(destinationDirectory, new_file_name!!).exists()
        )
    }

    /**
     * Checking that `MoveDirectoryContent` don't delete the source directory.
     * Deleting the source directory is the responsibility of `MoveDirectory`
     */
    @Test
    fun empty_directory_is_not_deleted() {
        val source = createTransientDirectory()
        val destinationDirectory = generateDestinationDirectoryRef()

        executeAll(moveDirectoryContent(source, destinationDirectory))

        assertThat("source directory should not be deleted", source.exists(), equalTo(true))
    }

    @Test
    fun factory_on_missing_directory_throw() {
        val source = createTransientDirectory()
        val sourceDirectory = Directory.createInstance(source)!!
        val destinationDirectory = generateDestinationDirectoryRef()
        source.delete()
        assertFailsWith<FileNotFoundException> { moveDirectoryContent(sourceDirectory, destinationDirectory) }
    }

    @SuppressLint("NewApi") // NotDirectoryException
    @Test
    fun factory_on_file_throw() {
        val source_file = createTransientFile()
        val dir = Directory.createInstanceUnsafe(source_file)
        val destinationDirectory = generateDestinationDirectoryRef()
        val ex = assertFailsWith<IOException> { moveDirectoryContent(dir, destinationDirectory) }
        if (isV26) {
            assertThat("Starting at API 26, this should be a NotDirectoryException", ex, instanceOf(NotDirectoryException::class.java))
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
        permissionDenied.assertThrowsWhenPermissionDenied { MoveDirectoryContent.createInstance(permissionDenied.directory, createTransientFile()) }
    }

    private fun moveDirectoryContent(source: Directory, destinationDirectory: File): MoveDirectoryContent {
        return MoveDirectoryContent.createInstance(source, destinationDirectory)
    }

    private fun moveDirectoryContent(source: File, destinationDirectory: File): MoveDirectoryContent {
        return moveDirectoryContent(Directory.createInstance(source)!!, destinationDirectory)
    }
}
