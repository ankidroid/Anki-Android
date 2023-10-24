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
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.model.Directory
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.UserDataMigrationPreferences
import com.ichi2.anki.ui.windows.managespace.isInsideDirectoriesRemovedWithTheApp
import com.ichi2.compat.CompatHelper
import com.ichi2.utils.FileUtil
import com.ichi2.utils.FileUtil.getParentsAndSelfRecursive
import com.ichi2.utils.FileUtil.isDescendantOf
import com.ichi2.utils.Permissions
import timber.log.Timber
import java.io.File

/** Validated source and destination folders.
 *
 * - [unscopedSourceDirectory] is the existing AnkiDroid directory, named AnkiDroid by default.
 * - [scopedDestinationDirectory] is the new directory inside scoped storage where files will be copied.
 * This storage directory is accessible without permissions after scoped storage changes,
 * and is much faster to access.
 *
 * When uninstalling: A user will be asked if they want to delete this folder
 * A folder here may be modifiable via USB. In the case of AnkiDroid, all collection folders should
 * be modifiable
 *
 * @see [isLegacyStorage]
 */
data class ValidatedMigrationSourceAndDestination(val unscopedSourceDirectory: Directory, val scopedDestinationDirectory: Directory)

/** Overrides for testing. If root is provided, a subfolder is automatically created in it.
 * If subfolder is provided, the exact folder provided is used. */
sealed class DestFolderOverride {
    object None : DestFolderOverride()
    class Root(val folder: File) : DestFolderOverride()
    class Subfolder(val folder: File) : DestFolderOverride()
}

fun DestFolderOverride.rootFolder(): File? {
    return when (this) {
        is DestFolderOverride.Root -> folder
        else -> null
    }
}

fun DestFolderOverride.subFolder(): File? {
    return when (this) {
        is DestFolderOverride.Subfolder -> folder
        else -> null
    }
}

object ScopedStorageService {
    /**
     * Preference listing the [UnscopedSourceDirectory] where a scoped storage migration is occurring from
     *
     * This directory should exist if the preference is set
     *
     * If this preference is set and non-empty, then a [migration of user data][MigrateUserData] should be occurring
     * @see mediaMigrationIsInProgress
     * @see UserDataMigrationPreferences
     */
    const val PREF_MIGRATION_SOURCE = "migrationSourcePath"

    /**
     * Preference listing the [UnscopedSourceDirectory] where a scoped storage migration is migrating to.
     *
     * This directory should exist if the preference is set
     *
     * This preference exists to decouple scoped storage migration from the `deckPath` variable: there are a number
     * of reasons that `deckPath` could change, and it's a long-term risk to couple the two operations
     *
     * If this preference is set and non-empty, then a [migration of user data][MigrateUserData] should be occurring
     * @see mediaMigrationIsInProgress
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
     * The buffer space required to migrate files (in addition to the size of the files that we move)
     */
    private const val SAFETY_MARGIN_BYTES = 10 * 1024 * 1024

    /** See [ValidatedMigrationSourceAndDestination] */
    fun prepareAndValidateSourceAndDestinationFolders(
        context: Context,
        // used for testing
        sourceOverride: File? = null,
        destOverride: DestFolderOverride = DestFolderOverride.None,
        checkSourceDir: Boolean = true
    ): ValidatedMigrationSourceAndDestination {
        // this is checked by deckpicker already, but left here for unit tests
        if (mediaMigrationIsInProgress(context)) {
            throw IllegalStateException("Migration is already in progress")
        }

        val sourceDirectory = sourceOverride ?: getSourceDirectory()
        if (checkSourceDir) {
            validateSourceDirectory(context, sourceDirectory)
        }

        val destinationRoot = destOverride.rootFolder() ?: getBestDefaultRootDirectory(context, sourceDirectory)
        val destinationDirectory = destOverride.subFolder() ?: determineBestNewProfileDirectory(destinationRoot)
        CompatHelper.compat.createDirectories(destinationDirectory)

        validateDestinationDirectory(context, destinationDirectory)
        ensureSpaceAvailable(sourceDirectory, destinationDirectory)

        Timber.i("will migrate %s -> %s", sourceDirectory, destinationDirectory)

        return ValidatedMigrationSourceAndDestination(
            Directory.createInstance(sourceDirectory)!!,
            Directory.createInstance(destinationDirectory)!!
        )
    }

    private fun getSourceDirectory(): File {
        val path = CollectionManager.collectionPathInValidFolder()
        return File(path).parentFile!!
    }

    private fun validateSourceDirectory(context: Context, dir: File) {
        if (!isLegacyStorage(dir, context)) {
            throw IllegalStateException("Source directory is already under scoped storage")
        }
    }

    private fun validateDestinationDirectory(context: Context, destFolder: File) {
        if (CompatHelper.compat.hasFiles(destFolder)) {
            throw IllegalStateException("Target directory was not empty: '$destFolder'")
        }

        if (isLegacyStorage(destFolder, context)) {
            throw IllegalStateException("Destination folder was not under scoped storage '$destFolder'")
        }
    }

    private fun ensureSpaceAvailable(sourceDirectory: File, destDirectory: File) {
        // Ensure we have space.
        // This must be after .mkdirs(): determineBytesAvailable works on non-empty directories,
        MigrateEssentialFiles.UserActionRequiredException.OutOfSpaceException.throwIfInsufficient(
            available = FileUtil.determineBytesAvailable(destDirectory.absolutePath),
            required = MigrateEssentialFiles.PRIORITY_FILES.sumOf { it.spaceRequired(sourceDirectory.path) } + SAFETY_MARGIN_BYTES
        )
    }

    /** append a folder name to the root destination.
     If the root destination was /storage/emulated/0/Android/com.ichi2.anki/files
     we add a subfolder name to allow for more than one AnkiDroid data directory to be migrated.
     This is useful as:
     * Multiple installations of AnkiDroid go to different folders
     * It will allow us to add profiles without changing directories again
     */
    private fun determineBestNewProfileDirectory(rootDestination: File): File {
        return (1..MAX_ANKIDROID_DIRECTORIES).asSequence()
            .map { File(rootDestination, "AnkiDroid$it") }
            .first { !it.exists() } // skip directories which exist
    }

    /**
     * Whether a user data scoped storage migration is taking place
     * This refers to the [MigrateUserData] operation of copying media which can take a long time.
     *
     * DEPRECATED. Use [com.ichi2.anki.services.getMediaMigrationState] instead.
     *
     * @throws IllegalStateException If either [PREF_MIGRATION_SOURCE] or [PREF_MIGRATION_DESTINATION] is set (but not both)
     * It is a logic bug if only one is set
     */
    fun mediaMigrationIsInProgress(context: Context): Boolean =
        mediaMigrationIsInProgress(context.sharedPrefs())

    /**
     * Whether a user data scoped storage migration is taking place
     * This refers to the [MigrateUserData] operation of copying media which can take a long time.
     *
     * @see mediaMigrationIsInProgress[Context]
     * @throws IllegalStateException If either [PREF_MIGRATION_SOURCE] or [PREF_MIGRATION_DESTINATION] is set (but not both)
     * It is a logic bug if only one is set
     */
    fun mediaMigrationIsInProgress(preferences: SharedPreferences) =
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
                val firstExternalPathContainedInParent =
                    parentToSharedDirectoryPath.getOrDefault(parent, null)
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
            .firstNotNullOfOrNull { parent ->
                parentToSharedDirectoryPath.getOrDefault(
                    parent,
                    null
                )
            }!!
    }

    /**
     * Checks if current directory being used by AnkiDroid to store user data is a Legacy Storage Directory.
     * This directory is stored under [CollectionHelper.PREF_COLLECTION_PATH] in SharedPreferences
     *
     * DEPRECATED. Use either [com.ichi2.anki.services.getMediaMigrationState], or
     *   [com.ichi2.anki.ui.windows.managespace.isInsideDirectoriesRemovedWithTheApp].
     *
     * @return `true` if AnkiDroid is storing user data in a Legacy Storage Directory.
     */
    fun isLegacyStorage(context: Context): Boolean {
        return isLegacyStorage(File(CollectionHelper.getCurrentAnkiDroidDirectory(context)), context)
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
        if (!setCollectionPath && !context.sharedPrefs()
            .contains(CollectionHelper.PREF_COLLECTION_PATH)
        ) {
            return null
        }
        return isLegacyStorage(File(CollectionHelper.getCurrentAnkiDroidDirectory(context)), context)
    }

    /**
     * @return `true` if [currentDirPath] is a Legacy Storage Directory.
     *
     * DEPRECATED. Use either [com.ichi2.anki.services.getMediaMigrationState], or
     *   [com.ichi2.anki.ui.windows.managespace.isInsideDirectoriesRemovedWithTheApp].
     *
     */
    fun isLegacyStorage(currentDirPath: File, context: Context): Boolean {
        val internalScopedDirPath =
            CollectionHelper.getAppSpecificInternalAnkiDroidDirectory(context)
        val currentDir = currentDirPath.canonicalFile
        val externalScopedDirs =
            CollectionHelper.getAppSpecificExternalDirectories(context).map { it.canonicalFile }
        val internalScopedDir = File(internalScopedDirPath).canonicalFile
        Timber.i(
            "isLegacyStorage(): current dir: %s\nscoped external dirs: %s\nscoped internal dir: %s",
            currentDirPath,
            externalScopedDirs.joinToString(", "),
            internalScopedDirPath
        )

        // Loop to check if the current AnkiDroid directory or any of its parents are the same as the root directories
        // for app-private external or internal storage - the only directories which will be accessible without
        // permissions under scoped storage
        val scopedDirectories = externalScopedDirs + internalScopedDir
        var currentDirParent: File? = currentDir
        while (currentDirParent != null) {
            for (scopedDir in scopedDirectories) {
                if (currentDirParent.compareTo(scopedDir) == 0) {
                    Timber.i("isLegacyStorage(): false")
                    return false
                }
            }
            currentDirParent = currentDirParent.parentFile?.canonicalFile
        }

        // If the current AnkiDroid directory isn't a sub directory of the app-private external or internal storage
        // directories, then it must be in a legacy storage directory
        Timber.i("isLegacyStorage(): true")
        return true
    }

    /**
     * Whether the user's current collection is now inaccessible due to a 'reinstall'
     *
     * @return `false` if:
     * * ⚠️ The directory will be **removed** on uninstall
     *    * The user installed with Android 11+, and is more likely to expect this behavior
     *    * Note: The directory data may not be removed if the user taps "Keep data" when uninstalling
     * * The collection is currently accessible
     * * the user is on Android 9 or below and Android will not revoke permissions
     * * The user has the potential to grant [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE]
     * @see android.R.attr.preserveLegacyExternalStorage
     * @see android.R.attr.requestLegacyExternalStorage
     */
    fun collectionWasMadeInaccessibleAfterUninstall(context: Context): Boolean {
        // If we're < Q then `requestLegacyExternalStorage` was not introduced
        // We do not check for == Q here, instead relying on `isExternalStorageLegacy`
        // requestLegacyExternalStorage is a strong assumption, but we need to handle the case that
        // this assumption breaks down
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        // the user could obtain MANAGE_EXTERNAL_STORAGE
        if (Permissions.canManageExternalStorage(context)) {
            return false
        }

        if (userIsPromptedToDeleteCollectionOnUninstall(context)) {
            return false
        }

        return !Environment.isExternalStorageLegacy()
    }

    /**
     * Whether the user's current collection will be inaccessible after uninstalling the app
     *
     * DEPRECATED. Use [com.ichi2.anki.services.getMediaMigrationState] instead.
     *
     * @return `false` if:
     * * ⚠️ The directory will be **removed** on uninstall
     *    * The user installed with Android 11+, and is more likely to expect this behavior
     *    * Note: The directory data may not be removed if the user taps "Keep data" when uninstalling
     * * The collection is now inaccessible
     * * the user is on Android Q or below and Android **should** not revoke permissions
     * * The user has the potential to grant [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE]
     * Returns `true` > Android 10 and the user has no way to access the collection on uninstall
     * except for using another build of `com.ichi2.anki` or manually copying files
     * @see android.R.attr.preserveLegacyExternalStorage
     * @see android.R.attr.requestLegacyExternalStorage
     */
    fun collectionWillBeMadeInaccessibleAfterUninstall(context: Context): Boolean {
        // If we're < Q then `requestLegacyExternalStorage` was not introduced
        // If we're == Q then `preserveLegacyExternalStorage` is expected to be in place
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            return false
        }

        // the user could obtain MANAGE_EXTERNAL_STORAGE
        if (Permissions.canManageExternalStorage(context)) {
            return false
        }

        if (userIsPromptedToDeleteCollectionOnUninstall(context)) {
            return false
        }

        return Environment.isExternalStorageLegacy()
    }

    fun userIsPromptedToDeleteCollectionOnUninstall(context: Context): Boolean {
        return File(CollectionHelper.getCollectionPath(context)).isInsideDirectoriesRemovedWithTheApp(
            context
        )
    }
}
