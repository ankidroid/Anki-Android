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

import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.MissingDirectoryException.MissingFile
import com.ichi2.testutils.*
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.emptyCollectionOf
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import timber.log.Timber
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class MoveFileTest(private val attemptRename: Boolean) : RobolectricTest(), OperationTest {
    companion object {
        @Suppress("unused")
        @Parameters(name = "attemptRename = {0}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> {
            return listOf(arrayOf(true), arrayOf(false))
        }
    }

    override val executionContext: MockMigrationContext = MockMigrationContext()

    @Test
    fun move_file_is_success() {
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")
        val size = source.length()

        MoveFile(source, destinationFile)
            .execute()

        assertFalse(source.file.exists(), "source file should no longer exist")
        assertTrue(destinationFile.exists(), "destination file should exist")

        assertEquals(getContent(destinationFile), "hello-world", "content should be copied")
        assertProgressReported(size)
    }

    @Test
    fun move_file_twice_throws_no_exception() {
        // For example: if an operation was preempted by a file transfer for the Reviewer
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")
        val size = source.length()

        MoveFile(source, destinationFile).execute()
        MoveFile(source, destinationFile).execute()

        assertFalse(source.file.exists(), "source file should no longer exist")
        assertTrue(destinationFile.exists(), "destination file should exist")

        assertEquals(getContent(destinationFile), "hello-world", "content should be copied")
        assertProgressReported(size)
    }

    @Test
    fun if_copy_does_not_move_then_no_issues() {
        if (attemptRename) return // not relevant
        // if the move doesn't work, do not delete the source file
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")

        executionContext.logExceptions = true
        spy(MoveFile(source, destinationFile)) {
            Mockito.doAnswer { Timber.w("testing: do nothing on copy") }.whenever(it).copyFile(any(), any())
        }
            .execute()

        assertFalse(destinationFile.exists(), "copy should have failed, destination should not exist")
        assertTrue(source.file.exists(), "source file should still exist")

        val exception = getSingleThrownException()
        assertThat(exception.message, containsString("Failed to copy file to"))
    }

    @Test
    fun copy_exception_does_not_cause_issues() {
        if (attemptRename) return // not relevant
        // if the move doesn't work, do not delete the source file
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")

        // this is correct - exception is not in a logged context
        executionContext.logExceptions = true
        val exception = assertThrows<TestException> {
            spy(MoveFile(source, destinationFile)) {
                Mockito.doThrow(TestException("test-copyFile()")).whenever(it).copyFile(any(), any())
            }
                .execute()
        }
        assertFalse(destinationFile.exists(), "copy should have failed, destination should not exist")
        assertTrue(source.file.exists(), "source file should still exist")
        assertEquals(getContent(source.file), "hello-world", "source content is unchanged")
        assertThat(exception.message, containsString("test-copyFile()"))
    }

    @Test
    fun no_op_if_both_files_deleted_but_directory_exists() {
        // if the move doesn't work, do not delete the source file
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")
        // we make a `DiskFile` which doesn't exist - class is in a bad state
        source.file.delete()
        assertFalse(destinationFile.exists(), "destination should not exist")
        assertFalse(source.file.exists(), "source file should not exist")

        MoveFile(source, destinationFile)
            .execute()

        assertThat("no exceptions should have been reported", executionContext.exceptions, emptyCollectionOf(Exception::class.java))
        assertEquals(executionContext.progress.single(), 0L, "empty progress should have been reported")
    }

    @Test
    fun source_is_deleted_if_both_files_are_the_same() {
        // This can happen if a power off occurs between copying the file, and cleaning up the source
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")
        source.file.copyTo(destinationFile, overwrite = false)

        val size = source.length()

        assertTrue(destinationFile.exists(), "destination should exist")
        assertTrue(source.file.exists(), "source file should exist")

        MoveFile(source, destinationFile)
            .execute()

        assertFalse(source.file.exists(), "source file should be deleted")
        assertTrue(destinationFile.exists(), "destination file should not be deleted")
        assertEquals(executionContext.progress.single(), size, "progress was reported")
    }

    @Test
    fun if_both_files_same_and_delete_throws_exception() {
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")
        source.file.copyTo(destinationFile, overwrite = false)

        executionContext.logExceptions = true
        spy(MoveFile(source, destinationFile)) {
            Mockito.doThrow(TestException("test-deleteFile()")).whenever(it).deleteFile(any())
        }
            .execute()

        assertTrue(source.file.exists(), "source should still exist")
        assertTrue(destinationFile.exists(), "destination should still exist")

        val ex = executionContext.exceptions.single()
        assertThat(ex.message, containsString("test-deleteFile()"))
        assertThat("no progress - file was not deleted", executionContext.progress, hasSize(0))
    }

    @Test
    fun error_if_copied_file_already_exists_and_is_different() {
        // This can happen if someone adds a file in the new directory before the old directory
        // is copied
        val source = addUntrackedMediaFile("hello-oo", listOf("hello.txt"))
        val destinationFile = addUntrackedMediaFile("world", listOf("world.txt")).file

        executionContext.logExceptions = true
        MoveFile(source, destinationFile)
            .execute()

        val conflictException = getSingleExceptionOfType<FileConflictException>()
        assertEquals(conflictException.source.file.canonicalPath, source.file.canonicalPath, "source is correct")
        assertEquals(conflictException.destination.file.canonicalPath, destinationFile.canonicalPath, "destination is correct")

        assertEquals(getContent(source.file), "hello-oo", "source content is unchanged")
        assertEquals(getContent(destinationFile), "world", "destination content is unchanged")
    }

    @Test
    fun error_if_source_and_destination_are_same() {
        val source = addUntrackedMediaFile("hello-oo", listOf("hello.txt"))
        val destinationFile = source.file

        val ex = assertThrows<EquivalentFileException> {
            MoveFile(source, destinationFile)
                .execute()
        }

        assertTrue(source.file.exists(), "source still exists")
        assertThat(ex.message, containsString("Source and destination path are the same"))
    }

    @Test
    fun error_if_both_files_do_not_exist_but_no_directory() {
        val sourceDirectoryToDelete = createTransientDirectory("toDelete")
        val destinationDirectoryToDelete = createTransientDirectory("toDelete")
        val sourceNotExist = DiskFile.createInstanceUnsafe(File(sourceDirectoryToDelete, "deletedDirectory-in.txt"))
        val destinationFileNotExist = File(destinationDirectoryToDelete, "deletedDirectory-out.txt")
        assertTrue(
            sourceDirectoryToDelete.delete() && destinationDirectoryToDelete.delete(),
            "deletion should work",
        )

        val exception = assertThrows<MissingDirectoryException> {
            MoveFile(sourceNotExist, destinationFileNotExist)
                .execute()
        }

        assertThat("2 missing directories expected", exception.directories, hasSize(2))
        assertEquals(exception.directories[0], MissingFile("source - parent dir", sourceDirectoryToDelete), "source was logged")
        assertEquals(exception.directories[1], MissingFile("destination - parent dir", destinationDirectoryToDelete), "destination was logged")
    }

    @Test
    fun duplicate_file_is_cleaned_up_on_rerun() {
        if (attemptRename) return // not relevant
        // if a crash/"delete" fails, we want to ensure the duplicate file is cleaned up
        val source = addUntrackedMediaFile("hello-world", listOf("hello.txt"))
        val destinationFile = File(ankiDroidDirectory(), "hello.txt")
        val size = source.length()

        executionContext.logExceptions = true
        assertThrows<TestException> {
            spy(MoveFile(source, destinationFile)) {
                Mockito.doThrow(TestException("test-deleteFile()")).whenever(it).deleteFile(any())
            }
                .execute()
        }

        Timber.d("delete should have failed")
        assertTrue(source.file.exists(), "source exists")
        assertTrue(destinationFile.exists(), "destination exists")

        MoveFile(source, destinationFile)
            .execute()

        // delete now works, BUT the file was already copied

        assertFalse(source.file.exists(), "source file should no longer exist")
        assertTrue(destinationFile.exists(), "destination file should exist")

        assertEquals(getContent(destinationFile), "hello-world", "content should be copied")
        assertProgressReported(size)
    }

    @Test
    fun move_file_to_dir_fail() {
        val source = addUntrackedMediaFile("hello", listOf("hello.txt"))
        val destination = createTransientDirectory()
        executionContext.logExceptions = true
        MoveFile(source, destination).execute()

        assertThat("An exception should be logged", executionContext.exceptions, hasSize(1))
        val exception = executionContext.exceptions[0]
        assertIs<FileDirectoryConflictException>(exception, "An exception should be of the correct type")

        assertTrue(source.file.exists(), "source file should still exist")
        assertTrue(destination.exists(), "destination file should exist")
        assertEquals(getContent(source.file), "hello", "content should not have changed")
    }

    /** Asserts that 1 element of progress of the provided size was reported */
    private fun assertProgressReported(expectedSize: Long) {
        val progress = executionContext.progress
        assertEquals(progress.size, 1, "only one progress report expected")
        assertEquals(progress.single(), expectedSize, "unexpected progress")
    }

    private fun MoveFile.execute() {
        executionContext.attemptRename = attemptRename
        val result = this.execute(executionContext)
        assertThat("No operation left after a move file", result, hasSize(0))
    }

    private fun getContent(destinationFile: File) = FileUtil.readSingleLine(destinationFile)

    private fun getSingleThrownException(): Exception {
        val exceptions = executionContext.exceptions
        assertThat("a single exception should be thrown", exceptions, hasSize(1))
        return exceptions.single()
    }

    private inline fun <reified T : Exception> getSingleExceptionOfType(): T {
        val exception = getSingleThrownException()
        assertIs<FileConflictException>(exception)
        return exception as T
    }
}
