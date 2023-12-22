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
import android.os.Message
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import com.ichi2.anki.DeckPicker
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
        val dialog = AlertDialog.Builder(requireActivity())

        dialog.setTitle(notificationTitle)
            .positiveButton(R.string.export_choice_save_to) { listener.saveExportFile(exportPath) }
            .negativeButton(R.string.export_choice_share) { listener.shareFile(exportPath) }

        return dialog.create()
    }

    override val notificationTitle: String
        get() = res().getString(R.string.export_ready_title)

    override val notificationMessage: String? = null

    override val dialogHandlerMessage: DialogHandlerMessage
        get() = ExportReadyDialogMessage(exportPath)

    /** Export ready dialog message*/
    class ExportReadyDialogMessage(private val exportPath: String) : DialogHandlerMessage(
        which = WhichDialogHandler.MSG_EXPORT_READY,
        analyticName = "ExportReadyDialog",
    ) {
        override fun handleAsyncMessage(deckPicker: DeckPicker) {
            deckPicker.showDialogFragment(
                deckPicker.mExportingDelegate.mDialogsFactory.newExportReadyDialog().withArguments(exportPath),
            )
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                what = this@ExportReadyDialogMessage.what
                data = bundleOf("exportPath" to exportPath)
            }

        companion object {
            fun fromMessage(message: Message): ExportReadyDialogMessage {
                val exportPath = BundleCompat.getParcelable(message.data, "exportPath", String::class.java)!!
                return ExportReadyDialogMessage(exportPath)
            }
        }
    }
}
