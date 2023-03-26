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

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.annotation.CheckResult
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.RetryableException
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.ScopedAnkiDroidDirectory
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.LockedCollection
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.UserActionRequiredException.MissingEssentialFileException
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Storage
import com.ichi2.testutils.CollectionDBCorruption
import com.ichi2.testutils.TestException
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.test.assertFailsWith

const val DECK_PATH = "deckPath"

/**
 * Test for [MigrateEssentialFiles]
 */
@RunWith(AndroidJUnit4::class)
class MigrateEssentialFilesTest : RobolectricTest() {

    override fun useInMemoryDatabase(): Boolean = false
    private lateinit var defaultCollectionSourcePath: String

    /** Whether to check the collection to ensure it's still openable */
    private var checkCollectionAfter = true

    @Before
    override fun setUp() {
        // had interference between two tests
        CollectionHelper.instance.setColForTests(null)
        super.setUp()
        defaultCollectionSourcePath = getMigrationSourcePath()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        if (checkCollectionAfter) {
            assertThat("col is still valid", col.basicCheck())
        }
    }

    @Test
    fun successful_migration() {
        assertMigrationNotInProgress()

        this.addNoteUsingBasicModel("Hello", "World")

        val collectionSourcePath = getMigrationSourcePath()

        val oldDeckPath = getPreferences().getString(DECK_PATH, "")

        val outPath = executeAlgorithmSuccessfully(collectionSourcePath)

        // assert the collection is open, working, and has been moved to the outPath
        assertThat(col.basicCheck(), equalTo(true))
        assertThat(col.path, equalTo(File(outPath, "collection.anki2").canonicalPath))

        assertMigrationInProgress()

        // assert that the preferences are updated
        val prefs = getPreferences()
        assertThat("The deck path should be updated", prefs.getString(DECK_PATH, ""), equalTo(outPath.canonicalPath))
        assertThat("The migration source should be the original deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_SOURCE, ""), equalTo(oldDeckPath))
        assertThat("The migration destination should be the deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_DESTINATION, ""), equalTo(outPath.canonicalPath))

        assertThat(".nomedia should be copied", File(outPath.canonicalPath, ".nomedia"), FileMatchers.anExistingFile())

        assertThat("The added card should still exists", col.cardCount(), equalTo(1))
    }

    @Test
    fun exception_thrown_if_migration_is_started_while_in_process() {
        getPreferences().edit {
            putString(ScopedStorageService.PREF_MIGRATION_SOURCE, defaultCollectionSourcePath)
            putString(ScopedStorageService.PREF_MIGRATION_DESTINATION, createTransientDirectory().path)
        }
        assertMigrationInProgress()

        val ex = assertFailsWith<IllegalStateException> { executeAlgorithmSuccessfully(defaultCollectionSourcePath) }

        assertThat(ex.message, containsString("Migration is already in progress"))
    }

    @Test
    fun exception_thrown_if_destination_is_not_empty() {
        // This is not handled upstream as it's a logic error - the directory passed in should be created
        val nonEmptyDestination = getMigrationDestinationPath().also {
            File(it, "tmp.txt").createNewFile()
        }

        val exception = assertFailsWith<IllegalStateException> { executeAlgorithmSuccessfully(getMigrationSourcePath(), optionalDestinationPath = nonEmptyDestination) }
        assertThat(exception.message, startsWith("destination was non-empty"))
    }

    @Test
    fun fails_if_source_path_is_not_current_ankiDroid_collection() {
        val invalidSourcePath = createTransientDirectory().absolutePath
        // preliminary check, not part of the test assertion.
        assertThat("source path should be invalid", invalidSourcePath, not(equalTo(col.path)))
        assertThat(Directory.createInstance(invalidSourcePath), notNullValue())
        val algo = getAlgorithm(invalidSourcePath, getMigrationDestinationPath())
        val exception = assertFailsWith<IllegalStateException> { algo.execute() }
        assertThat(exception.message, containsString("paths did not match"))
    }

    @Test
    fun exception_thrown_if_database_corrupt() {
        checkCollectionAfter = false
        val collectionAnki2Path = CollectionDBCorruption.closeAndCorrupt(targetContext)

        val collectionSourcePath = File(collectionAnki2Path).parent!!

        assertFailsWith<SQLiteDatabaseCorruptException> { executeAlgorithmSuccessfully(collectionSourcePath) }

        assertMigrationNotInProgress()
    }

    @Test
    fun collection_is_not_locked_if_copy_fails() {
        var called = false

        assertFailsWith<TestException> {
            executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
                Mockito
                    .doAnswer {
                        called = true
                        assertThat("collection should be locked", Storage.isLocked, equalTo(true))
                        throw TestException("")
                    }
                    .whenever(it)
                    .copyTopLevelFile(any(), any())
            }
        }

        assertThat("mock was unused", called, equalTo(true))
        assertThat("the collection is no longer locked", Storage.isLocked, equalTo(false))
    }

    @Test
    fun fails_if_collection_can_still_be_opened() {
        val ex = assertFailsWith<RetryableException> {
            executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
                Mockito.doReturn(Mockito.mock(LockedCollection::class.java)).whenever(it).createLockedCollection()
            }
        }

        assertThat(ex.message, containsString("Collection not locked correctly"))
    }

    @Test
    fun prefs_are_restored_if_reopening_fails() {
        // after preferences are set, we make one final check with these new preferences
        // if this check fails, we want to revert the changes to preferences that we made
        val collectionSourcePath = getMigrationSourcePath()

        val prefKeys = listOf(ScopedStorageService.PREF_MIGRATION_SOURCE, ScopedStorageService.PREF_MIGRATION_DESTINATION, DECK_PATH)
        val oldPrefValues = prefKeys
            .associateWith { getPreferences().getString(it, null) }

        assertFailsWith<TestException> {
            executeAlgorithmSuccessfully(collectionSourcePath) {
                Mockito.doThrow(TestException("simulating final collection open failure")).whenever(it).throwIfCollectionCannotBeOpened()
            }
        }

        oldPrefValues.forEach {
            assertThat("Pref ${it.key} should be unchanged", getPreferences().getString(it.key, null), equalTo(it.value))
        }

        assertMigrationNotInProgress()
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun fails_if_missing_essential_file() {
        val unused = col.usnForSync

        col.close() // required for Windows, can't delete if locked.

        CompatHelper.compat.deleteFile(File(defaultCollectionSourcePath, "collection.anki2"))

        val ex = assertFailsWith<MissingEssentialFileException> {
            executeAlgorithmSuccessfully(defaultCollectionSourcePath) {
                Mockito.doReturn(Mockito.mock(LockedCollection::class.java)).whenever(it).createLockedCollection()
            }
        }

        assertThat(ex.file.name, equalTo("collection.anki2"))
    }

    /**
     * Executes the collection migration algorithm, moving from the local test directory /AnkiDroid, to /migration
     * This is only the initial stage which does not delete data
     */
    private fun executeAlgorithmSuccessfully(
        ankiDroidDirectory: String,
        optionalDestinationPath: File? = null,
        stubbing: (KStubbing<MigrateEssentialFiles>.(MigrateEssentialFiles) -> Unit)? = null
    ): File {
        val destinationPath = optionalDestinationPath ?: getMigrationDestinationPath()

        var algo = getAlgorithm(ankiDroidDirectory, destinationPath)

        if (stubbing != null) {
            algo = spy(algo, stubbing)
        }
        algo.execute()

        return destinationPath
    }

    private fun getMigrationDestinationPath(): File {
        return File(Path(targetContext.getExternalFilesDir(null)!!.canonicalPath, "AnkiDroid-1").pathString).also {
            it.mkdirs()
        }
    }

    private fun getMigrationSourcePath() = File(col.path).parent!!

    @CheckResult
    private fun getAlgorithm(sourcePath: String, destinationPath: File): MigrateEssentialFiles {
        val destinationDir = Directory.createInstance(destinationPath) ?: throw IllegalStateException("'$destinationPath' was not a directory")
        val destinationDirectory = ScopedAnkiDroidDirectory.createInstance(destinationDir, targetContext) ?: throw IllegalStateException("'$destinationPath' was not under scoped storage")
        return MigrateEssentialFiles(targetContext, Directory.createInstance(sourcePath)!!, destinationDirectory)
    }
}
