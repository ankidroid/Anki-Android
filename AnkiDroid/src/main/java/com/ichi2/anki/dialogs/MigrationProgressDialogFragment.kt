/*
 Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.viewmodels.MigrationProgressViewModel
import kotlinx.coroutines.launch

class MigrationProgressDialogFragment :
    DialogFragment() {
    private val progressViewModel by activityViewModels<MigrationProgressViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val messageTextView: TextView
        val progressBar: ProgressBar
        val progressView =
            requireActivity().layoutInflater.inflate(R.layout.indeterminate_progress_bar, null)
        progressBar = progressView.findViewById(R.id.indeterminate_progressBar)
        messageTextView = progressView.findViewById(R.id.migration_text)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                progressViewModel.migrationProgressFlow.collect { state ->
                    progressBar.max = state.totalMb
                    progressBar.progress = state.transferredMb
                    messageTextView.text = resources.getString(
                        R.string.scoped_storage_migration_progress
                    )
                }
            }
        }
        return AlertDialog.Builder(activity)
            .setView(progressView)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                dismiss()
            }
            .setNegativeButton(R.string.scoped_storage_learn_more) { _, _ ->
                (requireActivity() as AnkiActivity).openUrl(R.string.link_scoped_storage_faq)
            }
            .create()
    }
}

data class MigrationProgress(val transferredMb: Int, val totalMb: Int)
