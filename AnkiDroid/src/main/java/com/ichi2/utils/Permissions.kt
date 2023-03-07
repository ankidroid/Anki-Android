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
import androidx.core.content.ContextCompat
import com.ichi2.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.compat.PackageInfoFlagsCompat
import timber.log.Timber
import java.lang.Exception

object Permissions {
    fun canUseCamera(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.CAMERA)
    }

    fun canRecordAudio(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }

    fun hasPermission(context: Context, vararg permissions: String): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

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
     * @param context
     * @return
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    fun hasStorageAccessPermission(context: Context): Boolean {
        return hasStorageReadAccessPermission(context) && hasStorageWriteAccessPermission(context)
    }

    fun canUseWakeLock(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.WAKE_LOCK)
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
            val requestedPermissions = getPermissionsDefinedInManifest(packageName) ?: return false
            return permissions.all { requestedPermissions.contains(it) }
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
}
