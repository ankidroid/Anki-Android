package com.ichi2.anki.notetype.fieldeditor

import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.utils.FieldUtil
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class AddNewNoteTypeField(
    private val activity: NoteTypeFieldEditor,
    private val existingNameList: List<String>,
) {
    fun showAddNewNoteTypeFieldDialog(confirm: (String) -> Unit) {
        activity.apply {
            launchCatchingTask {
                AlertDialog
                    .Builder(activity)
                    .show {
                        setView(R.layout.dialog_generic_text_input)
                        title(R.string.model_field_editor_add)
                        positiveButton(R.string.menu_add) {
                            val userInput = (it as AlertDialog).getInputField().text.toString()
                            confirm(userInput)
                        }
                        negativeButton(R.string.dialog_cancel)
                    }.input(
                        hint = getString(R.string.model_field_editor_name),
                        displayKeyboard = true,
                        allowEmpty = true,
                        waitForPositiveButton = false,
                    ) { dialog, char ->
                        val name = char.toString()
                        val result =
                            FieldUtil.uniqueName(
                                nameList = existingNameList,
                                newName = name,
                            )
                        val textInputLayout =
                            dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(
                                R.id.dialog_text_input_layout,
                            )
                        textInputLayout?.apply {
                            when (result) {
                                is FieldUtil.UniqueNameResult.Success -> {
                                    helperText =
                                        if (name != result.name) {
                                            getString(
                                                R.string.model_field_editor_auto_rename,
                                                result.name,
                                            )
                                        } else {
                                            null
                                        }
                                    error = null
                                    isErrorEnabled = false
                                    dialog.positiveButton.isEnabled = true
                                }

                                FieldUtil.UniqueNameResult.Failure.DuplicateName -> {
                                    helperText = null
                                    error = getString(R.string.toast_duplicate_field)
                                    dialog.positiveButton.isEnabled = false
                                }

                                FieldUtil.UniqueNameResult.Failure.EmptyName -> {
                                    // Differs from the rename operation
                                    helperText = null
                                    error = getString(R.string.toast_empty_name)
                                    dialog.positiveButton.isEnabled = false
                                }
                            }
                        }
                    }
            }
        }
    }
}
