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
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.utils.ext.update
import com.ichi2.libanki.DeckId
import com.ichi2.utils.AndroidUiUtils.setFocusAndOpenKeyboard
import com.ichi2.utils.show
import timber.log.Timber

/**
 * Allows a user to edit the [deck description][description]
 *
 * This is visible on [StudyOptionsFragment]
 */
class EditDeckDescriptionDialog : DialogFragment(R.layout.dialog_deck_description) {
    private val deckId: DeckId
        get() = requireArguments().getLong(ARG_DECK_ID)

    private val deckDescriptionInput: TextInputEditText
        get() = requireView().findViewById(R.id.deck_description_input)

    private val formatAsMarkdownInput: CheckBox
        get() = requireView().findViewById(R.id.format_as_markdown)

    // state / user inputs

    /** @see [com.ichi2.libanki.Deck.description] */
    private var description: String
        get() = deckDescriptionInput.text.toString()
        set(value) = deckDescriptionInput.setText(value)

    /** @see [com.ichi2.libanki.Deck.markdownDescription] */
    private var formatAsMarkdown: Boolean
        get() = formatAsMarkdownInput.isChecked
        set(value) {
            formatAsMarkdownInput.isChecked = value
        }

    // derived state

    private val currentDialogState: DeckDescriptionState
        get() =
            DeckDescriptionState(
                description = this.description,
                formatAsMarkdown = this.formatAsMarkdown,
            )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also {
            it.setCanceledOnTouchOutside(false)
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // load initial state
        launchCatchingTask {
            val dialog = this@EditDeckDescriptionDialog
            queryDescriptionState().let { data ->
                dialog.description = data.description
                dialog.formatAsMarkdown = data.formatAsMarkdown
            }
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
        view
            .findViewById<MaterialToolbar>(R.id.topAppBar)
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
            fun closeWithoutSaving() {
                Timber.i("Closing dialog without saving")
                dismiss()
            }

            if (queryDescriptionState() == currentDialogState) {
                closeWithoutSaving()
                return@launchCatchingTask
            }

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
