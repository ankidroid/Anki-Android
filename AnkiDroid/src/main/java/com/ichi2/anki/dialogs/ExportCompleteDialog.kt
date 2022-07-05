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
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.utils.iconAttr
import java.io.File

class ExportCompleteDialog(private val listener: ExportCompleteDialogListener) : AsyncDialogFragment() {
    interface ExportCompleteDialogListener {
        fun dismissAllDialogFragments()
        fun emailFile(path: String)
        fun saveExportFile(exportPath: String)
    }

    fun withArguments(exportPath: String): ExportCompleteDialog {
        var args = this.arguments
        if (args == null) {
            args = Bundle()
        }
        args.putString("exportPath", exportPath)
        this.arguments = args
        return this
    }

    @Suppress("Deprecation") // Material dialog neutral button deprecation
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val exportPath = requireArguments().getString("exportPath")!!
        return MaterialDialog(requireActivity()).show {
            title(text = notificationTitle)
            message(text = notificationMessage)
            iconAttr(R.attr.dialogSendIcon)
            positiveButton(R.string.export_send_button) {
                listener.dismissAllDialogFragments()
                listener.emailFile(exportPath)
            }
            negativeButton(R.string.export_save_button) {
                listener.dismissAllDialogFragments()
                listener.saveExportFile(exportPath)
            }
            neutralButton(R.string.dialog_cancel) {
                // TODO: Discuss regarding alternatives to using a neutral button here
                //  since it is deprecated and not recommended in material guidelines

                listener.dismissAllDialogFragments()
            }
        }
    }

    override val notificationTitle: String
        get() {
            return res().getString(R.string.export_successful_title)
        }

    override val notificationMessage: String
        get() {
            val exportPath = File(requireArguments().getString("exportPath")!!)
            return res().getString(R.string.export_successful, exportPath.name)
        }
}
