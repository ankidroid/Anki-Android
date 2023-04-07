/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R

/**
 * This is a reusable convenience class which makes it easy to show a confirmation dialog as a DialogFragment.
 * Create a new instance, call setArgs(...), setConfirm(), and setCancel() then show it via the fragment manager as usual.
 */
class ConfirmationDialog : DialogFragment() {
    private var mConfirm = Runnable {} // Do nothing by default
    private var mCancel = Runnable {} // Do nothing by default
    fun setArgs(message: String?) {
        setArgs("", message)
    }

    fun setArgs(title: String?, message: String?) {
        val args = Bundle()
        args.putString("message", message)
        args.putString("title", title)
        arguments = args
    }

    fun setConfirm(confirm: Runnable) {
        mConfirm = confirm
    }

    fun setCancel(cancel: Runnable) {
        mCancel = cancel
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog{
        super.onCreate(savedInstanceState)
        val res = requireActivity().resources
        val title = requireArguments().getString("title")
        return MaterialDialog(requireActivity()).show {
            title(text = (if ("" == title) res.getString(R.string.app_name) else title)!!)
            message(text = requireArguments().getString("message")!!)
            positiveButton(R.string.dialog_ok) {
                mConfirm.run()
            }
            negativeButton(R.string.dialog_cancel) {
                mCancel.run()
            }
        }
    }
}
