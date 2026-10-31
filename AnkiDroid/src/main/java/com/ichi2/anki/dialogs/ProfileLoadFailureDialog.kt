// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.CommonString
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

class ProfileLoadFailureDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val profileName = requireArguments().requireString(ARG_PROFILE_NAME)
        val defaultProfileName = requireArguments().requireString(ARG_DEFAULT_PROFILE_NAME)
        return AlertDialog.Builder(requireContext()).create {
            title(text = getString(CommonString.profile_load_failed_title, profileName))
            message(text = getString(CommonString.profile_load_failed_message, defaultProfileName, profileName))
            positiveButton(CommonString.dialog_ok)
            neutralButton(text = getString(CommonString.profile_load_failed_use_default, defaultProfileName)) {
                ProfileManager.selectDefaultProfile(requireContext().applicationContext)
            }
        }
    }

    companion object {
        const val TAG = "profileLoadFailure"
        private const val ARG_PROFILE_NAME = "profileName"
        private const val ARG_DEFAULT_PROFILE_NAME = "defaultProfileName"

        fun newInstance(failure: ProfileManager.LoadFailure) =
            ProfileLoadFailureDialog().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_PROFILE_NAME, failure.profileName)
                        putString(ARG_DEFAULT_PROFILE_NAME, failure.defaultProfileName)
                    }
            }
    }
}
