/*
 * Copyright (c) 2024 Neel Doshi <neeldoshi147@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype.fieldeditor

import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.sync.userAcceptsSchemaChange
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

class RepositionTypeField(
    private val activity: NoteTypeFieldEditor,
    private val numberOfTemplates: Int,
) {
    fun showRepositionNoteTypeFieldDialog(confirm: (position: Int) -> Unit) {
        activity.apply {
            launchCatchingTask {
                val confirmation = userAcceptsSchemaChange()
                if (!confirmation) {
                    return@launchCatchingTask
                }
                AlertDialog
                    .Builder(activity)
                    .show {
                        positiveButton(R.string.dialog_ok) {
                            val input = (it as AlertDialog).getInputField()
                            confirm(input.text.toString().toInt() - 1)
                        }
                        negativeButton(R.string.dialog_cancel)
                        setMessage(TR.fieldsNewPosition1(numberOfTemplates))
                        setView(R.layout.dialog_generic_text_input)
                    }.input(
                        prefill = (numberOfTemplates + 1).toString(),
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        displayKeyboard = true,
                        waitForPositiveButton = false,
                    ) { dialog, text: CharSequence ->
                        val number = text.toString().toIntOrNull()
                        dialog.positiveButton.isEnabled = number != null && number in 1..(numberOfTemplates + 1)
                    }
            }
        }
    }
}
