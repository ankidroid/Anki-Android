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

package com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata

import android.content.SharedPreferences
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_DESTINATION
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_SOURCE
import java.io.File

/**
 * Preferences relating to whether a user data scoped storage migration is taking place
 * This refers to the [MigrateUserData] operation of copying media which can take a long time.
 *
 * @param source The path of the source directory. Check [migrationInProgress] before use.
 * @param destination The path of the destination directory. Check [migrationInProgress] before use.
 */
class UserDataMigrationPreferences private constructor(val source: String, val destination: String) {
    /**  Whether a scoped storage migration is in progress */
    val migrationInProgress = source.isNotEmpty()
    val sourceFile get() = File(source)
    val destinationFile get() = File(destination)

    // Throws if migration can't occur as expected.
    fun check() {
        // ensure that both are set, or both are empty
        if (source.isEmpty() != destination.isEmpty()) {
            // throw if there's a mismatch + list the key -> value pairs
            val message =
                "'$PREF_MIGRATION_SOURCE': '$source'; " +
                    "'$PREF_MIGRATION_DESTINATION': '$destination'"
            throw IllegalStateException("Expected either all or no migration directories set. $message")
        }
    }

    companion object {
        /**
         * @throws IllegalStateException If either [PREF_MIGRATION_SOURCE] or [PREF_MIGRATION_DESTINATION] is set (but not both)
         * It is a logic bug if only one is set
         */
        fun createInstance(preferences: SharedPreferences): UserDataMigrationPreferences {
            fun getValue(key: String) = preferences.getString(key, "")!!

            return createInstance(
                source = getValue(PREF_MIGRATION_SOURCE),
                destination = getValue(PREF_MIGRATION_DESTINATION),
            )
        }

        fun createInstance(
            source: String,
            destination: String,
        ) = UserDataMigrationPreferences(source, destination).also { it.check() }
    }
}
