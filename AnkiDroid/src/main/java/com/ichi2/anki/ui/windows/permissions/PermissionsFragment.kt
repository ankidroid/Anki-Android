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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.showThemedToast
import timber.log.Timber

/**
 * Base class for constructing a permissions screen
 *
 * @see PermissionsActivity
 */
abstract class PermissionsFragment(
    @LayoutRes contentLayoutId: Int,
) : Fragment(contentLayoutId) {
    /**
     * All the [PermissionsItem]s in the fragment.
     * Must be called ONLY AFTER [onCreateView]
     */
    val permissionsItems: List<PermissionsItem>
        by lazy { view?.allViews?.filterIsInstance<PermissionsItem>()?.toList() ?: emptyList() }

    protected fun hasAllPermissions() = permissionsItems.all { it.areGranted }

    override fun onResume() {
        super.onResume()
        permissionsItems.forEach { it.updateSwitchCheckedStatus() }
        setFragmentResult(PERMISSIONS_FRAGMENT_RESULT_KEY, bundleOf(HAS_ALL_PERMISSIONS_KEY to hasAllPermissions()))
    }

    /**
     * Opens the Android settings for AnkiDroid if the device provide this feature.
     * Lets a user grant any missing permissions which have been permanently denied
     */
    private fun openAppSettingsScreen() {
        Timber.i("launching ACTION_APPLICATION_DETAILS_SETTINGS")
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireActivity().packageName, null),
            ),
        )
    }

    protected fun showToastAndOpenAppSettingsScreen(
        @StringRes message: Int,
    ) {
        showThemedToast(requireContext(), message, false)
        openAppSettingsScreen()
    }

    /** Opens the Android 'MANAGE_ALL_FILES' page if the device provides this feature */
    @RequiresApi(Build.VERSION_CODES.R)
    protected fun ActivityResultLauncher<Intent>.showManageAllFilesScreen() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", requireActivity().packageName, null),
            )

        // From the docs: [ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION]
        // In some cases, a matching Activity may not exist, so ensure you safeguard against this.
        // example: not yet supported on WearOS: https://issuetracker.google.com/issues/299174252
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            Timber.i("launching ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
            launch(intent)
        } else {
            openAppSettingsScreen()
        }
    }

    companion object {
        const val PERMISSIONS_FRAGMENT_RESULT_KEY = "PERMISSION_FRAGMENT_RESULT"
        const val HAS_ALL_PERMISSIONS_KEY = "HAS_ALL_PERMISSIONS"
    }
}
