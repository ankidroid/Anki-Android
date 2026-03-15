/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.notetype

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class RenameCardTemplateDialog {
    companion object {
        fun showInstance(
            context: Context,
            prefill: String,
            existingNames: List<String>,
            block: (result: String) -> Unit,
        ) {
            val dialog =
                AlertDialog
                    .Builder(context)
                    .show {
                        title(R.string.rename_card_type)
                        positiveButton(R.string.rename) { }
                        negativeButton(R.string.dialog_cancel)
                        setView(R.layout.dialog_generic_text_input)
                    }

            val textInputLayout =
                dialog.findViewById<TextInputLayout>(R.id.dialog_text_input_layout)

            val editText =
                dialog.findViewById<TextInputEditText>(R.id.dialog_text_input)

            val renameButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // Set initial hint and prefill
            textInputLayout?.hint = CollectionManager.TR.actionsNewName().removeSuffix(":")
            editText?.setText(prefill)

            // Validation
            editText?.doOnTextChanged { text, _, _, _ ->

                val newName = text.toString().trim()

                if (existingNames.any { it.equals(newName, ignoreCase = true) }) {
                    textInputLayout?.error =
                        context.getString(R.string.card_type_already_exists)

                    renameButton.isEnabled = false
                } else {
                    textInputLayout?.error = null
                    renameButton.isEnabled = true
                }
            }

            renameButton.setOnClickListener {
                val newName = editText?.text?.toString()?.trim() ?: return@setOnClickListener

                block(newName)
                dialog.dismiss()
            }
        }
    }
}
