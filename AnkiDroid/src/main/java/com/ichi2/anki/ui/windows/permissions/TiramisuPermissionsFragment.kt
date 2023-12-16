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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * 2. TODO notifications permission
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TiramisuPermissionsFragment : PermissionsFragment() {
    private val accessAllFilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.permissions_tiramisu, container, false)

        val allFilesPermission = view.findViewById<PermissionItem>(R.id.all_files_permission)
        allFilesPermission.setOnSwitchClickListener {
            accessAllFilesLauncher.showManageAllFilesScreen()
        }

        return view
    }
}
