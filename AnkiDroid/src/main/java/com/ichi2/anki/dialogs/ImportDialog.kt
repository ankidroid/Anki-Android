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
import timber.log.Timber
import java.net.URLDecoder

class ImportDialog : AsyncDialogFragment() {
    interface ImportDialogListener {
        fun importAdd(importPath: List<String>)
        fun importReplace(importPath: List<String>)
        fun dismissAllDialogFragments()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val type = requireArguments().getInt("dialogType")
        val dialog = MaterialDialog(requireActivity())
        dialog.cancelable(true)
        val dialogMessageList = requireArguments().getStringArrayList("dialogMessage")!!
        // Iterate over dialog message list & create display filename
        val displayFileName = dialogMessageList.joinToString("\n") { filenameFromPath(convertToDisplayName(it)) }

        return when (type) {
            DIALOG_IMPORT_ADD_CONFIRM -> {
                dialog.show {
                    title(R.string.import_title)
                    message(text = res().getQuantityString(R.plurals.import_files_message_add_confirm, dialogMessageList.size, displayFileName))
                    positiveButton(R.string.import_message_add) {
                        (activity as ImportDialogListener).importAdd(dialogMessageList)
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_IMPORT_REPLACE_CONFIRM -> {
                dialog.show {
                    title(R.string.import_title)
                    message(text = res().getString(R.string.import_message_replace_confirm, displayFileName))
                    positiveButton(R.string.dialog_positive_replace) {
                        (activity as ImportDialogListener).importReplace(dialogMessageList)
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            else -> null!!
        }
    }

    private fun convertToDisplayName(name: String): String {
        // ImportUtils URLEncodes names, which isn't great for display.
        // NICE_TO_HAVE: Pass in the DisplayFileName closer to the source of the bad data, rather than fixing it here.
        return try {
            URLDecoder.decode(name, "UTF-8")
        } catch (e: Exception) {
            Timber.w(e, "Failed to convert filename to displayable string")
            name
        }
    }

    override val notificationMessage: String
        get() {
            return res().getString(R.string.import_interrupted)
        }

    override val notificationTitle: String
        get() {
            return res().getString(R.string.import_title)
        }

    fun dismissAllDialogFragments() {
        (activity as ImportDialogListener).dismissAllDialogFragments()
    }

    companion object {
        const val DIALOG_IMPORT_ADD_CONFIRM = 2
        const val DIALOG_IMPORT_REPLACE_CONFIRM = 3

        /**
         * A set of dialogs which deal with importing a file
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         * @param dialogMessageList An optional ArrayList of string(s) which can be used to show a custom message
         * or specify import path
         */
        fun newInstance(dialogType: Int, dialogMessageList: ArrayList<String>): ImportDialog {
            val f = ImportDialog()
            val args = Bundle()
            args.putInt("dialogType", dialogType)
            args.putStringArrayList("dialogMessage", dialogMessageList)
            f.arguments = args
            return f
        }

        private fun filenameFromPath(path: String): String {
            return path.split("/").toTypedArray()[path.split("/").toTypedArray().size - 1]
        }
    }
}
