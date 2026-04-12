// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.mediacheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebViewClient
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.databinding.FragmentMediaCheckBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.cancelable
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.ichi2.utils.toRGBHex
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MediaCheckFragment for displaying a list of media files that are either unused or missing.
 * It allows users to tag missing media files or delete unused ones.
 **/
class MediaCheckFragment : Fragment(R.layout.fragment_media_check) {
    private val viewModel: MediaCheckViewModel by viewModels()

    private val binding by viewBinding(FragmentMediaCheckBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.apply {
            setTitle(TR.sentenceCase.checkMediaTitle)
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)

        observeProgress(viewModel) { progress -> getString(progress.messageRes) }
        viewModel.checkMedia()

        lifecycleScope.launch {
            viewModel.mediaCheckResult.collectLatest { result ->
                updateWebView(result?.report.orEmpty())
                if (result != null) {
                    binding.tagMissingMediaButton.isVisible = result.missingCount != 0
                    binding.deleteUsedMediaButton.isVisible = result.unusedCount != 0
                    if (result.haveTrash) setupMenu()
                }
            }
        }

        setupButtonListeners()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    menuInflater.inflate(R.menu.media_check_menu, menu)
                    menu.findItem(R.id.action_restore_trash).apply {
                        isVisible = true
                        title = TR.sentenceCase.restoreDeleted
                    }
                    menu.findItem(R.id.action_empty_trash).apply {
                        isVisible = true
                        title = TR.sentenceCase.emptyTrash
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_restore_trash -> {
                            confirmMediaRestore()
                            true
                        }
                        R.id.action_empty_trash -> {
                            deleteTrash()
                            true
                        }
                        else -> false
                    }
            },
            viewLifecycleOwner,
        )
    }

    private fun updateWebView(report: String) {
        val backgroundColor = getColorFromAttr(requireContext(), android.R.attr.colorBackground)
        val textColor = getColorFromAttr(requireContext(), android.R.attr.textColorPrimary)

        val backgroundColorHex = backgroundColor.toRGBHex()
        val textColorHex = textColor.toRGBHex()

        val html =
            """
            <html>
                <body style="
                    background-color: $backgroundColorHex;
                    color: $textColorHex;
                    padding: 0px 8px;
                    font-size:14px;
                    white-space: pre-wrap;">$report
                </body>
            </html>
            """.trimIndent()

        binding.webView.webViewClient = WebViewClient()
        binding.webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun setupButtonListeners() {
        binding.tagMissingMediaButton.apply {
            // mediaCheckAddTag => "Tag Missing"
            text = TR.sentenceCase.tagMissing

            setOnClickListener {
                launchCatchingTask {
                    viewModel.tagMissing(TR.mediaCheckMissingMediaTag()).join()
                    showResultDialog(
                        R.string.check_media_tags_added,
                        TR.browsingNotesUpdated(viewModel.taggedFiles),
                    )
                }
            }
        }

        binding.deleteUsedMediaButton.apply {
            text = TR.sentenceCase.checkMediaDeleteUnused

            setOnClickListener {
                deleteConfirmationDialog()
            }
        }
    }

    private fun confirmMediaRestore() {
        launchCatchingTask {
            viewModel.restoreTrash().join()
            showTrashRestoredDialog()
        }
    }

    private fun deleteTrash() {
        launchCatchingTask {
            viewModel.deleteTrash().join()
            showTrashDeletedDialog()
        }
    }

    private fun deleteConfirmationDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckDeleteUnusedConfirm())
            positiveButton(R.string.dialog_positive_delete) { handleDeleteConfirmation() }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun handleDeleteConfirmation() {
        launchCatchingTask {
            viewModel.deleteUnusedMedia().join()
            showDeletionResult()
        }
    }

    /**
     * Displays the result of a media deletion operation and updates stored trash statistics.
     *
     * This function retrieves the previously stored trash information (if any),
     * combines it with the current deletion statistics from the ViewModel, and
     * updates the stored values accordingly.
     */
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

    private fun showTrashRestoredDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckTrashRestored())
            positiveButton(R.string.dialog_ok) {
                requireActivity().finish()
            }
            cancelable(false)
        }
    }

    private fun showTrashDeletedDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckTrashEmptied())
            positiveButton(R.string.dialog_ok) {
                requireActivity().finish()
            }
            cancelable(false)
        }
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

@get:StringRes
private val MediaCheckProgress.messageRes: Int
    get() =
        when (this) {
            MediaCheckProgress.CHECKING_MEDIA -> R.string.check_media_message
            MediaCheckProgress.ADDING_TAGS -> R.string.check_media_adding_missing_tag
            MediaCheckProgress.DELETING_MEDIA -> R.string.delete_media_message
        }
