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

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class DeckPickerNoSpaceLeftDialog : AnalyticsDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        val builder = AlertDialog.Builder(requireActivity())
            .setTitle(R.string.storage_full_title)
            .setMessage(R.string.backup_deck_no_storage_left)
            .setCancelable(true)
            .setPositiveButton(R.string.dialog_ok) { dialog, which ->
                (activity as DeckPicker).startLoadingCollection()
            }
            .setOnCancelListener { dialog ->
                (activity as DeckPicker).startLoadingCollection()
            }

        return builder.create()
    }

    companion object {
        fun newInstance(): DeckPickerNoSpaceLeftDialog {
            return DeckPickerNoSpaceLeftDialog()
        }
    }
}
