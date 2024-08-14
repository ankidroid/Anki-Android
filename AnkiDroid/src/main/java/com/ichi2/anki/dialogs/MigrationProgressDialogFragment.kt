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

import android.app.Dialog
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.services.MigrationService
import kotlinx.coroutines.flow.filterNotNull
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
        val layout = requireActivity().layoutInflater.inflate(R.layout.dialog_with_progress_indicator, null)
        val progressBar = layout.findViewById<CircularProgressIndicator>(R.id.progress_indicator)
        val textView = layout.findViewById<TextView>(R.id.text)

        progressBar.max = Int.MAX_VALUE

        fun publishProgress(progress: MigrationService.Progress.MovingMediaFiles) {
            when (progress) {
                is MigrationService.Progress.MovingMediaFiles.CalculatingNumberOfBytesToMove -> {
                    progressBar.isIndeterminate = true
                    textView.text = getString(R.string.migration__calculating_transfer_size)
                }

                is MigrationService.Progress.MovingMediaFiles.MovingFiles -> {
                    val movedSizeText = formatShortFileSize(requireContext(), progress.movedBytes)
                    val totalSizeText = formatShortFileSize(requireContext(), progress.totalBytes)

                    progressBar.isIndeterminate = false
                    progressBar.progress = (progress.ratio * Int.MAX_VALUE).toInt()
                    textView.text = getString(R.string.migration__moved_x_of_y, movedSizeText, totalSizeText)
                }
            }
        }

        lifecycleScope.launch {
            MigrationService.flowOfProgress
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .filterNotNull()
                .collect { progress ->
                    when (progress) {
                        // The dialog should not be accessible when copying essential files
                        is MigrationService.Progress.CopyingEssentialFiles -> {}

                        is MigrationService.Progress.MovingMediaFiles -> publishProgress(progress)

                        // MigrationSucceededDialogFragment or MigrationFailedDialogFragment
                        // is going to be shown instead.
                        is MigrationService.Progress.Done -> dismiss()
                    }
                }
        }

        return AlertDialog.Builder(requireActivity())
            .setView(layout)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> dismiss() }
            .setNegativeButton(R.string.scoped_storage_learn_more) { _, _ ->
                (requireActivity() as AnkiActivity).openUrl(R.string.link_scoped_storage_faq)
            }
            .create()
    }
}
