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
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.setLegacyStorage
import com.ichi2.libanki.Collection
import com.ichi2.testutils.ShadowStatFs
import com.ichi2.testutils.TestException
import com.ichi2.testutils.createTransientDirectory
import io.mockk.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.io.FileMatchers
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ScopedStorageAnkiDroidTest : RobolectricTest() {

    override fun useInMemoryDatabase(): Boolean = false

    @Test
    fun migrate_essential_files_successful() {
        val colPath = setupCol().path
        ShadowStatFs.markAsNonEmpty(getBestRootDirectory())
        val migratedFrom = File(colPath).parentFile!!
        val migratedTo = ScopedStorageService.migrateEssentialFiles(targetContext)

        val from = migratedFrom.listFiles()!!.associateBy { it.name }.toMutableMap()
        val to = migratedTo.listFiles()!!.associateBy { it.name }.toMutableMap()

        assertThat("target folder name should be set", migratedTo.name, equalTo("AnkiDroid1"))
        assertThat("target should be under scoped storage", ScopedStorageService.isLegacyStorage(migratedTo.absolutePath, targetContext), equalTo(false))
        assertThat("bare files should be moved", to.keys, equalTo(from.keys))
    }

    @Test
    fun migrate_essential_files_second_directory() {
        setupCol()
        getBestRootDirectory().createTransientDirectory("AnkiDroid1")
        mockkObject(MigrateEssentialFiles) {
            val destinationFile = slot<File>()
            every { MigrateEssentialFiles.migrateEssentialFiles(any(), destination = capture(destinationFile)) } returns Unit

            ScopedStorageService.migrateEssentialFiles(targetContext)

            assertThat(destinationFile.captured.name, equalTo("AnkiDroid2"))
        }
    }

    @Test
    fun migrate_essential_files_fails_on_no_available_directory() {
        setupCol()
        for (i in 1..100) {
            getBestRootDirectory().createTransientDirectory("AnkiDroid$i")
        }

        // if "AnkiDroid100" can't be created
        assertFailsWith<NoSuchElementException> { ScopedStorageService.migrateEssentialFiles(targetContext) }
    }

    @Test
    fun migrate_essential_files_deletes_created_directory_on_failure() {
        setupCol()
        mockkObject(MigrateEssentialFiles) {
            val destinationFile = slot<File>()
            every { MigrateEssentialFiles.migrateEssentialFiles(any(), destination = capture(destinationFile)) } throws TestException("failed")

            assertFailsWith<TestException> { ScopedStorageService.migrateEssentialFiles(targetContext) }

            assertThat("destination was deleted on failure", destinationFile.captured, not(FileMatchers.anExistingDirectory()))
        }
    }

    /**
     * Accessing the collection ensure the creation of the collection.
     */
    private fun setupCol(): Collection {
        setLegacyStorage()
        return col
    }

    private fun getBestRootDirectory(): File {
        val collectionPath = AnkiDroidApp.getSharedPrefs(targetContext).getString(CollectionHelper.PREF_COLLECTION_PATH, null)!!

        // Get the scoped storage directory to migrate to. This is based on the location
        // of the current collection path
        return ScopedStorageService.getBestDefaultRootDirectory(targetContext, File(collectionPath))
    }
}
