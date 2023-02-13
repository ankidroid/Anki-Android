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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
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
    activity: AnkiActivity,
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun willRequestManageExternalStorage(context: Context): Boolean {
        val requiresManageExternalStoragePermission = permissions.contains(MANAGE_EXTERNAL_STORAGE)
        val isManageExternalStorageGranted = Permissions.hasPermission(context, MANAGE_EXTERNAL_STORAGE)

        return requiresManageExternalStoragePermission && !isManageExternalStorageGranted
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

        val activity = activityRef.get() ?: throw Exception("activity disposed")

        // 'Manage External Storage' needs special-casing as launchPermissionDialog can't request it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && willRequestManageExternalStorage(activity)) {
            // Open an external screen and close the activity.
            // Accepting this permission closes the app
            UIUtils.showThemedToast(activity, R.string.startup_no_storage_permission, false)
            activity.finishActivityAndShowManageAllFilesScreen()
            return
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
        @RequiresApi(Build.VERSION_CODES.R)
        private const val MANAGE_EXTERNAL_STORAGE = android.Manifest.permission.MANAGE_EXTERNAL_STORAGE

        /**
         * This ** must be called unconditionally, as part of AnkiDroid initialization path.
         * This is because we can't know whether we'll be receiving the result of the activity requesting the permissions.
         * We typically call it by assigning its result to a field during initialization of an activity.
         */
        fun register(
            activity: AnkiActivity,
            permissions: Array<String>,
            useCallbackIfActivityRecreated: Boolean,
            callback: (permissionDialogResult: PermissionsRequestRawResults) -> Unit
        ): PermissionManager =
            PermissionManager(activity, permissions, useCallbackIfActivityRecreated, callback)
    }
}

/**
 * Closes the activity. Opens the Android settings for AnkiDroid if the phone provide this feature.
 * Lets a user grant any missing permissions which have been permanently denied
 * We finish the activity as setting permissions terminates the app
 */
fun AnkiActivity.finishActivityAndShowAppPermissionManagementScreen() {
    this.finishWithoutAnimation()
    showAppPermissionManagementScreen()
}

private fun AnkiActivity.showAppPermissionManagementScreen() {
    this.startActivityWithoutAnimation(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", this.packageName, null)
        )
    )
}

/**
 * Closes the activity and opens the Android 'MANAGE_ALL_FILES' page if the phone provides this feature.
 */
@RequiresApi(Build.VERSION_CODES.R)
fun AnkiActivity.finishActivityAndShowManageAllFilesScreen() {
    // This screen is simpler than the one from displayAppPermissionManagementScreen:
    // In 'AppPermissionManagement' a user has to go to permissions -> storage -> 'allow management of all files' -> dialog warning
    // In 'ManageAllFiles': a user selects the app which has permission
    // We finish the activity as setting permissions terminates the app
    this.finishWithoutAnimation()

    val intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", this.packageName, null)
    )

    // From the docs: [ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION]
    // In some cases, a matching Activity may not exist, so ensure you safeguard against this.

    if (intent.resolveActivity(packageManager) != null) {
        startActivityWithoutAnimation(intent)
    } else {
        // This also allows management of the all files permission (worse UI)
        showAppPermissionManagementScreen()
    }
}
