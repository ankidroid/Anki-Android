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
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.anki.model.CardsOrNotes

class BrowserOptionsDialog(private val cardsOrNotes: CardsOrNotes, private val isTruncated: Boolean) : AppCompatDialogFragment() {
    private lateinit var dialogView: View

    private val positiveButtonClick = { _: DialogInterface, _: Int ->
        @IdRes val selectedButtonId = dialogView.findViewById<RadioGroup>(R.id.select_browser_mode).checkedRadioButtonId
        val newCardsOrNotes = if (selectedButtonId == R.id.select_cards_mode) CardsOrNotes.CARDS else CardsOrNotes.NOTES
        if (cardsOrNotes != newCardsOrNotes) {
            (activity as CardBrowser).switchCardOrNote(newCardsOrNotes)
        }
        val newTruncate = dialogView.findViewById<CheckBox>(R.id.truncate_checkbox).isChecked

        if (newTruncate != isTruncated) {
            (activity as CardBrowser).onTruncate(newTruncate)
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
}
