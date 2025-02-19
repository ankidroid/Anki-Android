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

package com.ichi2.anki.mediacheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.libanki.MediaCheckResult
import com.ichi2.utils.cancelable
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MediaCheckFragment for displaying a list of media files that are either unused or missing.
 * It allows users to tag missing media files or delete unused ones.
 **/
class MediaCheckFragment : Fragment(R.layout.fragment_media_check) {
    private val viewModel: MediaCheckViewModel by viewModels()
    private lateinit var adapter: MediaCheckAdapter

    private lateinit var deleteMediaButton: MaterialButton
    private lateinit var tagMissingButton: MaterialButton

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(TR.mediaCheckCheckMediaAction().toSentenceCase(requireContext(), R.string.check_media))
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        deleteMediaButton = view.findViewById(R.id.delete_used_media_button)
        tagMissingButton = view.findViewById(R.id.tag_missing_media_button)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)

        adapter = MediaCheckAdapter(requireContext())
        recyclerView.adapter = adapter

        launchCatchingTask {
            withProgress(R.string.check_media_message) {
                viewModel.checkMedia().join()
            }
        }

        lifecycleScope.launch {
            viewModel.mediaCheckResult.collectLatest { result ->
                view.findViewById<TextView>(R.id.unused_media_count)?.apply {
                    text = (TR.mediaCheckUnusedCount(result?.unusedFileNames?.size ?: 0))
                }

                view.findViewById<TextView>(R.id.missing_media_count)?.apply {
                    text = (TR.mediaCheckMissingCount(result?.missingMediaNotes?.size ?: 0))
                }

                result?.let { files ->
                    handleMediaResult(files)
                }
            }
        }

        setupButtonListeners()
    }

    /**
     * Processes media check results and updates the UI.
     *
     * @param mediaCheckResult The result containing missing and unused media file names.
     */
    private fun handleMediaResult(mediaCheckResult: MediaCheckResult) {
        val fileList =
            buildList {
                if (mediaCheckResult.missingFileNames.isNotEmpty()) {
                    tagMissingButton.visibility = View.VISIBLE
                    add(TR.mediaCheckMissingHeader())
                    addAll(mediaCheckResult.missingFileNames.map(TR::mediaCheckMissingFile))
                }
                if (mediaCheckResult.unusedFileNames.isNotEmpty()) {
                    deleteMediaButton.visibility = View.VISIBLE
                    if (isNotEmpty()) add("\n")
                    add(TR.mediaCheckUnusedHeader())
                    addAll(mediaCheckResult.unusedFileNames.map(TR::mediaCheckUnusedFile))
                }
            }

        adapter.submitList(fileList)
    }

    private fun setupButtonListeners() {
        tagMissingButton.apply {
            // mediaCheckAddTag => "Tag Missing"
            text = TR.mediaCheckAddTag().toSentenceCase(requireContext(), R.string.sentence_tag_missing)

            setOnClickListener {
                launchCatchingTask {
                    withProgress(getString(R.string.check_media_adding_missing_tag)) {
                        viewModel.tagMissing(TR.mediaCheckMissingMediaTag()).join()
                        showResultDialog(
                            R.string.check_media_tags_added,
                            TR.browsingNotesUpdated(viewModel.taggedFiles),
                        )
                    }
                }
            }
        }

        deleteMediaButton.apply {
            text =
                TR.mediaCheckDeleteUnused().toSentenceCase(
                    requireContext(),
                    R.string.sentence_check_media_delete_unused,
                )

            setOnClickListener {
                deleteConfirmationDialog()
            }
        }
    }

    private fun deleteConfirmationDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckDeleteUnusedConfirm())
            positiveButton(R.string.dialog_ok) { handleDeleteConfirmation() }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun handleDeleteConfirmation() {
        launchCatchingTask {
            withProgress(resources.getString(R.string.delete_media_message)) {
                viewModel.deleteUnusedMedia().join()
                showDeletionResult()
            }
        }
    }

    private fun showDeletionResult() {
        showResultDialog(
            R.string.delete_media_result_title,
            resources.getQuantityString(
                R.plurals.delete_media_result_message,
                viewModel.deletedFiles,
                viewModel.deletedFiles,
            ),
        )
    }

    private fun showResultDialog(
        titleRes: Int,
        message: String,
    ) {
        AlertDialog.Builder(requireContext()).show {
            title(titleRes)
            message(text = message)
            positiveButton(R.string.dialog_ok) {
                requireActivity().finish()
            }
            cancelable(false)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, MediaCheckFragment::class)
    }
}
