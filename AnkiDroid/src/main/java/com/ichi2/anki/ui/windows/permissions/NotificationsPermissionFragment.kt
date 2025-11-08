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
import com.ichi2.anki.R
import com.ichi2.anki.databinding.NotificationsPermissionBinding
import com.ichi2.utils.Permissions
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Permissions fragment shown on the [PermissionsBottomSheet] for requesting notification permissions
 * from the user. This permission only needs to be requested at or above API 33.
 *
 * Requested permissions:
 * 1. Notifications: [Permissions.postNotification].
 *   Used to view and cancel sync progress.
 *   Used for review reminder notifications.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationsPermissionFragment : PermissionsFragment(R.layout.notifications_permission) {
    private val binding by viewBinding(NotificationsPermissionBinding::bind)

    /**
     * Launches the OS dialog for requesting notification permissions.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { requestedPermissions ->
            Timber.i("Notification permission result: $requestedPermissions")
            if (!requestedPermissions.all { it.value }) {
                showToastAndOpenAppSettingsScreen(R.string.manually_grant_permissions)
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        Permissions.postNotification?.let {
            binding.notificationPermission.offerToGrantOrRevokeOnClick(
                notificationPermissionLauncher,
                arrayOf(Permissions.postNotification),
            )
        }
    }
}
