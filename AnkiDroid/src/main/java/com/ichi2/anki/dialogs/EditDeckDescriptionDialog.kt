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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.utils.ext.description
import com.ichi2.anki.utils.ext.update
import com.ichi2.libanki.DeckId
import com.ichi2.themes.Themes
import timber.log.Timber

/**
 * Allows a user to edit the [deck description][description]
 *
 * This is visible on [StudyOptionsFragment]
 */
class EditDeckDescriptionDialog : DialogFragment() {
    private val deckId: DeckId
        get() = requireArguments().getLong(ARG_DECK_ID)

    private lateinit var deckDescriptionInput: TextInputEditText

    private var currentDescription
        get() = deckDescriptionInput.text.toString()
        set(value) { deckDescriptionInput.setText(value) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Themes.setTheme(requireContext())
        return inflater.inflate(R.layout.dialog_deck_description, null).apply {
            deckDescriptionInput = this.findViewById(R.id.deck_description_input)
            launchCatchingTask {
                currentDescription = getDescription()
            }
            findViewById<MaterialToolbar>(R.id.topAppBar).apply {
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
        }
    }

    override fun onStart() {
        super.onStart()

        dialog!!.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun saveAndExit() = launchCatchingTask {
        setDescription(currentDescription)
        Timber.i("closing deck description dialog")
        dismiss()
    }

    private fun onBack() = launchCatchingTask {
        fun closeWithoutSaving() {
            Timber.i("Closing dialog without saving")
            dismiss()
        }

        if (getDescription() == currentDescription) {
            closeWithoutSaving()
            return@launchCatchingTask
        }

        Timber.i("asking if user should discard changes")
        DiscardChangesDialog.showDialog(requireContext(), getString(R.string.discard), CollectionManager.TR.addingKeepEditing(), CollectionManager.TR.addingDiscardCurrentInput()) {
            closeWithoutSaving()
        }
    }

    private suspend fun getDescription() = withCol { decks.get(deckId)!!.description }

    private suspend fun setDescription(value: String) {
        Timber.i("updating deck description")
        withCol { decks.update(deckId) { description = value } }
    }

    companion object {
        private const val ARG_DECK_ID = "deckId"

        fun newInstance(deckId: DeckId): EditDeckDescriptionDialog {
            return EditDeckDescriptionDialog().apply {
                arguments = bundleOf(
                    ARG_DECK_ID to deckId
                )
            }
        }
    }
}
