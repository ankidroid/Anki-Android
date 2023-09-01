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
import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ichi2.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.compat.PackageInfoFlagsCompat
import timber.log.Timber
import java.lang.Exception

object Permissions {
    fun canRecordAudio(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permission == MANAGE_EXTERNAL_STORAGE) {
            // checkSelfPermission doesn't return PERMISSION_GRANTED, even if it's granted.
            return isExternalStorageManager()
        }

        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

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
    private fun hasStorageWriteAccessPermission(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    /**
     * Check if we have read access permission to the external storage
     * @param context
     * @return
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    private fun hasStorageReadAccessPermission(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * Check if we have read and write access permission to the external storage
     * Note: This can return true >= R on a debug build or if storage is preserved
     * @param context
     * @return
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    fun hasStorageAccessPermission(context: Context): Boolean {
        return hasStorageReadAccessPermission(context) && hasStorageWriteAccessPermission(context)
    }

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
    fun Context.arePermissionsDefinedInManifest(packageName: String, vararg permissions: String): Boolean {
        try {
            val permissionsInManifest = getPermissionsDefinedInManifest(packageName) ?: return false
            return permissions.all { permissionsInManifest.contains(it) }
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }

    private fun Context.getPermissionsDefinedInManifest(packageName: String): Array<out String>? {
        return try {
            // requestedPermissions => <uses-permission> in manifest
            val flags = PackageInfoFlagsCompat.of(GET_PERMISSIONS.toLong())
            getPackageInfoCompat(packageName, flags)!!.requestedPermissions
        } catch (e: Exception) {
            Timber.w(e)
            null
        }
    }

    /**
     * @see Context.arePermissionsDefinedInManifest
     */
    fun Context.arePermissionsDefinedInAnkiDroidManifest(vararg permissions: String) =
        this.arePermissionsDefinedInManifest(this.packageName, *permissions)

    fun canManageExternalStorage(context: Context): Boolean {
        // TODO: See if we can move this to a testing manifest
        if (isRobolectric) {
            return false
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.arePermissionsDefinedInAnkiDroidManifest(MANAGE_EXTERNAL_STORAGE)
    }
}
