/****************************************************************************************
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

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R
import com.ichi2.utils.cancelable
import com.ichi2.utils.create
import com.ichi2.utils.customView

/**
 * Simple [DialogFragment] to be used to show a "loading" ui state. Shows an indeterminate
 * [CircularProgressIndicator] and a customizable(optional) text.
 */
class LoadingDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_loading, null)
        val canBeCancelled = arguments?.getBoolean(KEY_CANCELLABLE) ?: false
        dialogView.findViewById<TextView>(R.id.text).text =
            arguments?.getString(KEY_MESSAGE) ?: getString(R.string.dialog_processing)
        return AlertDialog
            .Builder(requireContext())
            .create {
                customView(dialogView)
                cancelable(canBeCancelled)
            }.apply { setCanceledOnTouchOutside(canBeCancelled) }
    }

    companion object {
        const val TAG = "loading_dialog_fragment_tag"
        private const val KEY_MESSAGE = "key_message"
        private const val KEY_CANCELLABLE = "key_cancellable"

        /**
         * Creates an instance of [LoadingDialogFragment].
         * @param message optional message for the loading operation, will default to
         * [R.string.dialog_processing] if not provided
         * @param cancellable if the dialog should be cancellable or not(also affects cancel when
         * touching outside the dialog window). Defaults to false.
         */
        fun newInstance(
            message: String? = null,
            cancellable: Boolean = false,
        ) = LoadingDialogFragment().apply {
            arguments =
                bundleOf(
                    KEY_MESSAGE to message,
                    KEY_CANCELLABLE to cancellable,
                )
        }
    }
}
