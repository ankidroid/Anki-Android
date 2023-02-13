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
import android.os.Build
import android.os.Environment
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.model.RelativeFilePath
import com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.UserDataMigrationPreferences
import com.ichi2.utils.FileUtil.getParentsAndSelfRecursive
import com.ichi2.utils.FileUtil.isDescendantOf
import com.ichi2.utils.Permissions
import timber.log.Timber
import java.io.File

/** A path to the AnkiDroid directory, named "AnkiDroid" by default */
typealias AnkiDroidDirectory = Directory

/** A path to collection.anki2 */
typealias CollectionFilePath = String

/** The collection.anki2 CollectionFilePath of [this] AnkiDroid directory */
fun AnkiDroidDirectory.getCollectionAnki2Path(): CollectionFilePath =
    File(this.directory, CollectionHelper.COLLECTION_FILENAME).canonicalPath

/**
 * Returns the relative file path from a given [AnkiDroidDirectory]
 * @return null if the file was not inside the directory, or referred to the root directory
 */
fun AnkiDroidDirectory.getRelativeFilePath(file: DiskFile): RelativeFilePath? =
    RelativeFilePath.fromPaths(
        baseDir = this,
        file = file
    )

/**
 * An [AnkiDroidDirectory] for an AnkiDroid collection which is under scoped storage
 * This storage directory is accessible without permissions after scoped storage changes,
 * and is much faster to access
 *
 * When uninstalling: A user will be asked if they want to delete this folder
 * A folder here may be modifiable via USB. In AnkiDroid's case, all collection folders should
 * be modifiable
 *
 * @see [isLegacyStorage]
 */
class ScopedAnkiDroidDirectory private constructor(val path: AnkiDroidDirectory) {
    companion object {
        /**
         * Creates an instance of [ScopedAnkiDroidDirectory] from [directory]
         * @param directory The [AnkiDroidDirectory] which should contain the AnkiDroid collection.
         * This should not be a directory which is under the legacy (non-scoped storage) model
         *
         * @return The directory, or `null` if the provided [directory] was a [legacy directory][isLegacyStorage]
         * @see [isLegacyStorage]
         */
        fun createInstance(directory: Directory, context: Context): ScopedAnkiDroidDirectory? {
            if (isLegacyStorage(directory.directory.absolutePath, context)) {
                return null
            }

            return ScopedAnkiDroidDirectory(directory)
        }
    }
}

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
     * The maximum allowed number of 'AnkiDroid' folders
     *
     * Exists as un unreachable bound through normal activity.
     */
    private const val MAX_ANKIDROID_DIRECTORIES = 100

    /**
     * Migrates from the current directory to a directory under scoped storage
     *
     * @throws MigrateEssentialFiles.UserActionRequiredException Subclasses define user action required
     * @throws NoSuchElementException if no directory was valid
     * @throws IllegalStateException An internal error occurred. Examples:
     * * If current directory is already under scoped storage
     * * If destination is not under scoped storage
     */
    fun migrateEssentialFiles(context: Context): File {
        val collectionPath = AnkiDroidApp.getSharedPrefs(context).getString(CollectionHelper.PREF_COLLECTION_PATH, null)!!

        // Get the scoped storage directory to migrate to. This is based on the location
        // of the current collection path
        val bestRootDestination = getBestDefaultRootDirectory(context, File(collectionPath))

        // append a folder name to the root destination.
        // If the root destination was /storage/emulated/0/Android/com.ichi2.anki/files
        // we add a subfolder name to allow for more than one AnkiDroid data directory to be migrated.
        // This is useful as:
        // * Multiple installations of AnkiDroid go to different folders
        // * It will allow us to add profiles without changing directories again
        val bestProfileDirectory = (1..MAX_ANKIDROID_DIRECTORIES).asSequence()
            .map { File(bestRootDestination, "AnkiDroid$it") }
            .first { !it.exists() } // skip directories which exist

        try {
            MigrateEssentialFiles.migrateEssentialFiles(context, bestProfileDirectory)
        } catch (e: Exception) {
            try {
                // MigrateEssentialFiles performs a COPY. Delete the data so we don't take up space.
                bestProfileDirectory.deleteRecursively()
            } catch (e: Exception) {
            }
            throw e
        }
        return bestProfileDirectory
    }

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
     * Given a path, find in which app directory it is contained if any, otherwise return an arbitrary app directory
     *
     * If the file is in a non-scoped directory on the SD card, we do not want to move it to main storage
     * and vice-versa.
     *
     * @returns the external directory which best represents the template
     */
    @VisibleForTesting
    internal fun getBestDefaultRootDirectory(context: Context, templatePath: File): File {
        // List of external paths.
        val externalPaths = CollectionHelper.getAppSpecificExternalDirectories(context)
            .map { it.canonicalFile }

        // A map that associate to each parents `p` of an external directory path an external
        // directory it is a prefix of.
        // If there are multiple such external directories (which seems unlikely with 2022 scoped storage)
        // we select the first app specific external directories returned by the OS, or its parent with minimal depth
        // still contained in `p`.
        // Storing every single parent is not efficient, however, given the expected depth of files considered in this migration,
        // the extra cost is negligible.
        val parentToSharedDirectoryPath = HashMap<File, File>()
        for (externalPath in externalPaths) {
            for (parent in externalPath.getParentsAndSelfRecursive()) {
                val firstExternalPathContainedInParent = parentToSharedDirectoryPath.getOrDefault(parent, null)
                if (firstExternalPathContainedInParent != null) {
                    // We generally prefer the first shared path. So if we already found a shared path contained in this [parent]
                    // (and hence all of its parents)
                    // we prefer this already found shared path and so we can beak.
                    if (firstExternalPathContainedInParent.isDescendantOf(externalPath)) {
                        // The only exception is if the new external path is if the new external path is an ancestor of the current one.
                        // In this case, the new external path is closest to the [parent] and so we prefer it.
                    } else {
                        break
                    }
                }
                parentToSharedDirectoryPath[parent] = externalPath
            }
        }

        return templatePath.getParentsAndSelfRecursive()
            .firstNotNullOfOrNull { parent -> parentToSharedDirectoryPath.getOrDefault(parent, null) }!!
    }

    /**
     * Checks if current directory being used by AnkiDroid to store user data is a Legacy Storage Directory.
     * This directory is stored under [CollectionHelper.PREF_COLLECTION_PATH] in SharedPreferences
     * @return `true` if AnkiDroid is storing user data in a Legacy Storage Directory.
     */
    fun isLegacyStorage(context: Context): Boolean {
        return isLegacyStorage(CollectionHelper.getCurrentAnkiDroidDirectory(context), context)
    }

    /**
     * Checks if current directory being used by AnkiDroid to store user data is a Legacy Storage Directory.
     * This directory is stored under [CollectionHelper.PREF_COLLECTION_PATH] in SharedPreferences
     * @return `true` if AnkiDroid is storing user data in a Legacy Storage Directory.
     *
     * @param setCollectionPath if `false`, null is returned. This stops an infinite loop
     * if `isLegacyStorage` is called when obtaining the collection path
     */
    fun isLegacyStorage(context: Context, setCollectionPath: Boolean): Boolean? {
        if (!setCollectionPath && !AnkiDroidApp.getSharedPrefs(context).contains(CollectionHelper.PREF_COLLECTION_PATH)) {
            return null
        }
        return isLegacyStorage(CollectionHelper.getCurrentAnkiDroidDirectory(context), context)
    }

    /**
     * @return `true` if [currentDirPath] is a Legacy Storage Directory.
     */
    fun isLegacyStorage(currentDirPath: String, context: Context): Boolean {
        val internalScopedDirPath = CollectionHelper.getAppSpecificInternalAnkiDroidDirectory(context)
        val currentDir = File(currentDirPath).canonicalFile
        val externalScopedDirs = CollectionHelper.getAppSpecificExternalDirectories(context).map { it.canonicalFile }
        val internalScopedDir = File(internalScopedDirPath).canonicalFile
        Timber.i(
            "isLegacyStorage(): current dir: %s\nscoped external dirs: %s\nscoped internal dir: %s",
            currentDirPath,
            externalScopedDirs.joinToString(", "),
            internalScopedDirPath
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

    fun migrationStatus(context: Context): Status {
        if ((!isLegacyStorage(context) && !userMigrationIsInProgress(context))) {
            return Status.COMPLETED
        }

        if (Permissions.allFileAccessPermissionGranted(context)) {
            return Status.NOT_NEEDED
        }

        if (!Permissions.hasStorageAccessPermission(context)) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Environment.isExternalStorageLegacy()) {
                Status.PERMISSION_FAILED
            } else {
                Status.REQUIRES_PERMISSION
            }
        }

        if (userMigrationIsInProgress(context)) {
            return Status.IN_PROGRESS
        }

        return Status.NEEDS_MIGRATION
    }

    enum class Status {
        NEEDS_MIGRATION,
        REQUIRES_PERMISSION,
        PERMISSION_FAILED,
        IN_PROGRESS,
        COMPLETED,
        NOT_NEEDED
    }
}
