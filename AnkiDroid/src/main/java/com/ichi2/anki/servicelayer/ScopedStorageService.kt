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

package com.ichi2.anki.servicelayer

import android.content.Context
import android.content.SharedPreferences
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.model.RelativeFilePath
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData
import com.ichi2.annotations.NeedsTest
import timber.log.Timber
import java.io.File

/** A path to the AnkiDroid directory, named "AnkiDroid" by default */
typealias AnkiDroidDirectory = Directory

/**
 * Returns the relative file path from a given [AnkiDroidDirectory]
 * @return null if the file was not inside the directory, or referred to the root directory
 */
fun AnkiDroidDirectory.getRelativeFilePath(file: DiskFile): RelativeFilePath? =
    RelativeFilePath.fromPaths(
        baseDir = this,
        file = file
    )

object ScopedStorageService {
    /**
     * Preference listing the [AnkiDroidDirectory] where a scoped storage migration is occurring from
     *
     * This directory should exist if the preference is set
     *
     * If this preference is set and non-empty, then a [migration of user data][MigrateUserData] should be occurring
     * @see userMigrationIsInProgress
     * @see UserDataMigrationPreferences
     */
    const val PREF_MIGRATION_SOURCE = "migrationSourcePath"

    /**
     * Preference listing the [AnkiDroidDirectory] where a scoped storage migration is migrating to.
     *
     * This directory should exist if the preference is set
     *
     * This preference exists to decouple scoped storage migration from the `deckPath` variable: there are a number
     * of reasons that `deckPath` could change, and it's a long-term risk to couple the two operations
     *
     * If this preference is set and non-empty, then a [migration of user data][MigrateUserData] should be occurring
     * @see userMigrationIsInProgress
     * @see UserDataMigrationPreferences
     */
    const val PREF_MIGRATION_DESTINATION = "migrationDestinationPath"

    /**
     * Whether a user data scoped storage migration is taking place
     * This refers to the [MigrateUserData] operation of copying media which can take a long time.
     *
     * @throws IllegalStateException If either [PREF_MIGRATION_SOURCE] or [PREF_MIGRATION_DESTINATION] is set (but not both)
     * It is a logic bug if only one is set
     */
    fun userMigrationIsInProgress(context: Context): Boolean =
        userMigrationIsInProgress(AnkiDroidApp.getSharedPrefs(context))

    /**
     * Whether a user data scoped storage migration is taking place
     * This refers to the [MigrateUserData] operation of copying media which can take a long time.
     *
     * @see userMigrationIsInProgress[Context]
     * @throws IllegalStateException If either [PREF_MIGRATION_SOURCE] or [PREF_MIGRATION_DESTINATION] is set (but not both)
     * It is a logic bug if only one is set
     */
    fun userMigrationIsInProgress(preferences: SharedPreferences) =
        UserDataMigrationPreferences.createInstance(preferences).migrationInProgress

    /**
     * Checks if current directory being used by AnkiDroid to store user data is a Legacy Storage Directory.
     * This directory is stored under [CollectionHelper.PREF_DECK_PATH] in SharedPreferences
     * @return `true` if AnkiDroid is storing user data in a Legacy Storage Directory.
     */
    @NeedsTest("may want unit testing")
    fun isLegacyStorage(context: Context): Boolean {
        val currentDirPath = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        val internalScopedDirPath = CollectionHelper.getAppSpecificInternalAnkiDroidDirectory(context)
        val currentDir = File(currentDirPath).canonicalFile
        val externalScopedDirs = CollectionHelper.getAppSpecificExternalDirectories(context).map { it.canonicalFile }
        val internalScopedDir = File(internalScopedDirPath).canonicalFile
        Timber.i(
            "isLegacyStorage(): current dir: %s\nscoped external dirs: %s\nscoped internal dir: %s",
            currentDirPath, externalScopedDirs.joinToString(", "), internalScopedDirPath
        )

        // Loop to check if the current AnkiDroid directory or any of its parents are the same as the root directories
        // for app-specific external or internal storage - the only directories which will be accessible without
        // permissions under scoped storage
        val scopedDirectories = externalScopedDirs + internalScopedDir
        var currentDirParent: File? = currentDir
        while (currentDirParent != null) {
            for (scopedDir in scopedDirectories) {
                if (currentDirParent.compareTo(scopedDir) == 0) {
                    return false
                }
            }
            currentDirParent = currentDirParent.parentFile?.canonicalFile
        }

        // If the current AnkiDroid directory isn't a sub directory of the app-specific external or internal storage
        // directories, then it must be in a legacy storage directory
        return true
    }

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
        companion object {
            /**
             * @throws IllegalStateException If either [PREF_MIGRATION_SOURCE] or [PREF_MIGRATION_DESTINATION] is set (but not both)
             * It is a logic bug if only one is set
             */
            fun createInstance(preferences: SharedPreferences): UserDataMigrationPreferences {
                fun getValue(key: String) = preferences.getString(key, "")!!

                return UserDataMigrationPreferences(
                    source = getValue(PREF_MIGRATION_SOURCE),
                    destination = getValue(PREF_MIGRATION_DESTINATION)
                ).also {
                    // ensure that both are set, or both are empty
                    if (it.source.isEmpty() != it.destination.isEmpty()) {
                        // throw if there's a mismatch + list the key -> value pairs
                        val message =
                            "'$PREF_MIGRATION_SOURCE': '${getValue(PREF_MIGRATION_SOURCE)}'; " +
                                "'$PREF_MIGRATION_DESTINATION': '${getValue(PREF_MIGRATION_DESTINATION)}'"
                        throw IllegalStateException("Expected either all or no migration directories set. $message")
                    }
                }
            }
        }
    }
}
