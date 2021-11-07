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
import com.ichi2.anki.R
import java.io.File

class ExportCompleteDialog(private val listener: ExportCompleteDialogListener) : AsyncDialogFragment() {
    interface ExportCompleteDialogListener {
        fun dismissAllDialogFragments()
        fun emailFile(path: String?)
        fun saveExportFile(exportPath: String?)
    }

    fun withArguments(exportPath: String?): ExportCompleteDialog {
        var args = this.arguments
        if (args == null) {
            args = Bundle()
        }
        args.putString("exportPath", exportPath)
        this.arguments = args
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val exportPath = requireArguments().getString("exportPath")
        val dialogBuilder = MaterialDialog.Builder(requireActivity())
            .title(notificationTitle)
            .content(notificationMessage)
            .iconAttr(R.attr.dialogSendIcon)
            .positiveText(R.string.export_send_button)
            .negativeText(R.string.export_save_button)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                listener.dismissAllDialogFragments()
                listener.emailFile(exportPath)
            }
            .onNegative { _: MaterialDialog?, _: DialogAction? ->
                listener.dismissAllDialogFragments()
                listener.saveExportFile(exportPath)
            }
            .neutralText(R.string.dialog_cancel)
            .onNeutral { _: MaterialDialog?, _: DialogAction? -> listener.dismissAllDialogFragments() }
        return dialogBuilder.show()
    }

    override fun getNotificationTitle(): String {
        return res().getString(R.string.export_successful_title)
    }

    override fun getNotificationMessage(): String {
        val exportPath = File(requireArguments().getString("exportPath")!!)
        return res().getString(R.string.export_successful, exportPath.name)
    }
}
