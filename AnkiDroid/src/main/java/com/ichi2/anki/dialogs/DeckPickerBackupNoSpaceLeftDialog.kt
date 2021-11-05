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

import android.os.Bundle
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class DeckPickerBackupNoSpaceLeftDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val res = resources
        val space = BackupManager.getFreeDiscSpace(CollectionHelper.getCollectionPath(activity))
        return MaterialDialog.Builder(requireActivity())
            .title(res.getString(R.string.sd_card_almost_full_title))
            .content(res.getString(R.string.sd_space_warning, space / 1024 / 1024))
            .positiveText(R.string.dialog_ok)
            .onPositive { _: MaterialDialog?, _: DialogAction? -> (activity as DeckPicker?)!!.finishWithoutAnimation() }
            .cancelable(true)
            .cancelListener { (activity as DeckPicker?)!!.finishWithoutAnimation() }
            .show()
    }

    companion object {
        @JvmStatic
        fun newInstance(): DeckPickerBackupNoSpaceLeftDialog {
            return DeckPickerBackupNoSpaceLeftDialog()
        }
    }
}
