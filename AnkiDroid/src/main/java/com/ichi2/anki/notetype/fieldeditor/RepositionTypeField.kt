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
