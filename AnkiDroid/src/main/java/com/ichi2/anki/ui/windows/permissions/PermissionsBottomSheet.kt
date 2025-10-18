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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R

/**
 * BottomSheet that requests permissions from the user.
 *
 * The full-screen [PermissionsActivity] which launches on initial app installation should be used to request
 * mandatory permissions from the user that AnkiDroid cannot run without. This more relaxed BottomSheet
 * should be used to request optional permissions from the user, and can be launched as the user gradually
 * encounters features that require permissions rather than being shoved in the face of every first-time user.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PermissionsBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.permissions_bottom_sheet, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val closeButton = view.findViewById<ImageButton>(R.id.close_button)
        closeButton.setOnClickListener { dismiss() }

        val permissionSet =
            requireNotNull(BundleCompat.getParcelable(requireArguments(), PERMISSION_SET_ARGUMENT_KEY, PermissionSet::class.java)) {
                "Permission set cannot be null"
            }
        val permissionsFragment =
            requireNotNull(permissionSet.permissionsFragment?.getDeclaredConstructor()?.newInstance()) {
                "invalid permissionsFragment"
            }
        view.post {
            childFragmentManager.commit {
                replace(R.id.bottom_sheet_fragment_container, permissionsFragment)
            }
        }
    }

    companion object {
        /**
         * Unique fragment tag for launching this bottom sheet.
         */
        private const val FRAGMENT_TAG = "notifications_bottom_sheet"

        /**
         * Arguments key for the [PermissionSet] to launch this BottomSheet with.
         */
        private const val PERMISSION_SET_ARGUMENT_KEY = "permission_set"

        /**
         * Starts this BottomSheet with the provided [PermissionSet].
         */
        fun launch(
            fragmentManager: FragmentManager,
            permissionsSet: PermissionSet,
        ) {
            val bottomSheet =
                PermissionsBottomSheet().apply {
                    arguments =
                        Bundle().apply {
                            putParcelable(PERMISSION_SET_ARGUMENT_KEY, permissionsSet)
                        }
                }
            bottomSheet.show(fragmentManager, FRAGMENT_TAG)
        }
    }
}
