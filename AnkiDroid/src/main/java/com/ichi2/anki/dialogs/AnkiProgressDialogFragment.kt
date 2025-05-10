/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2025 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import timber.log.Timber

/**
 * A DialogFragment implementation for showing progress indicators.
 *
 * This replaces the deprecated ProgressDialog with a DialogFragment that
 * handles configuration changes and provides a consistent UI across Android versions.
 *
 * ProgressDialog was deprecated in API level 26 (Android 8.0) because:
 * - It doesn't handle configuration changes (like screen rotation) correctly
 * - It doesn't follow Material Design guidelines
 * - It can lead to leaked window errors in certain scenarios
 *
 * Benefits of this DialogFragment implementation:
 * - Survives configuration changes automatically
 * - Follows Material Design guidelines using MaterialAlertDialogBuilder
 * - Prevents memory leaks through proper lifecycle management
 * - Provides a consistent UI experience across all Android versions
 */
class AnkiProgressDialogFragment : DialogFragment() {
    var message: String = ""
    private var cancelableViaBackButton: Boolean = false
    private var cancelButtonTextResId: Int? = null
    private var onCancelListener: (() -> Unit)? = null
    private var isIndeterminate: Boolean = true
    private var progress: Int = 0
    private var maxProgress: Int = 100

    @VisibleForTesting
    var progressMessageView: TextView? = null
    private lateinit var indeterminateProgressBar: ProgressBar
    private lateinit var determinateProgressBar: ProgressBar
    private lateinit var progressCounterView: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        restoreSavedState(savedInstanceState)

        val builder = MaterialAlertDialogBuilder(requireContext())
        val view = layoutInflater.inflate(R.layout.new_anki_progress_dialog, null)

        progressMessageView = view.findViewById(R.id.progress_message)
        indeterminateProgressBar = view.findViewById(R.id.indeterminate_progress_bar)
        determinateProgressBar = view.findViewById(R.id.determinate_progress_bar)
        progressCounterView = view.findViewById(R.id.progress_counter)

        progressMessageView?.text = message

        if (isIndeterminate) {
            indeterminateProgressBar.visibility = View.VISIBLE
            determinateProgressBar.visibility = View.GONE
        } else {
            indeterminateProgressBar.visibility = View.GONE
            determinateProgressBar.visibility = View.VISIBLE
            determinateProgressBar.max = maxProgress
            determinateProgressBar.progress = progress
            progressCounterView.visibility = View.VISIBLE
            progressCounterView.text = "$progress / $maxProgress"
        }

        builder.setView(view)
        isCancelable = cancelableViaBackButton

        if (cancelButtonTextResId != null) {
            builder.setNegativeButton(cancelButtonTextResId!!) { _, _ ->
                Timber.i("Progress dialog cancelled via cancel button")
                onCancelListener?.invoke()
            }
        }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_MESSAGE, message)
        outState.putBoolean(KEY_CANCELABLE, cancelableViaBackButton)
        outState.putInt(KEY_CANCEL_BUTTON, cancelButtonTextResId ?: -1)
        outState.putBoolean(KEY_INDETERMINATE, isIndeterminate)
        outState.putInt(KEY_PROGRESS, progress)
        outState.putInt(KEY_MAX_PROGRESS, maxProgress)
    }

    private fun restoreSavedState(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            message = it.getString(KEY_MESSAGE, "")
            cancelableViaBackButton = it.getBoolean(KEY_CANCELABLE, false)
            val cancelBtnId = it.getInt(KEY_CANCEL_BUTTON, -1)
            cancelButtonTextResId = if (cancelBtnId != -1) cancelBtnId else null
            isIndeterminate = it.getBoolean(KEY_INDETERMINATE, true)
            progress = it.getInt(KEY_PROGRESS, 0)
            maxProgress = it.getInt(KEY_MAX_PROGRESS, 100)
        }
    }

    fun updateMessage(newMessage: String) {
        message = newMessage
        progressMessageView?.text = newMessage
    }

    /**
     * Updates the progress amount for determinate progress bars
     */
    fun updateProgress(
        current: Int,
        max: Int,
    ) {
        progress = current
        maxProgress = max
        isIndeterminate = false

        if (::indeterminateProgressBar.isInitialized && ::determinateProgressBar.isInitialized) {
            // Switch from indeterminate to determinate progress bar
            indeterminateProgressBar.visibility = View.GONE
            determinateProgressBar.visibility = View.VISIBLE

            determinateProgressBar.max = max
            determinateProgressBar.progress = current

            progressCounterView.visibility = View.VISIBLE
            progressCounterView.text = "$current / $max"
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelListener?.invoke()
    }

    companion object {
        const val TAG = "AnkiProgressDialogFragment"

        private const val KEY_MESSAGE = "message"
        private const val KEY_CANCELABLE = "cancelableViaBackButton"
        private const val KEY_CANCEL_BUTTON = "cancel_button"
        private const val KEY_INDETERMINATE = "indeterminate"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_MAX_PROGRESS = "max_progress"

        fun newInstance(
            message: String,
            cancelableViaBackButton: Boolean = false,
            @StringRes cancelButtonTextResId: Int? = null,
            onCancelListener: (() -> Unit)? = null,
        ): AnkiProgressDialogFragment =
            AnkiProgressDialogFragment().apply {
                this.message = message
                this.cancelableViaBackButton = cancelableViaBackButton
                this.cancelButtonTextResId = cancelButtonTextResId
                this.onCancelListener = onCancelListener
                this.isIndeterminate = true
            }
    }
}
