/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.utils.Permissions
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

interface NotificationPermissionManager {
    /**
     * Registers the activity for notification permission result.
     *
     * @param activity The FragmentActivity requesting permission.
     * @param permission The permission string to request.
     */
    fun registerForNotificationPermission(activity: FragmentActivity, permission: String)

    /**
     * Shows a dialog to request notification permission.
     *
     * @param activity The FragmentActivity in which to show the dialog.
     */
    fun showNotificationPermissionDialog(activity: FragmentActivity)
}

/**
 * This class provides methods to show a dialog for requesting notification permission and handle its result.
 * It also registers for notification permission and manages the associated launcher.
 */
class NotificationPermission : NotificationPermissionManager {

    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null

    override fun showNotificationPermissionDialog(activity: FragmentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Timber.d("Android SDK<33, no notification permission required")
            return
        }
        val permission = Permissions.postNotification
        when {
            permission?.let {
                ContextCompat.checkSelfPermission(
                    activity,
                    it
                )
            } == PackageManager.PERMISSION_GRANTED -> {
                activity.sharedPrefs().edit {
                    putBoolean("notification_permission", true)
                }
            }

            shouldShowRequestPermissionRationale(activity, permission!!) -> {
                notificationRationaleDialog(context = activity, permission = permission)
            }

            else -> {
                notificationPermissionLauncher?.launch(permission)
            }
        }
    }

    private fun notificationRationaleDialog(context: FragmentActivity, permission: String) {
        Timber.i("Showing notification rationale")
        AlertDialog.Builder(context).show {
            title(stringRes = R.string.notification_permission_title)
            positiveButton(stringRes = R.string.grant) {
                notificationPermissionLauncher?.launch(permission)
            }
            negativeButton(stringRes = R.string.skip)
            message(stringRes = R.string.notification_permission_message)
        }
    }

    override fun registerForNotificationPermission(activity: FragmentActivity, permission: String) {
        notificationPermissionLauncher =
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    activity.sharedPrefs().edit {
                        putBoolean("notification_permission", true)
                    }
                } else {
                    if (shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    ) {
                        notificationRationaleDialog(activity, permission)
                    }
                }
            }
    }
}
