/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2></perceptualchaos2>@gmail.com>                          *
 * *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 * *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                           *
 */
package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import java.io.File

@SuppressLint("ConstantFieldName")
class DeckPickerExportCompleteDialog : AsyncDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val exportPath = requireArguments().getString("exportPath")
        val dialogBuilder: MaterialDialog.Builder = activity?.let {
            MaterialDialog.Builder(it)
                .title(notificationTitle)
                .content(notificationMessage)
                .iconAttr(R.attr.dialogSendIcon)
                .positiveText(R.string.export_send_button)
                .negativeText(R.string.export_save_button)
                .onPositive(
                    MaterialDialog.SingleButtonCallback { dialog: MaterialDialog?, which: DialogAction? ->
                        (activity as DeckPicker?)?.dismissAllDialogFragments()
                        (activity as DeckPicker?)?.emailFile(exportPath)
                    }
                )
                .onNegative(
                    MaterialDialog.SingleButtonCallback { dialog: MaterialDialog?, which: DialogAction? ->
                        (activity as DeckPicker?)?.dismissAllDialogFragments()
                        (activity as DeckPicker?)?.saveExportFile(exportPath)
                    }
                )
                .neutralText(R.string.dialog_cancel)
                .onNeutral(MaterialDialog.SingleButtonCallback { dialog: MaterialDialog?, which: DialogAction? -> (activity as DeckPicker?)?.dismissAllDialogFragments() })
        }!!
        return dialogBuilder.show()
    }

    override fun getNotificationTitle(): String {
        return res().getString(R.string.export_successful_title)
    }

    override fun getNotificationMessage(): String {
        val exportPath = File(requireArguments().getString("exportPath"))
        return res().getString(R.string.export_successful, exportPath.name)
    }

    override fun getDialogHandlerMessage(): Message {
        val msg = Message.obtain()
        msg.what = DialogHandler.MSG_SHOW_EXPORT_COMPLETE_DIALOG
        val b = Bundle()
        b.putString("exportPath", requireArguments().getString("exportPath"))
        msg.data = b
        return msg
    }

    companion object {
        fun newInstance(exportPath: String?): DeckPickerExportCompleteDialog {
            val f = DeckPickerExportCompleteDialog()
            val args = Bundle()
            args.putString("exportPath", exportPath)
            f.arguments = args
            return f
        }
    }
}
