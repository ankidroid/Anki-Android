/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.utils.Permissions

/**
 * Permissions explanation screen that appears when the user clicks on the extra info buttons next to the permissions
 * AnkiDroid requests in the OS settings screen. Explains the permissions AnkiDroid requests and provides switches for
 * toggling them on or off.
 *
 * This feature first became available in API 31. Hence, we do not show legacy storage permission (which were available
 * until API 29) in this fragment.
 * See: https://developer.android.com/training/permissions/explaining-access#privacy-dashboard
 */
@RequiresApi(Build.VERSION_CODES.S)
class AllPermissionsExplanation : PermissionsFragment(R.layout.all_permissions_explanation) {
    /**
     * Attempts to open the dialog for granting a permission. Falls back to opening the OS settings if
     * the dialog fails to show up. This may happen if the user has previously denied the permission multiple times,
     * if the user selects "don't ask again" on the notification dialog, etc.
     */
    private val permissionRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { requestedPermissions ->
            if (!requestedPermissions.all { it.value }) {
                // The permission dialog did not show up or the user denied the permission
                // Offer the ability to manually grant the permission via the OS settings
                showToastAndOpenAppSettingsScreen(R.string.manually_grant_permissions)
            }
        }

    /**
     * Activity launcher for the external storage management permission.
     */
    private val accessAllFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {}

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val externalStoragePermission = view.findViewById<PermissionItem>(R.id.manage_external_storage_permission_item)
        val notificationPermission = view.findViewById<PermissionItem>(R.id.post_notification_permission_item)
        val recordAudioPermission = view.findViewById<PermissionItem>(R.id.record_audio_permission_item)

        // External storage
        if (Permissions.canManageExternalStorage(requireContext())) {
            externalStoragePermission.apply {
                isVisible = true
                requestExternalStorageOnClick(accessAllFilesLauncher)
            }
        }
        // Notifications
        if (Permissions.postNotification != null) {
            notificationPermission.apply {
                isVisible = true
                offerToGrantOrRevokeOnClick(Permissions.postNotification)
            }
        }
        // Microphone
        // Minimum API 23, which is less than AnkiDroid's minimum targeted API version, so we show it unconditionally
        recordAudioPermission.apply {
            isVisible = true
            offerToGrantOrRevokeOnClick(Permissions.recordAudioPermission)
        }

        // Handle headings
        view
            .findViewById<View>(R.id.required_heading)
            .isVisible = externalStoragePermission.isVisible
        view
            .findViewById<View>(R.id.optional_heading)
            .isVisible = (notificationPermission.isVisible || recordAudioPermission.isVisible)
    }

    /**
     * If this permission is already granted, open the OS settings to allow the user to disable it, as
     * it is impossible to programmatically revoke a permission. If the permission has not been granted,
     * use [permissionRequestLauncher] to try and grant it. Note that [permissionRequestLauncher] also falls back
     * to opening the OS settings if the dialog fails to show up. This may happen if the user has previously
     * denied the permission multiple times, selected "don't ask again" on the notification dialog, etc.
     */
    private fun PermissionItem.offerToGrantOrRevokeOnClick(associatedPermission: String) {
        setOnSwitchClickListener { isGranted ->
            if (isGranted) {
                // Offer the ability to revoke the permission
                showToastAndOpenAppSettingsScreen(R.string.revoke_permissions)
            } else {
                permissionRequestLauncher.launch(arrayOf(associatedPermission))
            }
        }
    }
}
