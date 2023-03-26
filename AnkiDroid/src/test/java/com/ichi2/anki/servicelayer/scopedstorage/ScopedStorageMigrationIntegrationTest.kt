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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrationProgressListener
import com.ichi2.exceptions.AggregateException
import com.ichi2.testutils.*
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.hamcrest.io.FileMatchers.anExistingDirectory
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.test.assertFailsWith
import kotlin.test.fail

// PERF: Some of these do not need a collection
/** Test for [MigrateUserData.migrateFiles] */
@RunWith(AndroidJUnit4::class)
class ScopedStorageMigrationIntegrationTest : RobolectricTest() {

    private lateinit var underTest: MigrateUserDataTester
    private val validDestination = File(Path(targetContext.getExternalFilesDir(null)!!.canonicalPath, "AnkiDroid-1").pathString)

    override fun useInMemoryDatabase() = false

    @After
    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            ShadowStatFs.reset()
        }
    }

    @Test
    fun `Valid migration`() {
        setLegacyStorage()

        underTest = MigrateUserDataTester.create()

        // use all the real components on a real collection.
        val inputDirectory = File(col.path).parentFile!!
        File(inputDirectory, "collection.media").addTempFile("image.jpg", "foo")

        ShadowStatFs.markAsNonEmpty(validDestination)
        ShadowStatFs.markAsNonEmpty(inputDirectory)

        // migrate the essential files
        MigrateEssentialFiles.migrateEssentialFiles(targetContext, validDestination)

        underTest = MigrateUserDataTester.create(inputDirectory, validDestination)
        val result = underTest.execTask()

        assertThat("execution of user data should succeed", result, equalTo(true))

        // 5 files remain: [collection.log, collection.media.ad.db2, collection.anki2-journal, collection.anki2, .nomedia]
        underTest.integrationAssertOnlyIntendedFilesRemain()
        assertThat(underTest.migratedFilesCount, equalTo(underTest.filesToMigrateCount))

        assertThat(
            "a number of files should remain to allow the user to restore their collection",
            fileCount(inputDirectory),
            equalTo(MigrateUserDataTester.INTEGRATION_INTENDED_REMAINING_FILE_COUNT)
        )
    }

    @Test
    fun `Migration without space fails`() {
        setLegacyStorage()
        // use all the real components on a real collection.
        val inputDirectory = File(col.path).parentFile!!
        File(inputDirectory, "collection.media").addTempFile("image.jpg", "foo")
        File(inputDirectory, "collection.media").addTempFile("image2.jpg", "bar")

        ShadowStatFs.markAsNonEmpty(validDestination)
        ShadowStatFs.markAsNonEmpty(inputDirectory)

        // migrate the essential files
        MigrateEssentialFiles.migrateEssentialFiles(targetContext, validDestination)

        underTest = MigrateUserDataTester.create(inputDirectory, validDestination)
        underTest.executor = object : Executor(ArrayDeque()) {
            override fun executeOperationInternal(it: Operation, context: MigrationContext): List<Operation> {
                if (it is MoveFile) {
                    context.reportError(it, TestException("no space left on disk"))
                    return emptyList()
                }
                return super.executeOperationInternal(it, context)
            }
        }

        val aggregatedException = assertFailsWith<AggregateException> { underTest.execTask() }

        val testExceptions = aggregatedException.exceptions.filter { it !is DirectoryNotEmptyException }

        assertThat("two failed files means two exceptions", testExceptions.size, equalTo(2))

        assertThat(testExceptions[0], instanceOf(TestException::class.java))
        assertThat(testExceptions[1], instanceOf(TestException::class.java))
    }

    @Test
    fun `Empty migration passes`() {
        underTest = MigrateUserDataTester.create(createTransientDirectory(), createTransientDirectory())

        val result = underTest.execTask()

        assertThat("migrating empty directory should succeed", result, equalTo(true))
    }

    /**
     * Introduce a conflicted file
     * * it's moved to /conflict/
     * * the process succeeds
     */
    @Test
    fun `Migration with conflict is moved`() {
        underTest = MigrateUserDataTester.create()
        underTest.destination.directory.addTempFile("maybeConflicted.log", "bar")

        val result = underTest.execTask()

        assertThat("all files should be in the destination", underTest.migratedFilesCount, equalTo(underTest.filesToMigrateCount))
        assertThat("one file is conflicted", underTest.conflictedFilesCount, equalTo(1))
        assertThat("expect to have conflict/maybeConflicted.log in source (file & directory)", underTest.sourceFilesCount, equalTo(2))
        assertThat(underTest.conflictedFilePaths.single(), anyOf(endsWith("/conflict/maybeConflicted.log"), endsWith("\\conflict\\maybeConflicted.log")))

        assertThat("even with a conflict, the operation should succeed", result, equalTo(true))
    }

    @Test
    fun `Migration with file added is internally retried`() {
        underTest = MigrateUserDataTester.create()
        val executorWithNonEmpty = object : Executor(ArrayDeque()) {
            var called = false
            override fun executeOperationInternal(it: Operation, context: MigrationContext): List<Operation> {
                Timber.i("%s", it::class.java.name)
                val inner = innerOperation(it)
                if (!called && inner is DeleteEmptyDirectory && inner.directory.directory.name == "collection.media") {
                    called = true
                    context.reportError(
                        it,
                        DirectoryNotEmptyException(inner.directory)
                    )
                    return emptyList()
                }
                return super.executeOperationInternal(it, context)
            }

            fun innerOperation(op: Operation): Operation = (op as? SingleRetryDecorator)?.standardOperation ?: op
        }
        underTest.executor = executorWithNonEmpty

        underTest.execTask()

        assertThat("test exception should be raised", executorWithNonEmpty.called, equalTo(true))

        assertThat(
            "collection media should be deleted on retry if empty",
            File(underTest.source.directory, "collection.media"),
            not(anExistingDirectory())
        )

        assertThat("no external retries should be made", underTest.externalRetries, equalTo(0))
    }

    @Test
    fun `Migration with temporary problem is externally retried`() {
        underTest = MigrateUserDataTester.create()
        // Define an 'out of space' error, and the 'retry' will solve this
        val executorWithNonEmpty = object : Executor(ArrayDeque()) {
            val shouldFail get() = underTest.externalRetries == 0
            override fun executeOperationInternal(it: Operation, context: MigrationContext): List<Operation> {
                if (shouldFail) {
                    context.reportError(it, TestException("testing"))
                    return emptyList()
                }
                return super.executeOperationInternal(it, context)
            }
        }
        underTest.executor = executorWithNonEmpty

        val result = underTest.execTask()

        assertThat("operation should succeed", result, equalTo(true))
        assertThat("an external retry occurred", underTest.externalRetries, equalTo(1))
    }

    private fun MigrateUserDataTester.execTask(): Boolean {
        return this.migrateFiles(mock())
    }
}

/**
 * @param filesToMigrateCount The number of files which should be migrated
 */
private class MigrateUserDataTester
private constructor(source: Directory, destination: Directory, val filesToMigrateCount: Int) :
    MigrateUserData(source, destination) {

    override fun initializeContext(progress: MigrationProgressListener): UserDataMigrationContext {
        return super.initializeContext(progress).apply {
            attemptRename = false
        }
    }

    fun integrationAssertOnlyIntendedFilesRemain() {
        if (sourceFilesCount == INTEGRATION_INTENDED_REMAINING_FILE_COUNT) {
            return
        }
        fail("expected directory with 5 files, got: " + source.directory.listFiles()!!.map { it.name })
    }

    private val conflictDirectory = File(source.directory, "conflict")

    /** The number of files in [destination] */
    val migratedFilesCount: Int get() = fileCount(destination.directory)

    /** The number of files in [source] */
    val sourceFilesCount: Int get() = fileCount(source.directory)

    /** The number of files in the "conflict" directory */
    val conflictedFilesCount: Int get() {
        if (!conflictDirectory.exists()) {
            return 0
        }
        return fileCount(conflictDirectory)
    }

    /**
     * Lists the files in the TOP LEVEL directory of /conflict/
     * Throws if [conflictDirectory] does not exist
     */
    val conflictedFilePaths: List<String> get() {
        check(conflictDirectory.exists()) { "$conflictDirectory should exist" }
        return conflictDirectory.listFiles()!!.map { it.path }
    }

    companion object {
        // media DB created on demand, and no -journal file in new backend
        val INTEGRATION_INTENDED_REMAINING_FILE_COUNT: Int = if (BackendFactory.defaultLegacySchema) 5 else 3

        /**
         * A MigrateUserDataTest from inputSource to inputDestination (or transient directories if not provided)
         *
         * If [inputSource] is null, it is created and with the following contents:
         * * ./foo.txt`, `./bar.txt`
         * * `maybeConflicted.log`
         * *`./collection.media/`
         * * `.collection.media/image.jpg`
         *
         * i.e. 5 files files or directories that are not part of AnkiDroid's essential files.
         */
        fun create(inputSource: File? = null, inputDestination: File? = null): MigrateUserDataTester {
            val destination = inputDestination ?: createTransientDirectory("destination")

            val source = inputSource ?: createTransientDirectory("source").apply {
                addTempFile("foo.txt", "foo")
                addTempFile("bar.txt", "bar")
                addTempFile("maybeConflicted.log", "maybeConflicted")
                val media = addTempDirectory("collection.media")
                media.directory.addTempFile("image.jpg", "image")
            }

            return MigrateUserDataTester(
                source = Directory.createInstance(source)!!,
                destination = Directory.createInstance(destination)!!,
                filesToMigrateCount = fileCount(source)
            ).also {
                assertThat("Conflict directory should not exist before the migration starts", it.conflictedFilesCount, equalTo(0))
            }
        }
    }
}

/**
 * Return the number of files and directories in [directory] or in one of its subdirectories; not counting [directory] itself.
 *
 * Assumes no symbolic links.
 */
private fun fileCount(directory: File): Int {
    check(directory.exists()) { "$directory must exist" }
    check(directory.isDirectory) { "$directory must be a directory" }

    val files = directory.listFiles()
    return files!!.sumOf {
        if (it.isFile) {
            return@sumOf 1
        } else {
            return@sumOf fileCount(it) + 1
        }
    }
}
