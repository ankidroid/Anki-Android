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
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A DialogFragment implementation for showing progress indicators.
 */
open class AnkiProgressDialogFragment : DialogFragment() {
    @VisibleForTesting
    val viewModel: AnkiProgressDialogViewModel by viewModels()

    private var progressMessageView: TextView? = null
    private lateinit var indeterminateProgressBar: ProgressBar
    private lateinit var determinateProgressBar: ProgressBar
    private lateinit var progressCounterView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupViewModelFromArguments()
    }

    private fun setupViewModelFromArguments() {
        if (viewModel.message.value.isNotBlank()) {
            // Already configured (e.g., after config change)
            return
        }
        arguments?.let { args ->
            val message = args.getString(ARG_MESSAGE, "")
            val cancelable = args.getBoolean(ARG_CANCELABLE, false)
            val cancelButtonText = args.getString(ARG_CANCEL_BUTTON_TEXT, null)

            Timber.d("Setting up ViewModel with message: '%s'", message)
            viewModel.setup(
                message = message,
                cancelableViaBackButton = cancelable,
                cancelButtonText = cancelButtonText,
                onCancelListener = cancelListener,
            )
            cancelListener = null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.new_anki_progress_dialog, null)
        initializeViews(view)
        updateUiFromViewModel()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                setupCancelButton(this)
            }
    }

    private fun initializeViews(view: View) {
        progressMessageView = view.findViewById(R.id.progress_message)
        indeterminateProgressBar = view.findViewById(R.id.indeterminate_progress_bar)
        determinateProgressBar = view.findViewById(R.id.determinate_progress_bar)
        progressCounterView = view.findViewById(R.id.progress_counter)
    }

    private fun setupCancelButton(dialog: AlertDialog) {
        viewModel.cancelButtonText.value?.let { buttonText ->
            dialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                buttonText,
            ) { _, _ ->
                Timber.i("Progress dialog cancelled via cancel button")
                viewModel.cancel()
            }
        }
    }

    /**
     * Sets up the initial UI state from the ViewModel's values
     */
    private fun updateUiFromViewModel() {
        try {
            updateMessageUi(viewModel.message.value)
            updateProgressUi(viewModel.progress.value)
            updateCancelableState(viewModel.cancelableViaBackButton.value)
        } catch (e: Exception) {
            Timber.w(e, "Error in updateUiFromViewModel")
        }
    }

    private fun updateMessageUi(message: String) {
        progressMessageView?.let {
            it.text = message
        } ?: Timber.w("progressMessageView is null during message update")
    }

    private fun updateProgressUi(progress: AnkiProgressDialogViewModel.Progress?) {
        val isIndeterminate = progress == null

        if (isIndeterminate) {
            showIndeterminateProgress()
        } else {
            showDeterminateProgress(progress!!.currentProgress, progress.maxProgress)
        }
    }

    private fun showIndeterminateProgress() {
        indeterminateProgressBar.visibility = View.VISIBLE
        determinateProgressBar.visibility = View.GONE
        progressCounterView.visibility = View.GONE
    }

    private fun showDeterminateProgress(
        current: Int,
        max: Int,
    ) {
        indeterminateProgressBar.visibility = View.GONE
        determinateProgressBar.visibility = View.VISIBLE
        progressCounterView.visibility = View.VISIBLE

        determinateProgressBar.max = max
        determinateProgressBar.progress = current
        updateProgressCounter()
    }

    private fun updateCancelableState(cancelable: Boolean) {
        isCancelable = cancelable
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupStateFlowCollectors()
    }

    private fun setupStateFlowCollectors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.message.collectLatest { message ->
                        updateMessageUi(message)
                    }
                }

                launch {
                    viewModel.progress.collect { progress ->
                        updateProgressUi(progress)
                    }
                }

                launch {
                    viewModel.cancelableViaBackButton.collectLatest { cancelable ->
                        updateCancelableState(cancelable)
                    }
                }
            }
        }
    }

    private fun updateProgressCounter() {
        val progress = viewModel.progress.value ?: return
        progressCounterView.visibility = View.VISIBLE
        progressCounterView.text = "${progress.currentProgress} / ${progress.maxProgress}"
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.cancel()
    }

    fun updateMessage(newMessage: String) {
        if (newMessage.isBlank()) {
            Timber.w("Empty message provided to updateMessage, keeping existing message")
            return
        }

        try {
            viewModel.updateMessage(newMessage)

            progressMessageView?.let { textView ->
                if (isAdded && !isDetached) {
                    activity?.runOnUiThread {
                        textView.text = newMessage
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Exception while updating message in progress dialog")
        }
    }

    fun updateProgress(
        current: Int,
        max: Int,
    ) {
        try {
            viewModel.updateProgress(current, max)

            if (isAdded && !isDetached) {
                activity?.runOnUiThread {
                    indeterminateProgressBar.visibility = View.GONE
                    determinateProgressBar.visibility = View.VISIBLE
                    progressCounterView.visibility = View.VISIBLE

                    determinateProgressBar.max = max
                    determinateProgressBar.progress = current
                    progressCounterView.text = "$current / $max"
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Exception while updating progress in progress dialog")
        }
    }

    fun setOnCancelListener(listener: (() -> Unit)?) {
        viewModel.setOnCancelListener(listener)
    }

    companion object {
        const val TAG = "AnkiProgressDialogFragment"

        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_CANCELABLE = "arg_cancelable"
        private const val ARG_CANCEL_BUTTON_TEXT = "arg_cancel_button_text"

        private var cancelListener: (() -> Unit)? = null

        fun newInstance(
            message: String,
            cancellationConfig: ProgressDialogCancellationConfig = ProgressDialogCancellationConfig(),
        ): AnkiProgressDialogFragment =
            AnkiProgressDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_MESSAGE, message)
                        putBoolean(ARG_CANCELABLE, cancellationConfig.cancelableViaBackButton)
                        cancellationConfig.cancelButtonText?.let {
                            putString(ARG_CANCEL_BUTTON_TEXT, it)
                        }
                    }

                cancelListener = cancellationConfig.onCancel
            }
    }
}
