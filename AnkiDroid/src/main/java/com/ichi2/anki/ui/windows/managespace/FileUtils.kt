/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.windows.managespace

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.IPackageStatsObserver
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ichi2.anki.R
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Get the size of user data and cache for the current package, in bytes.
 * This should amount to the sum of User data and Cache in App info -> Storage and cache.
 */
suspend fun Context.getUserDataAndCacheSize(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        getUserDataAndCacheSizeUsingStorageStatsManager()
    } else {
        getUserDataAndCacheSizeUsingGetPackageSizeInfo()
    }

/*
 * The logic was taken from this SO question: https://stackoverflow.com/q/43472398/#44708209
 * Asked & answered by android developer: https://stackoverflow.com/users/878126/android-developer
 *
 * Regarding obtaining valid storage volume UUID, see:
 *   https://issuetracker.google.com/issues/62982912
 *   https://stackoverflow.com/questions/48589109/invalid-uuid-of-storage-gained-from-android-storagemanager
 *
 * TODO The below platform class uses a simpler approach:
 *       val appStorageUuid = packageManager.getApplicationInfo(packageName, 0).storageUuid
 *   The docstring of the method says, "Get number of bytes of the app data of the package".
 *   Investigate whether this approach is sufficient, and if so, why.
 *   See `com.android.packageinstaller.handheld.UninstallAlertDialogFragment#getAppDataSizeForUser`.
 */
@RequiresApi(Build.VERSION_CODES.O)
private fun Context.getUserDataAndCacheSizeUsingStorageStatsManager(): Long {
    // See StorageManager#isFatVolumeIdentifier
    fun String.isFatVolumeIdentifier() = length == 9 && this[4] == '-'

    // See StorageManager#convert(java.lang.String)
    fun String.fromFatVolumeIdentifierToUuid() =
        UUID.fromString("fafafafa-fafa-5afa-8afa-fafa" + replace("-", ""))

    fun StorageVolume.getValidUuid() = uuid.let { uuidish ->
        when {
            uuidish == null -> StorageManager.UUID_DEFAULT
            uuidish.isFatVolumeIdentifier() -> uuidish.fromFatVolumeIdentifierToUuid()
            else -> UUID.fromString(uuidish)
        }
    }

    val storageManager = ContextCompat.getSystemService(this, StorageManager::class.java) ?: return 0
    val storageStatsManager = ContextCompat.getSystemService(this, StorageStatsManager::class.java) ?: return 0
    val currentUser = android.os.Process.myUserHandle()

    return storageManager.storageVolumes
        .mapTo(mutableSetOf()) { volume -> volume.getValidUuid() }
        .sumOf { uuid ->
            storageStatsManager.queryStatsForPackage(uuid, packageName, currentUser).dataBytes
        }
}

/*
 * The logic was taken from this SO question: https://stackoverflow.com/q/36944194#36983630
 * Asked by Chris Sherlock: https://stackoverflow.com/users/2992462/chris-sherlock
 * Answered by Mattia Maestrini: https://stackoverflow.com/users/2837959/mattia-maestrini
 */
private suspend fun Context.getUserDataAndCacheSizeUsingGetPackageSizeInfo(): Long {
    lateinit var continuation: Continuation<Long>

    packageManager::class.java
        .getMethod("getPackageSizeInfo", String::class.java, IPackageStatsObserver::class.java)
        .invoke(
            packageManager,
            packageName,
            object : IPackageStatsObserver.Stub() {
                @Suppress("DEPRECATION") // PackageStats
                override fun onGetStatsCompleted(packageStats: android.content.pm.PackageStats, succeeded: Boolean) {
                    val totalCacheSize = packageStats.cacheSize + packageStats.externalCacheSize
                    val totalDataSize = packageStats.dataSize + packageStats.externalDataSize
                    continuation.resume(totalCacheSize + totalDataSize)
                }
            }
        )

    return suspendCancellableCoroutine { continuation = it }
}

/**
 * This only examines the locations where users can reasonably put files.
 * For reference, some locations from my device:
 *
 *     dataDir [needs API 24]:      /data/user/0/com.ichi2.anki
 *     cacheDir:                    /data/user/0/com.ichi2.anki/cache
 *     codeCacheDir:                /data/user/0/com.ichi2.anki/code_cache
 *     filesDir:                    /data/user/0/com.ichi2.anki/files
 *     noBackupFilesDir:            /data/user/0/com.ichi2.anki/no_backup
 *     getExternalFilesDir(null):   /storage/emulated/0/Android/data/com.ichi2.anki/files
 *     getExternalFilesDirs(null):  /storage/emulated/0/Android/data/com.ichi2.anki/files
 *     externalCacheDir:            /storage/emulated/0/Android/data/com.ichi2.anki/cache
 *     externalCacheDirs:           /storage/emulated/0/Android/data/com.ichi2.anki/cache
 *     externalMediaDirs:           /storage/emulated/0/Android/media/com.ichi2.anki
 *     obbDir:                      /storage/emulated/0/Android/obb/com.ichi2.anki
 *     obbDirs:                     /storage/emulated/0/Android/obb/com.ichi2.anki
 *
 *     externalDirs:                /storage/emulated/0/Android/data/com.ichi2.anki
 *
 * There is a similar method [com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage],
 * but it has a different purpose and behavior, and I am not sure if I understand it well.
 */
@Suppress("DEPRECATION") // context.externalMediaDirs: see the doc for the method
fun File.isInsideDirectoriesRemovedWithTheApp(context: Context): Boolean {
    infix fun File.isInsideOf(parent: File) = this.canonicalFile.startsWith(parent.canonicalFile)

    return context.getExternalFilesDirs(null).any { this isInsideOf it } ||
        context.externalCacheDirs.any { this isInsideOf it } ||
        context.externalMediaDirs.any { this isInsideOf it } ||
        context.obbDirs.any { this isInsideOf it } ||
        context.externalDirs.any { this isInsideOf it }
}

/*
 * Retrieves folders such as /storage/emulated/0/Android/data/com.ichi2.anki.
 * User can put files into these folders, but there seems to be no API to fetch them.
 * Since this folder is usually a parent of folders such as `externalCacheDir`,
 * and ends in package name, attempting to determine it by other API methods seems reasonable.
 */
private val Context.externalDirs: Set<File> get() =
    (getExternalFilesDirs(null) + externalCacheDirs)
        .mapNotNullTo(mutableSetOf()) { externalFilesOrCacheDir ->
            externalFilesOrCacheDir.parentFile?.let { parentDir ->
                if (parentDir.name == packageName) parentDir else null
            }
        }

/**
 * Get the size of a file or a directory in bytes. Cancellable.
 */
fun CoroutineScope.calculateSize(file: File): Long {
    ensureActive()
    return when {
        file.isDirectory -> file.listFiles()?.sumOf(::calculateSize) ?: 0
        else -> file.length()
    }
}

fun File.canWriteToOrCreate(): Boolean =
    when {
        canWrite() -> true
        exists() -> false
        else -> parentFile?.canWriteToOrCreate() ?: false
    }

/********************************** Collection directory utils ************************************/

interface CollectionDirectoryProvider { val collectionDirectory: File }

class CanNotWriteToOrCreateFileException(val file: File) : Exception() {
    override val message get() = "Can not write to or create file: $file"
}

suspend fun CollectionDirectoryProvider.ensureCanWriteToOrCreateCollectionDirectory() {
    if (!withContext(Dispatchers.IO) { collectionDirectory.canWriteToOrCreate() })
        throw CanNotWriteToOrCreateFileException(collectionDirectory)
}

suspend fun CollectionDirectoryProvider.collectionDirectoryExists() =
    withContext(Dispatchers.IO) { collectionDirectory.exists() }

/********************************************* Etc ************************************************/

fun Context.getUserFriendlyErrorText(e: Exception): CharSequence =
    if (e is CanNotWriteToOrCreateFileException) {
        getString(R.string.error__etc__cannot_write_to_or_create_file, e.file)
    } else {
        e.localizedMessage
            ?: e.message
            ?: e::class.simpleName
            ?: getString(R.string.error__etc__unknown_error)
    }
