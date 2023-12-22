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

package com.ichi2.anki.servicelayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.migrateEssentialFilesForTest
import com.ichi2.anki.servicelayer.scopedstorage.setLegacyStorage
import com.ichi2.libanki.Collection
import com.ichi2.testutils.ShadowStatFs
import com.ichi2.testutils.assertFalse
import com.ichi2.testutils.createTransientDirectory
import io.mockk.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ScopedStorageAnkiDroidTest : RobolectricTest() {
    override fun useInMemoryDatabase(): Boolean = false

    private fun getMigrationSourcePath() = File(col.path).parent!!

    @Test
    fun migrate_essential_files_successful() =
        runTest {
            val colPath = setupCol().path
            ShadowStatFs.markAsNonEmpty(getBestRootDirectory())
            val migratedFrom = File(colPath).parentFile!!

            val migratedTo = migrateEssentialFilesForTest(targetContext, getMigrationSourcePath())

            // close collection again so -wal doesn't end up in the list
            CollectionManager.ensureClosed()

            val from = migratedFrom.listFiles()!!.associateBy { it.name }.toMutableMap()
            val to = migratedTo.listFiles()!!.associateBy { it.name }.toMutableMap()

            assertThat("target folder name should be set", migratedTo.name, equalTo("AnkiDroid1"))
            assertThat(
                "target should be under scoped storage",
                ScopedStorageService.isLegacyStorage(migratedTo.absoluteFile, targetContext),
                equalTo(false),
            )
            assertThat("bare files should be moved", to.keys, equalTo(from.keys))
        }

    @Test
    fun migrate_essential_files_second_directory() =
        runTest {
            setupCol()
            val root = getBestRootDirectory()
            root.createTransientDirectory("AnkiDroid1")

            val destinationFile =
                migrateEssentialFilesForTest(targetContext, getMigrationSourcePath(), destOverride = DestFolderOverride.Root(root))
            assertThat(destinationFile.name, equalTo("AnkiDroid2"))
        }

    @Test
    fun migrate_essential_files_fails_on_no_available_directory() =
        runTest {
            setupCol()
            val root = getBestRootDirectory()
            for (i in 1..100) {
                root.createTransientDirectory("AnkiDroid$i")
            }

            // if "AnkiDroid100" can't be created
            assertFailsWith<NoSuchElementException> {
                migrateEssentialFilesForTest(targetContext, getMigrationSourcePath(), destOverride = DestFolderOverride.Root(root))
            }
        }

    @Test
    fun migrate_essential_files_deletes_created_directory_on_failure() =
        runTest {
            setupCol()

            val colPath = File(col.path)
            CollectionManager.ensureClosed()
            colPath.delete()

            val folder = getBestRootDirectory()

            assertFailsWith<MigrateEssentialFiles.UserActionRequiredException.MissingEssentialFileException> {
                migrateEssentialFilesForTest(
                    targetContext,
                    colPath.parent!!,
                    destOverride = DestFolderOverride.Subfolder(folder),
                )
            }

            assertFalse("folder should not exist", folder.exists())
        }

    /**
     * Accessing the collection ensure the creation of the collection.
     */
    private fun setupCol(): Collection {
        setLegacyStorage()
        ShadowStatFs.markAsNonEmpty(getBestRootDirectory())
        return col
    }

    private fun getBestRootDirectory(): File {
        val collectionPath =
            targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)!!

        // Get the scoped storage directory to migrate to. This is based on the location
        // of the current collection path
        return ScopedStorageService.getBestDefaultRootDirectory(targetContext, File(collectionPath))
    }
}
