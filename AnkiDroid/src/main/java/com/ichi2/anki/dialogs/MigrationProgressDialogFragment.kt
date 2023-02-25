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
import android.text.format.Formatter.formatShortFileSize
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.services.MigrationService
import com.ichi2.anki.services.withBoundTo
import kotlinx.coroutines.launch

/**
 * A dialog showing the progress of migration of the collection
 * from public storage to app-private storage.
 * It attaches to the migration service, and, while showing,
 * constantly updates the amount of transferred data,
 * and displays messages in cases of success or failure.
 * Dismissible.
 */
class MigrationProgressDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = requireActivity().layoutInflater.inflate(R.layout.indeterminate_progress_bar, null)
        val progressBar = layout.findViewById<ProgressBar>(R.id.indeterminate_progressBar)
        val textView = layout.findViewById<TextView>(R.id.migration_text)

        progressBar.max = Int.MAX_VALUE

        fun publishProgress(progress: MigrationService.Progress) {
            when (progress) {
                is MigrationService.Progress.CalculatingTransferSize -> {
                    progressBar.isIndeterminate = true
                    textView.text = getString(R.string.migration__calculating_transfer_size)
                }

                is MigrationService.Progress.Transferring -> {
                    val transferredSizeText = formatShortFileSize(requireContext(), progress.transferredBytes)
                    val totalSizeText = formatShortFileSize(requireContext(), progress.totalBytes)

                    progressBar.isIndeterminate = false
                    progressBar.progress = (progress.ratio * Int.MAX_VALUE).toInt()
                    textView.text = getString(R.string.migration__transferred_x_of_y, transferredSizeText, totalSizeText)
                }

                is MigrationService.Progress.Success -> {
                    progressBar.isIndeterminate = false
                    progressBar.progress = Int.MAX_VALUE
                    textView.text = getString(R.string.migration_successful_message)
                }

                is MigrationService.Progress.Failure -> {
                    textView.text = getString(R.string.migration__failed, progress.e)
                }
            }
        }

        lifecycleScope.launch {
            requireContext().withBoundTo<MigrationService> { service ->
                service.flowOfProgress
                    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                    .collect { progress -> publishProgress(progress) }
            }
        }

        return AlertDialog.Builder(activity)
            .setView(layout)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> dismiss() }
            .setNegativeButton(R.string.scoped_storage_learn_more) { _, _ ->
                (requireActivity() as AnkiActivity).openUrl(R.string.link_scoped_storage_faq)
            }
            .create()
    }
}
