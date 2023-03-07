/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import com.ichi2.anki.AnkiActivity
import com.ichi2.utils.Permissions
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * Handle permission requests via:
 * * [permissionDialogLauncher] returning whether it will execute the provided callback
 * * [PermissionsRequestResults] encapsulating temporary and permanent permission failure
 * @param permissions an array of permissions which PermissionManager will request (may be empty)
 * @param useCallbackIfActivityRecreated Whether [callback] should be executed if the activity was recreated.
 * Some logic may be re-executed on startup, and therefore the callback is unnecessary.
*/
class PermissionManager private constructor(
    activity: ComponentActivity,
    val permissions: Array<String>,
    private val useCallbackIfActivityRecreated: Boolean,
    // callback must be supplied here to allow for recreation of the activity if destroyed
    callback: (permissionDialogResult: PermissionsRequestRawResults) -> Unit
) {

    /**
     * Has a [ActivityResultLauncher.launch] method which accepts one or more permissions and displays
     * an Android permissions dialog. The callback is executed once the permissions dialog is closed
     * and focus returns to the app; except in one case, @see [useCallbackIfActivityRecreated]
     */
    private val permissionDialogLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (!permissionsRequestedInCurrentInstance) {
                // This can occur if the activity that sent the request was deleted while requesting for permission.
                if (!useCallbackIfActivityRecreated) {
                    // We may not want to execute the callback, because everything that it would do
                    // is done during the creation of the new activity. So we can just do an early return
                    return@registerForActivityResult
                }
            }
            callback.invoke(results)
        }
    private val activityRef = WeakReference(activity)
    private lateinit var callback: ((PermissionsRequestResults) -> Unit)

    // Whether permissions were requested in this instance
    private var permissionsRequestedInCurrentInstance: Boolean = false

    fun checkPermissions(): PermissionsCheckResult {
        val activity = activityRef.get() ?: throw Exception("activity disposed")
        val permissions = permissions.associateWith { Permissions.hasPermission(activity, it) }
        return PermissionsCheckResult(permissions)
    }

    /**
     * Launches a permission dialog. [callback] is executed after it is closed.
     * Should be called if [PermissionsCheckResult.requiresPermissionDialog] is true
     */
    @CheckResult
    fun launchPermissionDialog() {
        permissionsRequestedInCurrentInstance = true
        if (!permissions.any()) {
            throw IllegalStateException("permissions should be non-empty/requiresPermissionDialog was not called")
        }
        Timber.i("Showing dialog to request: '%s'", permissions.joinToString(", "))
        permissionDialogLauncher.launch(permissions)
    }

    fun launchDialogOrExecuteCallbackNow() {
        val permissions = checkPermissions()
        if (permissions.requiresPermissionDialog) {
            this.launchPermissionDialog()
        } else {
            callback.invoke(PermissionsRequestResults.allGranted(permissions))
        }
    }

    companion object {
        /**
         * This ** must be called unconditionally, as part of AnkiDroid initialization path.
         * This is because we can't know whether we'll be receiving the result of the activity requesting the permissions.
         * We typically call it by assigning its result to a field during initialization of an activity.
         */
        fun register(
            activity: ComponentActivity,
            permissions: Array<String>,
            useCallbackIfActivityRecreated: Boolean,
            callback: (permissionDialogResult: PermissionsRequestRawResults) -> Unit
        ): PermissionManager =
            PermissionManager(activity, permissions, useCallbackIfActivityRecreated, callback)
    }
}

/**
 * Closes the activity and opens the Android settings page for AnkiDroid
 * Lets a user grant any missing permissions which have been permanently denied
 * We finish the activity as setting permissions terminates the app
 */
fun AnkiActivity.finishActivityAndShowAppPermissionManagementScreen() {
    this.finishWithoutAnimation()
    this.startActivityWithoutAnimation(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", this.packageName, null)
        )
    )
}
