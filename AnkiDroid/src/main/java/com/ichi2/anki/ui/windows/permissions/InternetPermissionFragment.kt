/*
 *  Copyright (c) 2025 Akshita Tiwary <akshita.andev16@gmail.com>
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

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.core.net.toUri
import com.ichi2.anki.R
import com.ichi2.utils.Permissions

class InternetPermissionFragment : PermissionsFragment(R.layout.permission_internet_xiomi) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val internetPermissionItem =
            view.findViewById<PermissionsItem>(R.id.internet_permission)

        internetPermissionItem.setOnPermissionsRequested { isAlreadyGranted ->
            if (!isAlreadyGranted) {
                openAppSettings()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val isGranted = Permissions.canAccessInternet(requireContext())

        (activity as? PermissionsActivity)?.setContinueButtonEnabled(isGranted)
    }

    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = ("package:" + requireContext().packageName).toUri()
            }
        startActivity(intent)
    }
}
