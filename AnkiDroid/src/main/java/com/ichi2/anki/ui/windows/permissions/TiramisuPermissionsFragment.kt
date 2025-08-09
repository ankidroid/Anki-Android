/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
import com.ichi2.anki.R
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.canManageExternalStorage

/**
 * Permissions screen for requesting permissions in API 33+,
 * if the user [canManageExternalStorage], which isn't possible in the play store.
 *
 * Requested permissions:
 * 1. All files access: [Permissions.MANAGE_EXTERNAL_STORAGE].
 *   Used for saving the collection in a public directory
 *   which isn't deleted when the app is uninstalled
 *
 * Note: Even though explicit user permission is required to send notifications on API 33+, we do not
 * request it on this screen. This is because:
 * 1. The permission is not strictly necessary for app functionality. It is used for syncing to view
 *   active media sync progress / cancel active media syncs, but tapping on the sync icon in DeckPicker
 *   also provides the ability to cancel active media syncs, so providing a notification is not mandatory.
 *   It is used for review reminders, but the notification permissions can be requested when the user first
 *   creates a review reminder rather than when the app is first installed.
 * 2. The "manage all files" permission is only grantable for apps not installed via the Play store, and thus
 *   putting an optional notifications permission onto this screen would mean that only users not installing
 *   via the Play store would ever see it.
 * 3. If, however, we choose to show this fragment to every user who installs AnkiDroid, even those from the Play store,
 *   then the "manage all files" permission will not be visible and they will only see a single optional tile
 *   for enabling notifications, which is awkward and adds unnecessary extra steps to the first-time-launch
 *   experience. It makes more sense to request permission to send notifications when the permission is
 *   immediately required, ex. when the user triggers a sync or creates a review reminder, rather than
 *   during the app onboarding process.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TiramisuPermissionsFragment : PermissionsFragment(R.layout.permissions_tiramisu) {
    private val accessAllFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            if (hasAllPermissions()) {
                requireActivity().finish()
            } else {
                showToastAndOpenAppSettingsScreen(R.string.manually_grant_permissions)
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        view
            .findViewById<PermissionItem>(R.id.all_files_permission)
            .requestExternalStorageOnClick(accessAllFilesLauncher)
    }
}
