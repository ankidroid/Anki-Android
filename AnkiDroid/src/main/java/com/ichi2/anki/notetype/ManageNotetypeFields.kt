/****************************************************************************************
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
package com.ichi2.anki.notetype

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import androidx.recyclerview.widget.RecyclerView
import anki.notetypes.Notetype
import anki.notetypes.copy
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.protobuf.ByteString
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.getFieldNames
import com.ichi2.libanki.getNotetype
import com.ichi2.libanki.updateNotetype
import com.ichi2.libanki.utils.set
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.*

// TODO Once the new backend becomes the default delete ModelFieldEditor and its related classes
class ManageNotetypeFields : AnkiActivity(), LocaleSelectionDialog.LocaleSelectionDialogHandler {
    private var notetypeId: Long = -1
    private val fieldsAdapter: NotetypeFieldsAdapter by lazy {
        NotetypeFieldsAdapter(
            this,
            onReposition = ::repositionField,
            onRename = ::renameField,
            onSortBy = ::sortByField,
            onDelete = ::deleteField,
            onToggleSticky = ::toggleStickyForField,
            onAddLanguageHint = ::addLanguageHintForField
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setTitle(R.string.model_field_editor_title)
        setContentView(R.layout.activity_note_types_fields)
        enableToolbar().apply { subtitle = intent.getStringExtra(EXTRA_TITLE) }
        notetypeId = intent.getLongExtra(EXTRA_NOTETYPE_ID, -1)
        findViewById<RecyclerView>(R.id.notetype_fields).apply { adapter = fieldsAdapter }
        findViewById<FloatingActionButton>(R.id.field_add).setOnClickListener { addNewField() }
        launchCatchingTask { updateNotetypeAndRefreshAfter() }
    }

    @SuppressLint("CheckResult")
    private fun repositionField(name: String) {
        launchCatchingTask {
            val allFieldsNames = withProgress {
                withCol { newBackend.getFieldNames(notetypeId) }
            }
            MaterialDialog(this@ManageNotetypeFields).show {
                title(text = TR.fieldsNewPosition_1(allFieldsNames.size))
                input(
                    inputType = InputType.TYPE_CLASS_NUMBER,
                    waitForPositiveButton = false,
                    callback = { dialog, text ->
                        dialog.getActionButton(WhichButton.POSITIVE).isEnabled =
                            text.isNotEmpty() && text.toString().toInt() in 1..allFieldsNames.size
                    }
                )
                positiveButton(R.string.dialog_ok) { dialog ->
                    launchCatchingTask {
                        if (userAcceptsSchemaChange()) {
                            updateNotetypeAndRefreshAfter { notetype ->
                                // the values shown to the user are offset by one
                                val newPosition = dialog.getInputField().text.toString().toInt() - 1
                                val field = notetype.getFieldWith(name)
                                notetype.toBuilder()
                                    .removeFields(field.ord.`val`)
                                    .addFields(newPosition, field)
                                    .build()
                            }
                        }
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun renameField(name: String) {
        val invalidStartChars = listOf("#", "^", "/")
        val invalidChars = listOf(":", "\"", "{", "}")
        launchCatchingTask {
            val allFieldsNames = withProgress {
                withCol { newBackend.getFieldNames(notetypeId) }
            }
            val dialog = MaterialDialog(this@ManageNotetypeFields).show {
                title(R.string.model_field_editor_rename)
                input(
                    prefill = name,
                    waitForPositiveButton = false,
                    callback = { dialog, text ->
                        val errorMessage = when {
                            text.isNotEmpty() && "${text[0]}" in invalidStartChars -> TR.fieldsNameFirstLetterNotValid()
                            text.any { "$it" in invalidChars } -> TR.fieldsNameInvalidLetter()
                            allFieldsNames.contains(text.toString()) -> TR.fieldsThatFieldNameIsAlreadyUsed()
                            else -> null
                        }
                        dialog.getInputField().error = errorMessage
                        dialog.getActionButton(WhichButton.POSITIVE).isEnabled =
                            text.isNotEmpty() && errorMessage == null
                    }
                )
                positiveButton(R.string.dialog_ok) {
                    launchCatchingTask {
                        if (userAcceptsSchemaChange()) {
                            updateNotetypeAndRefreshAfter { notetype ->
                                val newName = it.getInputField().text.toString()
                                val field = notetype.getFieldWith(name)
                                notetype.toBuilder()
                                    .setFields(field.ord.`val`, field.toBuilder().setName(newName))
                                    .build()
                            }
                        }
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
            // start with the ok button disabled as the field name hasn't changed
            dialog.getActionButton(WhichButton.POSITIVE).isEnabled = false
        }
    }

    private fun sortByField(name: String) {
        launchCatchingTask {
            if (userAcceptsSchemaChange()) {
                updateNotetypeAndRefreshAfter { notetype ->
                    notetype.copy {
                        config =
                            config.copy { sortFieldIdx = notetype.getFieldWith(name).ord.`val` }
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun toggleStickyForField(name: String) {
        launchCatchingTask {
            updateNotetypeAndRefreshAfter { notetype ->
                val field = notetype.getFieldWith(name)
                val updatedField = field.copy {
                    config = config.copy { sticky = !sticky }
                }
                notetype.toBuilder().setFields(field.ord.`val`, updatedField).build()
            }
        }
    }

    private fun addLanguageHintForField(name: String) {
        showDialogFragment(LocaleSelectionDialog.newInstance(this, name))
    }

    override fun onSelectedLocale(selectedLocale: Locale) {
        // not used, will be deleted once the new backend becomes the default
    }

    @NeedsTest("Make sure field's 'other' property has the structure expected by language hint related code")
    override fun onSelectedLocale(fieldName: String, selectedLocale: Locale) {
        dismissAllDialogFragments()
        launchCatchingTask {
            updateNotetypeAndRefreshAfter { notetype ->
                val field = notetype.getFieldWith(fieldName)
                val updatedLanguageHint =
                    updateLanguageHint(selectedLocale, field.config.other.toStringUtf8())
                Notetype.newBuilder(notetype).setFields(
                    field.ord.`val`,
                    field.copy {
                        config = config.copy {
                            other = ByteString.copyFrom(
                                updatedLanguageHint.toString(),
                                Charset.forName("UTF-8"),
                            )
                        }
                    }
                ).build()
            }
            val format = getString(
                R.string.model_field_editor_language_hint_dialog_success_result,
                selectedLocale.displayName,
            )
            showSnackbar(format, Snackbar.LENGTH_SHORT)
        }
    }

    private fun updateLanguageHint(locale: Locale, currentInput: String): JSONObject =
        if (currentInput.isEmpty()) {
            JSONObject().apply { put(LANGUAGE_HINT_PROP, locale.toLanguageTag()) }
        } else {
            JSONObject(currentInput).apply { set(LANGUAGE_HINT_PROP, locale.toLanguageTag()) }
        }

    override fun onLocaleSelectionCancelled() {
        dismissAllDialogFragments()
    }

    private fun deleteField(name: String) {
        launchCatchingTask {
            val allFieldsNames = withProgress {
                withCol { newBackend.getFieldNames(notetypeId) }
            }
            if (allFieldsNames.size <= 1) {
                showSnackbar(TR.fieldsNotesRequireAtLeastOneField())
                return@launchCatchingTask
            }
            if (userAcceptsSchemaChange()) {
                MaterialDialog(this@ManageNotetypeFields).show {
                    message(R.string.field_delete_warning)
                    positiveButton(R.string.dialog_ok) {
                        launchCatchingTask {
                            updateNotetypeAndRefreshAfter { notetype ->
                                val field = notetype.getFieldWith(name)
                                notetype.toBuilder().removeFields(field.ord.`val`).build()
                            }
                        }
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun addNewField() {
        launchCatchingTask {
            if (userAcceptsSchemaChange()) {
                val allFieldNames = withProgress {
                    withCol { newBackend.getFieldNames(notetypeId) }
                }
                MaterialDialog(this@ManageNotetypeFields).show {
                    title(R.string.model_field_editor_add)
                    input(
                        waitForPositiveButton = false,
                        callback = { dialog, text ->
                            dialog.getActionButton(WhichButton.POSITIVE).isEnabled =
                                text.isNotEmpty() && !allFieldNames.contains(text.toString())
                        }
                    )
                    positiveButton(R.string.dialog_ok) { dialog ->
                        launchCatchingTask {
                            updateNotetypeAndRefreshAfter { notetype ->
                                val newFieldName = dialog.getInputField().text.toString()
                                notetype.toBuilder()
                                    .addFields(Notetype.Field.newBuilder().setName(newFieldName))
                                    .build()
                            }
                        }
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
        }
    }

    private fun Notetype.getFieldWith(name: String): Notetype.Field =
        fieldsList.first { it.name == name }

    /**
     * Run the specified action on a [com.ichi2.libanki.Collection] to update the current notetype(
     *  which will be provided as a parameter to the action).
     * Note that this method must be called from the main thread.
     *
     * @param action the action to run, can be ignored to simply refresh the list of fields
     * @return the updated notetype to be saved in the backend, or null if no update for the
     * notetype should be done
     */
    private suspend fun updateNotetypeAndRefreshAfter(
        action: Collection.(Notetype) -> Notetype? = { null }
    ) {
        val updatedFieldsNames = withProgress {
            withCol {
                val notetype = newBackend.getNotetype(notetypeId)
                val updatedNotetype = action(notetype)
                updatedNotetype?.let { newBackend.updateNotetype(it) }
                newBackend.getFieldNames(notetypeId)
            }
        }
        fieldsAdapter.submitList(updatedFieldsNames)
    }

    internal companion object {
        const val EXTRA_TITLE = "notetype_fields_extra_title"
        const val EXTRA_NOTETYPE_ID = "notetype_fields_extra_notetype_id"
        const val LANGUAGE_HINT_PROP = "ad-hint-locale"
    }
}
