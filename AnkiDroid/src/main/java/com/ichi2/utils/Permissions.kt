/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.compat.PackageInfoFlagsCompat
import com.ichi2.utils.Permissions.MANAGE_EXTERNAL_STORAGE
import com.ichi2.utils.Permissions.arePermissionsDefinedInManifest
import com.ichi2.utils.Permissions.isExternalStorageManager
import timber.log.Timber

object Permissions {
    const val MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val tiramisuPhotosAndVideosPermissions =
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )

    /**
     * The name of the "post notification" permission on API where it's defined.
     */
    val postNotification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    /**
     * Returns whether AnkiDroid should request notification permissions from the user.
     */
    private fun shouldRequestNotificationPermissions(context: Context): Boolean {
        val permission = postNotification ?: return false // Null if below API 33
        val grantedStatus = ContextCompat.checkSelfPermission(context, permission)
        return (grantedStatus != PackageManager.PERMISSION_GRANTED)
    }

    /**
     * Shows the [com.ichi2.anki.ui.windows.permissions.NotificationsPermissionFragment] in the [PermissionsBottomSheet]
     * if notification permissions have not been granted. Does nothing if the permission does not need to
     * be requested (i.e. API < 33) or if the permission has already been granted.
     *
     * @param context Used for checking whether notification permissions have been granted.
     * @param fragmentManager Used for launching the BottomSheet, if necessary.
     * @param callback Executed only if the BottomSheet is actually shown.
     */
    fun requestNotificationsPermissionIfNeeded(
        context: Context,
        fragmentManager: FragmentManager,
        callback: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shouldRequestNotificationPermissions(context)) {
            Timber.i("Showing notifications bottom sheet")
            PermissionsBottomSheet.launch(fragmentManager, PermissionSet.NOTIFICATIONS)
            callback()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val tiramisuAudioPermission = Manifest.permission.READ_MEDIA_AUDIO

    val legacyStorageAccessPermissions =
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

    val recordAudioPermission = Manifest.permission.RECORD_AUDIO

    fun canRecordAudio(context: Context): Boolean = hasPermission(context, recordAudioPermission)

    /**
     * Whether the app is granted [permission]
     *
     * Same as [androidx.core.content.ContextCompat.checkSelfPermission] except it corrects a bug related to [MANAGE_EXTERNAL_STORAGE].
     */
    fun hasPermission(
        context: Context,
        permission: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permission == MANAGE_EXTERNAL_STORAGE) {
            // checkSelfPermission doesn't return PERMISSION_GRANTED, even if it's granted.
            return isExternalStorageManager()
        }

        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether the app is granted all permission of [permissions]
     */
    fun hasAllPermissions(
        context: Context,
        permissions: Collection<String>,
    ): Boolean = permissions.all { hasPermission(context, it) }

    @RequiresApi(Build.VERSION_CODES.R)
    fun isExternalStorageManager(): Boolean {
        // BUG: Environment.isExternalStorageManager() crashes under robolectric
        // https://github.com/robolectric/robolectric/issues/7300
        if (isRobolectric) {
            return false // TODO: handle tests with both 'true' and 'false'
        }
        return Environment.isExternalStorageManager()
    }

    /**
     * On < Android 11, returns false.
     * On >= Android 11, returns [isExternalStorageManager]
     */
    fun isExternalStorageManagerCompat(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        } else {
            isExternalStorageManager()
        }
    }

    /**
     * Check if we have write access permission to the external storage
     * @param context
     * @return
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    private fun hasStorageWriteAccessPermission(
        context: Context,
    ): Boolean = hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /**
     * Check if we have read access permission to the external storage
     * @param context
     * @return
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    private fun hasStorageReadAccessPermission(
        context: Context,
    ): Boolean = hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)

    /**
     * Check if we have read and write access permission to the external storage
     * Note: This can return true >= R on a debug build or if storage is preserved
     *
     * @see IntentHandler.grantedStoragePermissions
     *
     * @param context
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    fun hasLegacyStorageAccessPermission(context: Context): Boolean =
        hasStorageReadAccessPermission(context) && hasStorageWriteAccessPermission(context)

    /**
     * Detects if permissions are defined via <uses-permission> in the Manifest.
     * This does **not** mean the permission has been granted.
     * Intention is to be used when a permissions may be changed by build flavours
     *
     * Example:
     * * Amazon => no camera
     * * Play => no 'manage external storage'
     *
     * @param permissions One or more permission strings, typically defined in [Manifest.permission]
     * @return `true` if all permissions were granted. `false` otherwise, or if an error occurs.
     */
    fun Context.arePermissionsDefinedInManifest(
        packageName: String,
        vararg permissions: String,
    ): Boolean {
        try {
            val permissionsInManifest = getPermissionsDefinedInManifest(packageName) ?: return false
            return permissions.all { permissionsInManifest.contains(it) }
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }

    private fun Context.getPermissionsDefinedInManifest(packageName: String): Array<out String>? =
        try {
            // requestedPermissions => <uses-permission> in manifest
            val flags = PackageInfoFlagsCompat.of(GET_PERMISSIONS.toLong())
            getPackageInfoCompat(packageName, flags)!!.requestedPermissions
        } catch (e: Exception) {
            Timber.w(e)
            null
        }

    /**
     * @see Context.arePermissionsDefinedInManifest
     */
    fun Context.arePermissionsDefinedInAnkiDroidManifest(vararg permissions: String) =
        this.arePermissionsDefinedInManifest(this.packageName, *permissions)

    /**
     * Whether it would be possible to manage external storage (potentially after requesting permission).
     */
    fun canManageExternalStorage(context: Context): Boolean {
        // TODO: See if we can move this to a testing manifest
        if (isRobolectric) {
            return false
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.arePermissionsDefinedInAnkiDroidManifest(MANAGE_EXTERNAL_STORAGE)
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
