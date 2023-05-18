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

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.R

class ExportCompleteDialog(private val listener: ExportCompleteDialogListener) : AsyncDialogFragment() {
    interface ExportCompleteDialogListener {
        fun dismissAllDialogFragments()
        fun shareFile(path: String) // path of the file to be shared
        fun saveExportFile(exportPath: String)
    }
    private val exportPath
        get() = requireArguments().getString("exportPath")!!

    fun withArguments(exportPath: String): ExportCompleteDialog {
        arguments = (arguments ?: bundleOf(Pair("exportPath", exportPath)))
        return this
    }

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val options = listOf(
            getString(R.string.export_select_save_app),
            getString(R.string.export_select_share_app)
        )
        return MaterialDialog(requireActivity()).show {
            title(text = notificationTitle)
            message(text = notificationMessage)
            listItems(items = options, waitForPositiveButton = false) { _, index, _ ->
                listener.dismissAllDialogFragments()
                when (index) {
                    0 -> listener.saveExportFile(exportPath)
                    1 -> listener.shareFile(exportPath)
                }
            }
            negativeButton(R.string.dialog_cancel) { listener.dismissAllDialogFragments() }
        }
    }

    override val notificationTitle: String
        get() = res().getString(R.string.export_success_title)

    override val notificationMessage: String
        get() = res().getString(R.string.export_success_message)
}
