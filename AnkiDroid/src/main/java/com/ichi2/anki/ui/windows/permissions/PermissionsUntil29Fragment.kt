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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.ichi2.anki.R
import com.ichi2.utils.Permissions
import com.ichi2.utils.hasAnyOfPermissionsBeenDenied

/**
 * Permissions screen for requesting permissions until API 29.
 *
 * Requested permissions:
 * 1. Storage access: [Permissions.legacyStorageAccessPermissions].
 *   Used for saving the collection in a public directory
 *   which isn't deleted when the app is uninstalled
 */
class PermissionsUntil29Fragment : PermissionsFragment() {
    private val storageLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.permissions_until_29, container, false)

        val storagePermission = view.findViewById<PermissionItem>(R.id.storage_permission)
        storagePermission.setOnSwitchClickListener {
            if (!hasAnyOfPermissionsBeenDenied(storagePermission.permissions)) {
                storageLauncher.launch(storagePermission.permissions.toTypedArray())
            } else {
                showToastAndOpenAppSettingsScreen(R.string.startup_no_storage_permission)
            }
        }

        return view
    }
}
