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
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import java.io.File

class ExportCompleteDialog(private val listener: ExportCompleteDialogListener) : AsyncDialogFragment() {
    interface ExportCompleteDialogListener {
        fun dismissAllDialogFragments()
        fun shareFile(path: String) // path of the file to be shared
        fun saveExportFile(exportPath: String)
    }
    val exportPath
        get() = requireArguments().getString("exportPath")!!

    fun withArguments(exportPath: String): ExportCompleteDialog {
        arguments = (arguments ?: bundleOf(Pair("exportPath", exportPath)))
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        return MaterialDialog(requireActivity()).show {
            title(text = notificationTitle)
            message(text = notificationMessage)
            icon(Themes.getResFromAttr(context, R.attr.dialogSendIcon))
            positiveButton(R.string.export_send_button) {
                listener.dismissAllDialogFragments()
                listener.shareFile(exportPath)
            }
            negativeButton(R.string.dialog_cancel) { listener.dismissAllDialogFragments() }
        }
    }

    override val notificationTitle: String
        get() = res().getString(R.string.export_successful_title)

    override val notificationMessage: String
        get() = res().getString(R.string.export_successful, File(exportPath).name)
}
