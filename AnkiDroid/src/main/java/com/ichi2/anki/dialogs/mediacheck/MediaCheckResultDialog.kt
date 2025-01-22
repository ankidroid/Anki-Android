/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs.mediacheck

import android.app.Dialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.viewmodel.MediaCheckViewModel
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.libanki.MediaCheckResult
import com.ichi2.libanki.utils.isNullOrEmpty
import kotlinx.coroutines.launch

class MediaCheckResultDialog : DialogFragment() {
    private val viewModel: MediaCheckViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog =
            AlertDialog
                .Builder(requireContext())
                .setTitle(TR.mediaCheckCheckMediaAction())

        val dialogBody = layoutInflater.inflate(R.layout.media_check_dialog_body, null) as LinearLayout
        val reportTextView = dialogBody.findViewById<TextView>(R.id.reportTextView)
        val fileListTextView = dialogBody.findViewById<TextView>(R.id.fileListTextView)

        viewModel.mediaCheckResult?.let {
            setupDialogBody(
                reportTextView,
                fileListTextView,
                it,
            )
        }

        dialog
            .setView(dialogBody)
            .setCancelable(false)
            .apply {
                val hasUnusedFiles = !viewModel.mediaCheckResult?.unusedFileNames.isNullOrEmpty()
                val hasMissingFiles = !viewModel.mediaCheckResult?.missingFileNames.isNullOrEmpty()

                if (hasUnusedFiles || hasMissingFiles) {
                    if (hasUnusedFiles) {
                        setPositiveButton(
                            TR.mediaCheckDeleteUnused().toSentenceCase(
                                requireContext(),
                                R.string.check_media_delete_unused,
                            ),
                        ) { _, _ ->
                            lifecycleScope.launch {
                                viewModel.deleteUnusedMedia()
                            }
                        }
                    }

                    if (hasMissingFiles) {
                        setNegativeButton(
                            TR
                                .mediaCheckAddTag()
                                .toSentenceCase(requireContext(), R.string.tag_missing),
                        ) { _, _ ->
                            lifecycleScope.launch {
                                viewModel.tagMissingMediaFiles()
                            }
                        }
                    }

                    setNeutralButton(R.string.dialog_cancel) { _, _ -> dismiss() }
                } else {
                    setPositiveButton(R.string.dialog_ok) { _, _ -> dismiss() }
                }
            }

        return dialog.create()
    }

    private fun setupDialogBody(
        reportTextView: TextView,
        fileListTextView: TextView,
        result: MediaCheckResult,
    ) {
        reportTextView.text = createReportString(generateReport(result.unusedFileNames, result.missingFileNames, result.invalidFileNames))

        if (result.unusedFileNames.isNotEmpty() || result.missingFileNames.isNotEmpty()) {
            fileListTextView.text = formatMissingAndUnusedFiles(result.missingFileNames, result.unusedFileNames)
            fileListTextView.isScrollbarFadingEnabled =
                result.unusedFileNames.size + result.missingFileNames.size <= fileListTextView.maxLines
            fileListTextView.movementMethod = ScrollingMovementMethod.getInstance()
            fileListTextView.setTextIsSelectable(true)
        } else {
            fileListTextView.visibility = View.GONE
        }
    }

    private fun generateReport(
        unused: List<String>,
        noHave: List<String>,
        invalid: List<String>,
    ): String {
        val report = StringBuilder()
        if (invalid.isNotEmpty()) {
            report.append(String.format(getString(R.string.check_media_invalid), invalid.size))
        }

        if (noHave.isNotEmpty()) {
            if (report.isNotEmpty()) {
                report.append("\n")
            }
            report.append(TR.mediaCheckMissingCount(noHave.size))
        }

        if (unused.isNotEmpty()) {
            if (report.isNotEmpty()) {
                report.append("\n")
            }
            report.append(TR.mediaCheckUnusedCount(unused.size))
        }
        if (report.isEmpty()) {
            report.append(getString(R.string.check_media_no_unused_missing))
        }
        return report.toString()
    }

    private fun createReportString(report: String): String =
        """
        |$report
        """.trimMargin().trimIndent()

    private fun formatMissingAndUnusedFiles(
        noHave: List<String>,
        unused: List<String>,
    ): String {
        val noHaveFormatted = noHave.joinToString("\n") { missingMedia -> TR.mediaCheckMissingFile(missingMedia) }
        val unusedFormatted = unused.joinToString("\n") { unusedMedia -> TR.mediaCheckUnusedFile(unusedMedia) }

        return buildString {
            if (noHaveFormatted.isNotEmpty()) {
                append(TR.mediaCheckMissingHeader())
                append("\n")
                append(noHaveFormatted)
                append("\n\n")
            }
            if (unusedFormatted.isNotEmpty()) {
                append(TR.mediaCheckUnusedHeader())
                append("\n")
                append(unusedFormatted)
            }
        }
    }
}
