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

import android.content.SharedPreferences
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_DESTINATION
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_SOURCE
import com.ichi2.anki.servicelayer.ScopedStorageService.mediaMigrationIsInProgress
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertFailsWith

class ScopedStorageServiceTest {
    @Test
    fun no_migration_by_default() {
        val preferences = getScopedStorageMigrationPreferences(setSource = false, setDestination = false)

        assertThat("migration is not in progress if neither preference set", mediaMigrationIsInProgress(preferences), equalTo(false))
    }

    @Test
    fun error_if_only_source_set() {
        val preferences = getScopedStorageMigrationPreferences(setSource = true, setDestination = false)

        val exception = assertFailsWith<IllegalStateException> { mediaMigrationIsInProgress(preferences) }
        assertThat(
            exception.message,
            equalTo(
                "Expected either all or no migration directories set. " +
                    "'migrationSourcePath': 'sample_source_path'; " +
                    "'migrationDestinationPath': ''"
            )
        )
    }

    @Test
    fun error_if_only_destination_set() {
        val preferences = getScopedStorageMigrationPreferences(setSource = false, setDestination = true)

        val exception = assertFailsWith<IllegalStateException> { mediaMigrationIsInProgress(preferences) }
        assertThat(
            exception.message,
            equalTo(
                "Expected either all or no migration directories set. " +
                    "'migrationSourcePath': ''; " +
                    "'migrationDestinationPath': 'sample_dest_path'"
            )
        )
    }

    @Test
    fun migration_if_both_set() {
        val preferences = getScopedStorageMigrationPreferences(setSource = true, setDestination = true)

        assertThat("migration is in progress if both preferences set", mediaMigrationIsInProgress(preferences), equalTo(true))
    }

    private fun getScopedStorageMigrationPreferences(setSource: Boolean, setDestination: Boolean): SharedPreferences {
        return mock {
            on { getString(PREF_MIGRATION_SOURCE, "") } doReturn if (setSource) "sample_source_path" else ""
            on { getString(PREF_MIGRATION_DESTINATION, "") } doReturn if (setDestination) "sample_dest_path" else ""
        }
    }
}
