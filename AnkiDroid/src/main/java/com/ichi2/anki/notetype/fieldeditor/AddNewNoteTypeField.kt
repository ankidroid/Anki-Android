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
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class AddNewNoteTypeField(
    private val activity: NoteTypeFieldEditor,
) {
    fun showAddNewNoteTypeFieldDialog(confirm: (String) -> Unit) {
        activity.apply {
            launchCatchingTask {
                val confirmation = userAcceptsSchemaChange()
                if (!confirmation) {
                    return@launchCatchingTask
                }
                AlertDialog
                    .Builder(activity)
                    .show {
                        setView(R.layout.dialog_generic_text_input)
                        title(R.string.model_field_editor_add)
                        positiveButton(R.string.menu_add)
                        negativeButton(R.string.dialog_cancel)
                    }.input(
                        displayKeyboard = true,
                    ) { _, text ->
                        confirm(text.toString())
                    }
            }
        }
    }
}
