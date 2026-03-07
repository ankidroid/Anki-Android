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
