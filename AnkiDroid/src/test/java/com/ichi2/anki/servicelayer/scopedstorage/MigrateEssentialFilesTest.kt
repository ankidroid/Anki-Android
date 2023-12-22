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

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.servicelayer.DestFolderOverride
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.servicelayer.ScopedStorageService.prepareAndValidateSourceAndDestinationFolders
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.UserActionRequiredException.MissingEssentialFileException
import com.ichi2.compat.CompatHelper
import com.ichi2.testutils.CollectionDBCorruption
import com.ichi2.testutils.createTransientDirectory
import net.ankiweb.rsdroid.BackendException
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.shadows.ShadowStatFs
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
        // arbitrary large values
        ShadowStatFs.registerStats(getMigrationDestinationPath(targetContext), 100, 20, 10000)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ShadowStatFs.reset()
        if (checkCollectionAfter) {
            assertThat("col is still valid", col.basicCheck())
        }
    }

    @Test
    fun successful_migration() =
        runTest {
            assertMigrationNotInProgress()

            addNoteUsingBasicModel("Hello", "World")

            val collectionSourcePath = getMigrationSourcePath()

            val oldDeckPath = getPreferences().getString(DECK_PATH, "")

            val outPath = migrateEssentialFilesForTest(targetContext, collectionSourcePath)

            // assert the collection is open, working, and has been moved to the outPath
            assertThat(col.basicCheck(), equalTo(true))
            assertThat(col.path, equalTo(File(outPath, "collection.anki2").canonicalPath))

            assertMigrationInProgress()

            // assert that the preferences are updated
            val prefs = getPreferences()
            assertThat("The deck path should be updated", prefs.getString(DECK_PATH, ""), equalTo(outPath.canonicalPath))
            assertThat(
                "The migration source should be the original deck path",
                prefs.getString(ScopedStorageService.PREF_MIGRATION_SOURCE, ""),
                equalTo(oldDeckPath),
            )
            assertThat(
                "The migration destination should be the deck path",
                prefs.getString(ScopedStorageService.PREF_MIGRATION_DESTINATION, ""),
                equalTo(outPath.canonicalPath),
            )

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

        val ex =
            assertFailsWith<IllegalStateException> {
                prepareAndValidateSourceAndDestinationFolders(targetContext)
            }

        assertThat(ex.message, containsString("Migration is already in progress"))
    }

    @Test
    fun exception_thrown_if_destination_is_not_empty() {
        val source = getMigrationSourcePath()
        // This is not handled upstream as it's a logic error - the directory passed in should be created
        val nonEmptyDestination =
            getMigrationDestinationPath(targetContext).also {
                File(it, "tmp.txt").createNewFile()
            }

        val exception =
            assertFailsWith<IllegalStateException> {
                prepareAndValidateSourceAndDestinationFolders(
                    targetContext,
                    sourceOverride = File(source),
                    destOverride = DestFolderOverride.Subfolder(nonEmptyDestination),
                    checkSourceDir = false,
                )
            }
        assertThat(exception.message, containsString("not empty"))
    }

    @Test
    fun exception_thrown_if_database_corrupt() =
        runTest {
            checkCollectionAfter = false
            val collectionAnki2Path = CollectionDBCorruption.closeAndCorrupt(targetContext)

            val collectionSourcePath = File(collectionAnki2Path).parent!!

            assertFailsWith<SQLiteDatabaseCorruptException> { migrateEssentialFilesForTest(targetContext, collectionSourcePath) }

            assertMigrationNotInProgress()
        }

    @Test
    fun prefs_are_restored_if_reopening_fails() =
        runTest {
            // after preferences are set, we make one final check with these new preferences
            // if this check fails, we want to revert the changes to preferences that we made
            val collectionSourcePath = getMigrationSourcePath()

            val prefKeys = listOf(ScopedStorageService.PREF_MIGRATION_SOURCE, ScopedStorageService.PREF_MIGRATION_DESTINATION, DECK_PATH)
            val oldPrefValues =
                prefKeys
                    .associateWith { getPreferences().getString(it, null) }

            CollectionManager.emulateOpenFailure = true
            assertFailsWith<BackendException.BackendDbException.BackendDbLockedException> {
                migrateEssentialFilesForTest(targetContext, collectionSourcePath)
            }
            CollectionManager.emulateOpenFailure = false

            oldPrefValues.forEach {
                assertThat("Pref ${it.key} should be unchanged", getPreferences().getString(it.key, null), equalTo(it.value))
            }

            assertMigrationNotInProgress()
        }

    @Test
    fun fails_if_missing_essential_file() =
        runTest {
            col.close() // required for Windows, can't delete if locked.

            CompatHelper.compat.deleteFile(File(defaultCollectionSourcePath, "collection.anki2"))

            val ex =
                assertFailsWith<MissingEssentialFileException> {
                    migrateEssentialFilesForTest(targetContext, defaultCollectionSourcePath)
                }

            assertThat(ex.file.name, equalTo("collection.anki2"))
        }

    private fun getMigrationSourcePath() = File(col.path).parent!!
}

/**
 * Executes the collection migration algorithm, moving from the local test directory /AnkiDroid, to /migration
 * This is only the initial stage which does not delete data
 */
suspend fun migrateEssentialFilesForTest(
    context: Context,
    ankiDroidFolder: String,
    destOverride: DestFolderOverride = DestFolderOverride.None,
    checkSourceDir: Boolean = false,
): File {
    val destOverrideUpdated =
        when (destOverride) {
            is DestFolderOverride.None -> DestFolderOverride.Root(getMigrationDestinationPath(context))
            else -> destOverride
        }
    val sourceFolder = File(ankiDroidFolder)
    val folders =
        prepareAndValidateSourceAndDestinationFolders(
            context,
            sourceOverride = sourceFolder,
            destOverride = destOverrideUpdated,
            checkSourceDir = checkSourceDir,
        )
    CollectionManager.migrateEssentialFiles(context, folders)
    return folders.scopedDestinationDirectory.directory
}

private fun getMigrationDestinationPath(context: Context): File {
    return File(Path(context.getExternalFilesDir(null)!!.canonicalPath, "AnkiDroid1").pathString).also {
        it.mkdirs()
    }
}
