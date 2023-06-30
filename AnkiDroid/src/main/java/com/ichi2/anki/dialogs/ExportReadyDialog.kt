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
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.ichi2.anki.R
import com.ichi2.utils.*

class ExportReadyDialog(private val listener: ExportReadyDialogListener) : AsyncDialogFragment() {
    interface ExportReadyDialogListener {
        fun dismissAllDialogFragments()
        fun shareFile(path: String) // path of the file to be shared
        fun saveExportFile(exportPath: String)
    }
    private val exportPath
        get() = requireArguments().getString("exportPath")!!

    fun withArguments(exportPath: String): ExportReadyDialog {
        arguments = (arguments ?: bundleOf(Pair("exportPath", exportPath)))
        return this
    }

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val builder = AlertDialog.Builder(requireActivity())
        builder.show {
            title(text = notificationTitle)
            positiveButton(R.string.export_choice_save_to) { listener.saveExportFile(exportPath) }
            negativeButton(R.string.export_choice_share) { listener.shareFile(exportPath) }
        }
        return builder.create()
    }

    override val notificationTitle: String
        get() = res().getString(R.string.export_ready_title)

    override val notificationMessage: String? = null
}
