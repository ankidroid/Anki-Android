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
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.Operation
import com.ichi2.compat.Test21And26
import com.ichi2.testutils.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NotDirectoryException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals(moveFoo.sourceFile.name, "foo.txt", "First result's second operation should have source foo.txt")
        assertEquals(result1[1], moveOperation, "First result's second operation should be move_operation")

        val result2 = moveOperation.execute()
        assertThat("Second result should have no operation", result2, hasSize(0))
    }

    @Test
    fun test_success_integration_test_recursive() {
        val source = createTransientDirectory().withTempFile("tmp.txt")
        val moreFiles = source.createTransientDirectory("more files").withTempFile("tmp-2.txt")
        val destinationDirectory = createTransientDirectory()

        executeAll(moveDirectoryContent(source, destinationDirectory))

        assertTrue(source.exists(), "source directory should exist")
        assertTrue(destinationDirectory.exists(), "destination directory should exist")
        assertFalse(File(source, "tmp.txt").exists(), "tmp.txt should be deleted at source")
        assertTrue(File(destinationDirectory, "tmp.txt").exists(), "tmp.txt should be copied")

        val subdirectory = File(destinationDirectory, "more files")
        assertFalse(moreFiles.exists(), "'more file' should be deleted at source")
        assertTrue(subdirectory.exists(), "subdir was copied")
        assertFalse(File(moreFiles, "tmp-2.txt").exists(), "tmp-2.txt file was deleted at source")
        assertTrue(File(subdirectory, "tmp-2.txt").exists(), "tmp-2.txt file was copied")
    }

    @Test
    fun a_move_failure_is_not_fatal() {
        val source = createTransientDirectory()
            .withTempFile("foo.txt")
            .withTempFile("bar.txt")
            .withTempFile("baz.txt")

        assertTrue(File(source, "foo.txt").exists(), "foo should exists")
        val destinationDirectory = createTransientDirectory()

        // Use variables as we don't know which file will be returned in the middle from listFiles()
        executionContext.logExceptions = true
        val spyMoveDirectoryContent = OperationTest.SpyMoveDirectoryContent(moveDirectoryContent(source, destinationDirectory))
        val moveDirectoryContent = spyMoveDirectoryContent.spy
        executeAll(moveDirectoryContent)
        assertEquals(spyMoveDirectoryContent.movesProcessed, 3, "Should have done three moves")

        assertThat(executionContext.exceptions, hasSize(1))
        executionContext.exceptions[0].run {
            assertThat(this, instanceOf(TestException::class.java))
        }

        assertTrue(source.exists(), "source directory should not be deleted")
        assertTrue(spyMoveDirectoryContent.failedFile!!.exists(), "fail (${spyMoveDirectoryContent.failedFile!!.absolutePath}) was not copied")
        assertFalse(spyMoveDirectoryContent.beforeFile!!.exists(), "file before failure was copied")
        assertFalse(spyMoveDirectoryContent.afterFile!!.exists(), "file after failure was copied")
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
                        override fun execute(context: MigrateUserData.MigrationContext): List<Operation> {
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

        assertTrue(source.exists(), "source directory should not be deleted")
    }

    @Test
    fun factory_on_missing_directory_throw() {
        val source = createTransientDirectory()
        val sourceDirectory = Directory.createInstance(source)!!
        val destinationDirectory = generateDestinationDirectoryRef()
        source.delete()
        assertThrows<FileNotFoundException> { moveDirectoryContent(sourceDirectory, destinationDirectory) }
    }

    @SuppressLint("NewApi") // NotDirectoryException
    @Test
    fun factory_on_file_throw() {
        val source_file = createTransientFile()
        val dir = Directory.createInstanceUnsafe(source_file)
        val destinationDirectory = generateDestinationDirectoryRef()
        val ex = assertThrowsSubclass<IOException> { moveDirectoryContent(dir, destinationDirectory) }
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
