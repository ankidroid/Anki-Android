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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.isCheckedState
import com.ichi2.anki.ui.textOf
import com.ichi2.anki.utils.ext.update
import com.ichi2.utils.AndroidUiUtils.setFocusAndOpenKeyboard
import com.ichi2.utils.show
import timber.log.Timber

/**
 * Allows a user to edit the [deck description][description]
 *
 * This is visible on [StudyOptionsFragment]
 */
class EditDeckDescriptionDialog : DialogFragment() {
    private val deckId: DeckId
        get() = requireArguments().getLong(ARG_DECK_ID)

    private lateinit var dialogView: View

    private val deckDescriptionInput: TextInputEditText
        get() = dialogView.findViewById(R.id.deck_description_input)

    private val formatAsMarkdownInput: CheckBox
        get() = dialogView.findViewById(R.id.format_as_markdown)

    private val topAppBar: MaterialToolbar
        get() = dialogView.findViewById(R.id.topAppBar)

    private val saveMenuItem: MenuItem
        get() = topAppBar.menu.findItem(R.id.action_save)

    // immutable state

    private lateinit var initialDialogState: DeckDescriptionState

    // state / user inputs

    /** @see [com.ichi2.libanki.Deck.description] */
    private var description by textOf { deckDescriptionInput }

    /** @see [com.ichi2.libanki.Deck.markdownDescription] */
    private var formatAsMarkdown by isCheckedState { formatAsMarkdownInput }

    // derived state

    private val currentDialogState: DeckDescriptionState
        get() =
            DeckDescriptionState(
                description = this.description,
                formatAsMarkdown = this.formatAsMarkdown,
            )

    private val onUnsavedChangesBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                showDiscardChangesDialog()
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        this.dialogView = layoutInflater.inflate(R.layout.dialog_deck_description, null)
        return MaterialAlertDialogBuilder(requireContext())
            .show {
                setView(dialogView)
            }.apply {
                setupDialogView(dialogView)
                setCanceledOnTouchOutside(false)
                setCancelable(false)
                onBackPressedDispatcher.addCallback(this, onUnsavedChangesBackCallback)
            }
    }

    fun setupDialogView(view: View) {
        // load initial state
        launchCatchingTask {
            val dialog = this@EditDeckDescriptionDialog
            queryDescriptionState().let { state ->
                initialDialogState = state
                dialog.description = state.description
                dialog.formatAsMarkdown = state.formatAsMarkdown
            }

            deckDescriptionInput.doAfterTextChanged { checkForChanges() }
            formatAsMarkdownInput.setOnCheckedChangeListener { _, _ -> checkForChanges() }
            checkForChanges()
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

        // setup App Bar
        topAppBar
            .apply {
                setNavigationOnClickListener {
                    onBack()
                }

                setOnMenuItemClickListener { menuItem ->
                    if (menuItem.itemId == R.id.action_save) {
                        saveAndExit()
                        true
                    } else {
                        false
                    }
                }
            }.also { toolbar ->
                launchCatchingTask { toolbar.title = withCol { decks.get(deckId)!!.name } }
            }

        // setup input controls
        setFocusAndOpenKeyboard(deckDescriptionInput) { deckDescriptionInput.setSelection(deckDescriptionInput.text!!.length) }
    }

    override fun onStart() {
        super.onStart()

        dialog!!.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun saveAndExit() =
        launchCatchingTask {
            save()
            Timber.i("closing deck description dialog")
            dismiss()
        }

    private fun onBack() =
        launchCatchingTask {
            if (!hasChanges()) {
                closeWithoutSaving()
                return@launchCatchingTask
            }

            showDiscardChangesDialog()
        }

    private fun checkForChanges() {
        val hasChanges = hasChanges()
        onUnsavedChangesBackCallback.isEnabled = hasChanges
        saveMenuItem.isEnabled = hasChanges
    }

    private fun hasChanges(): Boolean {
        // this can be triggered via the back dispatcher
        if (!::initialDialogState.isInitialized) return false
        return initialDialogState != currentDialogState
    }

    fun closeWithoutSaving() {
        Timber.i("Closing dialog without saving")
        dismiss()
    }

    fun showDiscardChangesDialog() {
        Timber.i("asking if user should discard changes")
        DiscardChangesDialog.showDialog(requireContext()) {
            closeWithoutSaving()
        }
    }

    private suspend fun queryDescriptionState() =
        withCol {
            decks.get(deckId)!!.let {
                DeckDescriptionState(
                    description = it.description,
                    formatAsMarkdown = it.markdownDescription,
                )
            }
        }

    private suspend fun save() {
        Timber.i("updating deck description")
        val toSave = currentDialogState
        withCol {
            decks.update(deckId) {
                this.description = toSave.description
                this.markdownDescription = toSave.formatAsMarkdown
            }
        }
        showSnackbar(R.string.deck_description_saved)
    }

    /**
     * State for [EditDeckDescriptionDialog].
     *
     * Simplifies detecting user changes
     *
     * @param description see [com.ichi2.libanki.Deck.description]
     * @param formatAsMarkdown see [com.ichi2.libanki.Deck.markdownDescription]
     */
    private data class DeckDescriptionState(
        val description: String,
        val formatAsMarkdown: Boolean,
    )

    companion object {
        private const val ARG_DECK_ID = "deckId"

        fun newInstance(deckId: DeckId): EditDeckDescriptionDialog =
            EditDeckDescriptionDialog().apply {
                arguments =
                    bundleOf(
                        ARG_DECK_ID to deckId,
                    )
            }
    }
}
