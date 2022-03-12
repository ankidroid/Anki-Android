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

import android.content.SharedPreferences
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.MissingDirectoryException
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserDataJvmTest.SourceType.*
import com.ichi2.testutils.assertThrows
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * A test for [MigrateUserData] which does not require Robolectric
 */
class MigrateUserDataJvmTest {

    companion object {
        private lateinit var sourceDir: String
        private lateinit var destDir: String
        private lateinit var missingDir: String

        @BeforeClass
        @JvmStatic
        fun initClass() {
            sourceDir = createTransientDirectory().canonicalPath
            destDir = createTransientDirectory().canonicalPath
            missingDir = createTransientDirectory().also { it.delete() }.canonicalPath
        }
    }

    @Test
    fun valid_instance_if_directories_exist() {
        val preferences = getScopedStorageMigrationPreferences(source = VALID_DIR, destination = VALID_DIR)
        val data = MigrateUserData.createInstance(preferences)

        assertThat("a valid task instance should be created", data, notNullValue())

        assertThat(data!!.source.directory.canonicalPath, equalTo(sourceDir))
        assertThat(data.destination.directory.canonicalPath, equalTo(destDir))
    }

    @Test
    fun no_instance_if_not_migrating() {
        val preferences = getScopedStorageMigrationPreferences(source = NOT_SET, destination = NOT_SET)
        val data = MigrateUserData.createInstance(preferences)
        assertThat("a valid task instance should not be created as we are not migrating", data, nullValue())
    }

    @Test
    fun error_if_settings_are_bad() {
        val preferences = getScopedStorageMigrationPreferences(source = NOT_SET, destination = VALID_DIR)
        val exception = assertThrows<IllegalStateException> { MigrateUserData.createInstance(preferences) }

        assertThat(exception.message, equalTo("Expected either all or no migration directories set. 'migrationSourcePath': ''; 'migrationDestinationPath': '$destDir'"))
    }

    @Test
    fun error_if_source_does_not_exist() {
        val preferences = getScopedStorageMigrationPreferences(source = MISSING_DIR, destination = VALID_DIR)
        val exception = assertThrows<MissingDirectoryException> { MigrateUserData.createInstance(preferences) }
        assertThat(exception.directories.single().file.canonicalPath, equalTo(missingDir))
    }

    @Test
    fun error_if_destination_does_not_exist() {
        val preferences = getScopedStorageMigrationPreferences(source = VALID_DIR, destination = MISSING_DIR)
        val exception = assertThrows<MissingDirectoryException> { MigrateUserData.createInstance(preferences) }
        assertThat(exception.directories.single().file.canonicalPath, equalTo(missingDir))
    }

    private fun getScopedStorageMigrationPreferences(source: SourceType, destination: SourceType): SharedPreferences {
        return mock {
            on { getString(ScopedStorageService.PREF_MIGRATION_SOURCE, "") } doReturn
                when (source) {
                    VALID_DIR -> sourceDir
                    MISSING_DIR -> missingDir
                    NOT_SET -> ""
                }
            on { getString(ScopedStorageService.PREF_MIGRATION_DESTINATION, "") } doReturn
                when (destination) {
                    VALID_DIR -> destDir
                    MISSING_DIR -> missingDir
                    NOT_SET -> ""
                }
        }
    }

    enum class SourceType {
        NOT_SET,
        MISSING_DIR,
        VALID_DIR
    }
}
