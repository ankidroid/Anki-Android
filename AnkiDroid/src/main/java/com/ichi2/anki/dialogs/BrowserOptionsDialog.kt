/*
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
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
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.model.CardsOrNotes
import timber.log.Timber

class BrowserOptionsDialog : AppCompatDialogFragment() {
    private lateinit var dialogView: View

    private val viewModel: CardBrowserViewModel by activityViewModels()

    private val positiveButtonClick = { _: DialogInterface, _: Int ->
        @IdRes val selectedButtonId =
            dialogView.findViewById<RadioGroup>(R.id.select_browser_mode).checkedRadioButtonId
        val newCardsOrNotes =
            if (selectedButtonId == R.id.select_cards_mode) CardsOrNotes.CARDS else CardsOrNotes.NOTES
        if (cardsOrNotes != newCardsOrNotes) {
            viewModel.setCardsOrNotes(newCardsOrNotes)
        }
        val newTruncate = dialogView.findViewById<CheckBox>(R.id.truncate_checkbox).isChecked

        if (newTruncate != isTruncated) {
            viewModel.setTruncated(newTruncate)
        }
    }

    private val cardsOrNotes by lazy {
        when (arguments?.getBoolean(CARDS_OR_NOTES_KEY)) {
            true -> CardsOrNotes.CARDS
            false -> CardsOrNotes.NOTES
            null -> {
                // Default case, and what we'll do if there were no arguments supplied
                Timber.w("BrowserOptionsDialog instantiated without configuration.")
                CardsOrNotes.CARDS
            }
        }
    }

    private val isTruncated by lazy {
        arguments?.getBoolean(IS_TRUNCATED_KEY) ?: run {
            Timber.w("BrowserOptionsDialog instantiated without configuration.")
            false
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layoutInflater = requireActivity().layoutInflater
        dialogView = layoutInflater.inflate(R.layout.browser_options_dialog, null)

        if (cardsOrNotes == CardsOrNotes.CARDS) {
            dialogView.findViewById<RadioButton>(R.id.select_cards_mode).isChecked = true
        } else {
            dialogView.findViewById<RadioButton>(R.id.select_notes_mode).isChecked = true
        }

        dialogView.findViewById<CheckBox>(R.id.truncate_checkbox).isChecked = isTruncated

        dialogView.findViewById<LinearLayout>(R.id.action_rename_flag).setOnClickListener {
            Timber.d("Rename flag clicked")
            val flagRenameDialog = FlagRenameDialog()
            flagRenameDialog.show(parentFragmentManager, "FlagRenameDialog")
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext()).run {
            this.setView(dialogView)
            this.setTitle(getString(R.string.browser_options_dialog_heading))
            this.setNegativeButton(getString(R.string.dialog_cancel)) { _: DialogInterface, _: Int ->
                dismiss()
            }
            this.setPositiveButton(getString(R.string.dialog_ok), DialogInterface.OnClickListener(function = positiveButtonClick))
            this.create()
        }
    }

    companion object {
        private const val CARDS_OR_NOTES_KEY = "cardsOrNotes"
        private const val IS_TRUNCATED_KEY = "isTruncated"

        fun newInstance(cardsOrNotes: CardsOrNotes, isTruncated: Boolean): BrowserOptionsDialog {
            Timber.i("BrowserOptionsDialog::newInstance")
            return BrowserOptionsDialog().apply {
                arguments = bundleOf(
                    CARDS_OR_NOTES_KEY to (cardsOrNotes == CardsOrNotes.CARDS),
                    IS_TRUNCATED_KEY to isTruncated
                )
            }
        }
    }
}
