/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsFragment
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.DismissType
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.AndroidUiUtils.setFocusAndOpenKeyboard
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Allows a user to edit the [deck description][com.ichi2.anki.libanki.Deck.description]
 *
 * This is visible on [StudyOptionsFragment]
 */
class EditDeckDescriptionDialog : DialogFragment() {
    private val viewModel: EditDeckDescriptionDialogViewModel by viewModels()

    private lateinit var alertDialog: AlertDialog
    private lateinit var dialogView: View

    private val deckDescriptionInput: TextInputEditText
        get() = dialogView.findViewById(R.id.deck_description_input)

    private val formatAsMarkdownInput: CheckBox
        get() = dialogView.findViewById(R.id.format_as_markdown)

    private val toolbar: MaterialToolbar
        get() = dialogView.findViewById(R.id.topAppBar)

    private val onUnsavedChangesBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                showDiscardChangesDialog()
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        this.dialogView = layoutInflater.inflate(R.layout.dialog_deck_description, null)
        return MaterialAlertDialogBuilder(requireContext())
            .create {
                setView(dialogView)
                positiveButton(R.string.save)
                negativeButton(R.string.close)
            }.apply {
                alertDialog = this
                setOnShowListener {
                    positiveButton.setOnClickListener { viewModel.saveAndExit() }
                    negativeButton.setOnClickListener { viewModel.onBackRequested() }
                }
                setCanceledOnTouchOutside(false)
                setCancelable(false)
                onBackPressedDispatcher.addCallback(this, onUnsavedChangesBackCallback)
                show()
                setupDialogView(dialogView)
            }
    }

    private fun setupDialogView(view: View) {
        deckDescriptionInput.apply {
            doOnTextChanged { text, _, _, _ ->
                viewModel.description = text?.toString() ?: ""
            }
        }

        formatAsMarkdownInput.apply {
            setOnCheckedChangeListener { _, value -> viewModel.formatAsMarkdown = value }
        }

        // setup 'Format as Markdown' help
        view.findViewById<ImageButton>(R.id.markdown_formatting_help).apply {
            contentDescription =
                getString(R.string.help_button_content_description, getString(R.string.format_deck_description_as_markdown))
            setOnClickListener {
                MaterialAlertDialogBuilder(requireContext()).show {
                    setTitle(formatAsMarkdownInput.text)
                    setIcon(R.drawable.ic_help_black_24dp)
                    // FIXME: the upstream string unexpectedly contains newlines
                    setMessage(TR.deckConfigDescriptionNewHandlingHint().replace("\n", " ").replace("  ", " "))
                }
            }
        }

        setupFlows()
    }

    private fun setupFlows() {
        lifecycleScope.launch {
            viewModel.flowOfDismissDialog
                .filterNotNull()
                .collect { dismissType ->
                    when (dismissType) {
                        DismissType.ClosedWithoutSaving -> dismiss()
                        DismissType.Saved -> {
                            dismiss()
                            showSnackbar(R.string.deck_description_saved)
                            // notify DeckPicker to invalidate its toolbar menu, otherwise the undo
                            // action to revert the description change is not going to be visible
                            // when there are no other undo actions
                            requireActivity().invalidateOptionsMenu()
                        }
                    }
                }
        }

        lifecycleScope.launch {
            viewModel.flowOfDescription.collect { desc ->
                if (desc == deckDescriptionInput.text.toString()) return@collect
                deckDescriptionInput.setText(desc)
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfFormatAsMarkdown.collect {
                formatAsMarkdownInput.isChecked = it
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfInitCompleted.collect {
                if (!it) return@collect
                toolbar.title = viewModel.windowTitle
                setFocusAndOpenKeyboard(deckDescriptionInput) { deckDescriptionInput.setSelection(deckDescriptionInput.text!!.length) }
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfShowDiscardChanges.collect {
                showDiscardChangesDialog()
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfHasChanges.collect {
                alertDialog.positiveButton.isEnabled = it
                onUnsavedChangesBackCallback.isEnabled = it
            }
        }
    }

    fun showDiscardChangesDialog() {
        Timber.i("asking if user should discard changes")
        DiscardChangesDialog.showDialog(requireContext()) {
            viewModel.closeWithoutSaving()
        }
    }

    companion object {
        fun newInstance(deckId: DeckId): EditDeckDescriptionDialog =
            EditDeckDescriptionDialog().apply {
                arguments =
                    bundleOf(
                        EditDeckDescriptionDialogViewModel.ARG_DECK_ID to deckId,
                    )
            }
    }
}
