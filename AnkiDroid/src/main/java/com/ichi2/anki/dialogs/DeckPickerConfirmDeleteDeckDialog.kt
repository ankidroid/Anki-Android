/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.libanki.DeckId
import com.ichi2.utils.BundleUtils.requireLong
import com.ichi2.utils.iconAttr

class DeckPickerConfirmDeleteDeckDialog : AnalyticsDialogFragment() {
    val deckId get() = requireArguments().requireLong("deckId")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        return MaterialDialog(requireActivity()).show {
            title(R.string.delete_deck_title)
            message(text = requireArguments().getString("dialogMessage")!!)
            iconAttr(R.attr.dialogErrorIcon)
            positiveButton(R.string.dialog_positive_delete) {
                (activity as DeckPicker).deleteDeck(deckId)
                (activity as DeckPicker).dismissAllDialogFragments()
            }
            negativeButton(R.string.dialog_cancel) {
                (activity as DeckPicker).dismissAllDialogFragments()
            }
            cancelable(true)
        }
    }

    companion object {
        fun newInstance(dialogMessage: String?, deckId: DeckId): DeckPickerConfirmDeleteDeckDialog {
            val f = DeckPickerConfirmDeleteDeckDialog()
            val args = Bundle()
            args.putString("dialogMessage", dialogMessage)
            args.putLong("deckId", deckId)
            f.arguments = args
            return f
        }
    }
}
