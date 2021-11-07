/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.BackupManager
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import java.io.File

class DeckPickerNoSpaceToDowngradeDialog(private val formatter: FileSizeFormatter, private val collection: File?) : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val res = resources
        return MaterialDialog.Builder(requireActivity())
            .title(res.getString(R.string.no_space_to_downgrade_title))
            .content(res.getString(R.string.no_space_to_downgrade_content, requiredSpaceString))
            .cancelable(false)
            .positiveText(R.string.close)
            .onPositive { _: MaterialDialog?, _: DialogAction? -> (activity as DeckPicker?)!!.exit() }
            .show()
    }

    private val requiredSpaceString: String
        get() = formatter.formatFileSize(freeSpaceRequired)
    private val freeSpaceRequired: Long
        get() = BackupManager.getRequiredFreeSpace(collection)

    class FileSizeFormatter(private val context: Context) {
        fun formatFileSize(sizeInBytes: Long): String {
            return Formatter.formatShortFileSize(context, sizeInBytes)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(formatter: FileSizeFormatter, collectionFile: File?): DeckPickerNoSpaceToDowngradeDialog {
            return DeckPickerNoSpaceToDowngradeDialog(formatter, collectionFile)
        }
    }
}
