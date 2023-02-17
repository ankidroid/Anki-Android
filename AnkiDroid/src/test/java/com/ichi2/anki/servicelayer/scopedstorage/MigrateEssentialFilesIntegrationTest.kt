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

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.annotations.NeedsTest
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.assertThrows
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.spy
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Test for [MigrateEssentialFiles.migrateEssentialFiles]
 */
@RunWith(AndroidJUnit4::class)
@NeedsTest("fails when you can't create the destination directory")
class MigrateEssentialFilesIntegrationTest : RobolectricTest() {
    /**
     * A directory in scoped storage.
     * Non existing in the file system, but with available space
     */
    private lateinit var destinationPath: File

    override fun useInMemoryDatabase(): Boolean = false

    @Before
    override fun setUp() {
        super.setUp()

        setLegacyStorage()

        // we need to access 'col' before we start
        col.basicCheck()
        destinationPath = File(Path(targetContext.getExternalFilesDir(null)!!.canonicalPath, "AnkiDroid-1").pathString)

        // arbitrary large values
        ShadowStatFs.registerStats(destinationPath, 100, 20, 10000)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ShadowStatFs.reset()
    }

    @Test
    fun migrate_essential_files_success() {
        assertMigrationNotInProgress()

        val oldDeckPath = getPreferences().getString("deckPath", "")

        migrateEssentialFiles()

        // assert the collection is open, working, and has been moved to the outPath
        assertThat(col.basicCheck(), equalTo(true))
        assertThat(col.path, equalTo(File(destinationPath, "collection.anki2").canonicalPath))

        assertMigrationInProgress()

        // assert that the preferences are updated
        val prefs = getPreferences()
        assertThat("The deck path is updated", prefs.getString("deckPath", ""), equalTo(destinationPath.canonicalPath))
        assertThat("The migration source is the original deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_SOURCE, ""), equalTo(oldDeckPath))
        assertThat("The migration destination is the deck path", prefs.getString(ScopedStorageService.PREF_MIGRATION_DESTINATION, ""), equalTo(destinationPath.canonicalPath))
    }

    @Test
    fun exception_if_not_enough_free_space_migrate_essential_files() {
        ShadowStatFs.reset()

        val ex = assertThrows<MigrateEssentialFiles.UserActionRequiredException.OutOfSpaceException> {
            migrateEssentialFiles()
        }

        assertThat(ex.message, containsString("More free space is required"))
    }

    @Test
    fun exception_if_source_already_scoped() {
        getPreferences().edit { putString(DECK_PATH, destinationPath.canonicalPath) }
        CollectionHelper.instance.setColForTests(null)

        val newDestination = File(destinationPath, "again")

        val ex = assertThrows<IllegalStateException> {
            MigrateEssentialFiles.migrateEssentialFiles(targetContext, newDestination)
        }

        assertThat(ex.message, containsString("Directory is already under scoped storage"))
    }

    @Test
    fun no_exception_if_directory_is_empty_directory_migrate_essential_files() {
        assertThat("destination should not exist ($destinationPath)", destinationPath.exists(), equalTo(false))

        assertDoesNotThrow { migrateEssentialFiles() }
    }

    @Test
    fun fails_if_destination_is_not_empty() {
        destinationPath.mkdirs()
        assertThat("destination should exist ($destinationPath)", destinationPath.exists(), equalTo(true))

        FileOutputStream(File(destinationPath, "hello.txt")).use {
            it.write(1)
        }

        val ex = assertThrows<IllegalStateException> {
            migrateEssentialFiles()
        }

        assertThat(ex.message, containsString("Target directory was not empty"))
    }

    /**
     * A race condition can occur between closing and locking the collection.
     *
     * We add a retry mechanism to confirm that this works
     */
    @Test
    fun retry_succeeds_if_race_condition_occurs() {
        var timesCalled = 0

        migrateEssentialFiles {
            Mockito.doAnswer {
                // if it's the first time, open the collection instead of checking for corruption
                // on the retry this is not true
                if (timesCalled == 0) {
                    col.basicCheck()
                }
                timesCalled++
            }.`when`(it).throwIfCollectionIsCorrupted(ArgumentMatchers.anyString())
        }

        assertThat(timesCalled, equalTo(3))
    }

    private fun migrateEssentialFiles(stubbing: (KStubbing<MigrateEssentialFiles>.(MigrateEssentialFiles) -> Unit)? = null) {
        fun mock(e: MigrateEssentialFiles): MigrateEssentialFiles {
            return if (stubbing == null) e else spy(e, stubbing)
        }
        MigrateEssentialFiles.migrateEssentialFiles(targetContext, destinationPath, ::mock)
    }
}
