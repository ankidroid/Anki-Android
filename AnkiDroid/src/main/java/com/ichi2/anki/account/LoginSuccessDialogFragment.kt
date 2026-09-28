// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.account

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import timber.log.Timber

class LoginSuccessDialogFragment : DialogFragment() {
    enum class Action {
        SYNC,
        CONTINUE,
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext()).create {
            Timber.i("Showing dialog: 'Sync now?'")
            setTitle(R.string.login_successful)
            setIcon(R.drawable.ic_sync)
            setMessage(R.string.sync_now)
            positiveButton(R.string.button_sync) { reportResult(Action.SYNC) }
            negativeButton(R.string.dialog_continue) { reportResult(Action.CONTINUE) }
        }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        reportResult(Action.CONTINUE)
    }

    private fun reportResult(action: Action) {
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_ACTION to action))
    }

    companion object {
        const val REQUEST_KEY = "LoginSuccessDialogFragment_request"
        const val KEY_ACTION = "action"

        fun actionFrom(bundle: Bundle): Action? = BundleCompat.getSerializable(bundle, KEY_ACTION, Action::class.java)
    }
}
