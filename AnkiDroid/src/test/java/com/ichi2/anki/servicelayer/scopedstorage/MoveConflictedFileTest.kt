/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer.scopedstorage

import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.model.RelativeFilePath
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.compat.Test21And26
import com.ichi2.testutils.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.io.FileMatchers
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File
import java.io.IOException
import kotlin.test.assertFailsWith

class MoveConflictedFileTest : Test21And26(), OperationTest {

    override val executionContext: MockMigrationContext by lazy {
        MockMigrationContext()
    }

    /**
     * Comprehensive test of the function to query candidate filenames given a "template" file
     *
     * @see [MoveConflictedFile.queryCandidateFilenames]
     */
    @Test
    fun test_queryCandidateFilenames() {
        val testFile = createTransientFile()

        val sequence = MoveConflictedFile.queryCandidateFilenames(testFile).toList()

        assertThat("5 attempts should be made to find a valid file", sequence.size, equalTo(EXPECTED_ATTEMPTS))

        // Test and production uses different method to extract the base name for extra test safety
        val filename = testFile.name.substringBefore(".")

        assertThat("first element has no brackets", sequence.first().name, not(containsString("(")))
        assertThat("second element has brackets", sequence[1].name, endsWith(" (1).tmp"))
        assertThat("last element has brackets", sequence.last().name, equalTo("$filename (${EXPECTED_ATTEMPTS - 1}).tmp"))

        val final = sequence.last()

        assertThat("final file is in same directory as original file", final.parent!!, equalTo(testFile.parent))
    }

    @Test
    fun creation_fails_if_path_starts_with_conflict() {
        // the method adds /conflict/, so don't do this outside the function call
        val params = InputParameters("conflict", sourceFileName = "tmp.txt")

        val illegalStateException = assertFailsWith<IllegalStateException> { params.createOperation() }

        assertThat(illegalStateException.message, startsWith("can't move from a root path of 'conflict': "))
    }

    @Test
    fun creation_prepends_conflict_to_path() {
        val params = InputParameters("collection.media", sourceFileName = "tmp.txt")

        val operation = params.createOperation()

        assertThat("provided 'sourceFile' parameter is unchanged", operation.sourceFile.file, equalTo(params.sourceFile))

        // this is "path", but with a "conflict" subfolder.
        assertThat("'conflict' is prepended to the path", operation.proposedDestinationFile, equalTo(params.intendedDestinationFilePath))
    }

    @Test
    fun failing_to_create_directory_fails() {
        val params = InputParameters("collection.media", sourceFileName = "tmp.txt")

        // this will fail to create a directory, as we have a file named conflict.
        File(params.destinationTopLevel, "conflict").apply {
            createNewFile()
            deleteOnExit()
        }

        assertFailsWith<IOException> { params.createOperation().execute() }

        assertThat("should be no progress", executionContext.progress, hasSize(0))
    }

    @Test
    fun valid() {
        val params = InputParameters("collection.media", sourceFileName = "tmp.txt")
        assertThat("source file exists", params.sourceFile, FileMatchers.anExistingFile())
        val moveConflictedFile = params.createOperation()
        moveConflictedFile.execute()

        assertThat("source file should be removed", params.sourceFile, not(FileMatchers.anExistingFile()))
        assertThat("destination should exist", moveConflictedFile.proposedDestinationFile, FileMatchers.anExistingFile())

        assertThat("1 instance of progress", executionContext.progress, hasSize(1))
        assertThat("1 instance of progress: 0 bytes", executionContext.progress.single(), equalTo(params.contentLength))
    }

    @Test
    fun single_rename() {
        val params = InputParameters("collection.media", sourceFileName = "tmp.txt")

        params.intendedDestinationFilePath.apply {
            parentFile!!.mkdirs()
            createNewFile()
            writeText("hello") // we can't have the contents be identical
        }

        val moveConflictedFile = params.createOperation()
        moveConflictedFile.execute()

        val expectedFile = File(moveConflictedFile.proposedDestinationFile.parentFile, "tmp (1).txt")

        assertThat("source file should be removed", params.sourceFile, not(FileMatchers.anExistingFile()))
        assertThat("destination should exist with (1) in the name", expectedFile, FileMatchers.anExistingFile())

        assertThat("1 instance of progress", executionContext.progress, hasSize(1))
        assertThat("1 instance of progress: 0 bytes", executionContext.progress.single(), equalTo(params.contentLength))

        assertThat("1 instance of progress", executionContext.progress, hasSize(1))
        assertThat("1 instance of progress: 0 bytes", executionContext.progress.single(), equalTo(params.contentLength))
    }

    @Test
    fun maxed_out_rename_fails() {
        val params = InputParameters("collection.media", sourceFileName = "tmp.txt")

        // use up all the paths
        for (path in MoveConflictedFile.queryCandidateFilenames(params.intendedDestinationFilePath)) {
            path.apply {
                parentFile!!.mkdirs()
                createNewFile()
                writeText(path.nameWithoutExtension) // we can't have the contents be identical
            }
        }

        assertFailsWith<FileConflictResolutionFailedException> { params.createOperation().execute() }

        assertThat("should be no progress", executionContext.progress, hasSize(0))
    }

    @Test
    fun operation_failed_via_report() {
        // arrange
        val params = InputParameters("collection.media", sourceFileName = "tmp.txt")

        var op = params.createOperation()
        op = spy(op) {
            doAnswer<List<Operation>> { answer ->
                val context = answer.arguments[1] as MigrationContext
                context.reportError(this.mock, TestException("testing"))
                emptyList()
            }.whenever(it).moveFile(any(), any())
        }

        executionContext.logExceptions = true

        // act
        op.execute()

        // assert
        assertThat("source file should not be moved", params.sourceFile, FileMatchers.anExistingFile())
        assertThat("an error should be logged", executionContext.errors, hasSize(1))
        val error = executionContext.errors.single()

        assertThat("operation should be the wrapping operation", error.operation, instanceOf(MoveConflictedFile::class.java))
        assertThat("Exception should be thrown", error.exception, instanceOf(TestException::class.java))

        assertThat("should be no progress", executionContext.progress, hasSize(0))
    }

    @Test
    fun operation_failed_via_exception() {
        // arrange
        val params = InputParameters("collection.media", sourceFileName = "tmp.txt")

        var op = params.createOperation()
        op = spy(op) {
            doThrow(TestException("operation_failed_via_exception")).whenever(it).moveFile(any(), any())
        }

        executionContext.logExceptions = true

        // act
        op.execute()

        // assert
        assertThat("source file should not be moved", params.sourceFile, FileMatchers.anExistingFile())
        assertThat("an error should be logged", executionContext.errors, hasSize(1))
        val error = executionContext.errors.single()

        assertThat("operation should be the wrapping operation", error.operation, instanceOf(MoveConflictedFile::class.java))
        assertThat("Exception should be thrown", error.exception, instanceOf(TestException::class.java))

        assertThat("should be no progress", executionContext.progress, hasSize(0))
    }

    /**
     * Encapsulates the variables required to create a [MoveConflictedFile] instance for testing
     *
     * Generates a temporary directory for the source and destination, and sets up the file defined by
     * [directoryComponents]/[sourceFileName]
     *
     * [createOperation] returns the operation to move this source file to [destinationTopLevel]
     *
     * @param directoryComponents components to the folder holding the source file ["collection.media"]
     * @param sourceFileName The name of the source file: "file.ext"
     * @param content The content of the source file
     */
    private class InputParameters constructor(
        private vararg val directoryComponents: String,
        val sourceFileName: String,
        val content: String = "source content",
    ) {
        val sourceTopLevel: File by lazy { createTransientDirectory() }
        val sourceFile: File by lazy {
            var directory: File = sourceTopLevel
            for (component in directoryComponents) {
                directory = File(directory, component)
            }
            directory.mkdirs()
            directory.addTempFile(sourceFileName, content)
        }
        val destinationTopLevel by lazy { createTransientDirectory() }
        val destinationTopLevelDirectory get() = Directory.createInstance(destinationTopLevel)!!
        val contentLength = content.length.toLong()

        /** A [RelativeFilePath] created from [directoryComponents] and [sourceFileName]  */
        private val relativePath
            get() = RelativeFilePath.Companion.fromPaths(sourceTopLevel, sourceFile)!!

        /**
         * The intended destination of the file (assumed it did not have to be renamed due to conflict in the "conflict"'s subdirectory)
         *
         * File [destinationTopLevel]/"conflict"/[directoryComponents]/[sourceFileName]
         */
        val intendedDestinationFilePath: File get() =
            relativePath.unsafePrependDirectory("conflict").toFile(baseDir = destinationTopLevelDirectory)

        /**
         * Operation to move conflicted file [sourceFileName] from [sourceTopLevel] to [destinationTopLevel]/"conflict"/[directoryComponents]/[sourceFileName].
         */
        fun createOperation(): MoveConflictedFile {
            return MoveConflictedFile.createInstance(
                sourceFile = DiskFile.createInstance(this.sourceFile)!!,
                destinationTopLevel = destinationTopLevelDirectory,
                sourceRelativePath = relativePath
            )
        }
    }

    companion object {
        /**
         * Equal to [MoveConflictedFile.MAX_DESTINATION_NAMES]
         * Copied to tests to ensure unexpected changes cause test changes
         */
        const val EXPECTED_ATTEMPTS = 5
    }
}
