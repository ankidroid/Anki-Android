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

import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.sync.userAcceptsSchemaChange
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.customView
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class RenameNoteTypeField(
    private val activity: NoteTypeFieldEditor,
    val fieldName: String,
) {
    fun showRenameNoteTypeFieldDialog(confirm: (name: String) -> Unit) {
        val fieldNameInput =
            FixedEditText(activity).apply {
                focusWithKeyboard()
                isSingleLine = true
                setText(fieldName)
                moveCursorToEnd()
            }

        activity.apply {
            launchCatchingTask {
                val confirmation = userAcceptsSchemaChange()
                if (!confirmation) {
                    return@launchCatchingTask
                }
                AlertDialog
                    .Builder(activity)
                    .show {
                        customView(fieldNameInput, paddingStart = 64, paddingEnd = 64, paddingTop = 32)
                        title(R.string.model_field_editor_rename)
                        positiveButton(R.string.rename) {
                            confirm(fieldNameInput.text.toString())
                        }
                        negativeButton(R.string.dialog_cancel)
                    }
            }
        }
    }
}
