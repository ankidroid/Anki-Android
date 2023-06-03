/****************************************************************************************
 * Copyright (c) 2015 Ryan Annis <squeenix@live.ca>                                     *
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
package com.ichi2.anki

import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.LocaleSelectionDialogHandler
import com.ichi2.anki.dialogs.ModelEditorContextMenu.Companion.newInstance
import com.ichi2.anki.dialogs.ModelEditorContextMenu.ModelEditorContextMenuAction
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.displayKeyboard
import com.ichi2.utils.toStringList
import com.ichi2.widget.WidgetStatus
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.util.*

class ModelFieldEditor : AnkiActivity(), LocaleSelectionDialogHandler {
    // Position of the current field selected
    private var currentPos = 0
    private lateinit var mFieldsListView: ListView
    private var fieldNameInput: EditText? = null
    private lateinit var mModel: Model
    private lateinit var mNoteFields: JSONArray
    private lateinit var mFieldsLabels: List<String>

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.model_field_editor)
        mFieldsListView = findViewById(R.id.note_type_editor_fields)
        enableToolbar().apply {
            setTitle(R.string.model_field_editor_title)
            subtitle = intent.getStringExtra("title")
        }
        startLoadingCollection()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            WidgetStatus.update(this)
            saveCollectionInBackground()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.model_editor, menu)
        return true
    }

    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        initialize()
    }

    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------
    /**
     * Initialize the data holding properties and the UI from the model. This method expects that it
     * isn't followed by other type of work that access the data properties as it has the capability
     * to finish the activity.
     */
    private fun initialize() {
        val noteTypeID = intent.getLongExtra("noteTypeID", 0)
        val collectionModel = col.models.get(col, noteTypeID)
        if (collectionModel == null) {
            showThemedToast(this, R.string.field_editor_model_not_available, true)
            finishWithoutAnimation()
            return
        }
        mModel = collectionModel
        mNoteFields = mModel.getJSONArray("flds")
        mFieldsLabels = mNoteFields.toStringList("name")
        mFieldsListView.adapter = ArrayAdapter(this, R.layout.model_field_editor_list_item, mFieldsLabels)
        mFieldsListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position: Int, _ ->
            showDialogFragment(newInstance(mFieldsLabels[position]))
            currentPos = position
        }
    }
    // ----------------------------------------------------------------------------
    // CONTEXT MENU DIALOGUES
    // ----------------------------------------------------------------------------
    /**
     * Clean the input field or explain why it's rejected
     * @param fieldNameInput Editor to get the input
     * @return The value to use, or null in case of failure
     */
    private fun uniqueName(fieldNameInput: EditText): String? {
        var input = fieldNameInput.text.toString()
            .replace("[\\n\\r{}:\"]".toRegex(), "")
        // The number of #, ^, /, space, tab, starting the input
        var offset = 0
        while (offset < input.length) {
            if (!listOf('#', '^', '/', ' ', '\t').contains(input[offset])) {
                break
            }
            offset++
        }
        input = input.substring(offset).trim { it <= ' ' }
        if (input.isEmpty()) {
            showThemedToast(this, resources.getString(R.string.toast_empty_name), true)
            return null
        }
        if (mFieldsLabels.any { input == it }) {
            showThemedToast(this, resources.getString(R.string.toast_duplicate_field), true)
            return null
        }
        return input
    }

    /*
    * Creates a dialog to create a field
    */
    private fun addFieldDialog() {
        fieldNameInput = FixedEditText(this)
        fieldNameInput?.let { _fieldNameInput ->
            _fieldNameInput.isSingleLine = true
            MaterialDialog(this).show {
                customView(view = _fieldNameInput, horizontalPadding = true)
                title(R.string.model_field_editor_add)
                positiveButton(R.string.dialog_ok) {
                    // Name is valid, now field is added
                    val fieldName = uniqueName(_fieldNameInput)
                    try {
                        addField(fieldName, true)
                    } catch (e: ConfirmModSchemaException) {
                        e.log()

                        // Create dialogue to for schema change
                        val c = ConfirmationDialog()
                        c.setArgs(resources.getString(R.string.full_sync_confirmation))
                        val confirm = Runnable {
                            try {
                                addField(fieldName, false)
                            } catch (e1: ConfirmModSchemaException) {
                                e1.log()
                                // This should never be thrown
                            }
                        }
                        c.setConfirm(confirm)
                        this@ModelFieldEditor.showDialogFragment(c)
                    }
                    col.models.update(col, mModel)
                    initialize()
                }
                negativeButton(R.string.dialog_cancel)
            }
                .displayKeyboard(_fieldNameInput)
        }
    }

    /**
     * Adds a field with the given name
     */
    @Throws(ConfirmModSchemaException::class)
    private fun addField(fieldName: String?, modSchemaCheck: Boolean) {
        fieldName ?: return
        // Name is valid, now field is added
        if (modSchemaCheck) {
            col.modSchema()
        } else {
            col.modSchemaNoCheck()
        }
        launchCatchingTask {
            Timber.d("doInBackgroundAddField")
            withProgress {
                withCol {
                    models.addFieldModChanged(col, mModel, col.models.newField(col, fieldName))
                    save()
                }
            }
            initialize()
        }
    }

    /*
     * Creates a dialog to delete the currently selected field
     */
    private fun deleteFieldDialog() {
        val confirm = Runnable {
            col.modSchemaNoCheck()
            deleteField()

            // This ensures that the context menu closes after the field has been deleted
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        if (mFieldsLabels.size < 2) {
            showThemedToast(this, resources.getString(R.string.toast_last_field), true)
        } else {
            try {
                col.modSchema()
                ConfirmationDialog().let {
                    it.setArgs(resources.getString(R.string.field_delete_warning))
                    it.setConfirm(confirm)
                    showDialogFragment(it)
                }
            } catch (e: ConfirmModSchemaException) {
                e.log()
                ConfirmationDialog().let {
                    it.setConfirm(confirm)
                    it.setArgs(resources.getString(R.string.full_sync_confirmation))
                    showDialogFragment(it)
                }
            }
        }
    }

    private fun deleteField() {
        launchCatchingTask {
            Timber.d("doInBackGroundDeleteField")
            withProgress(message = getString(R.string.model_field_editor_changing)) {
                val result = withCol {
                    try {
                        models.remField(col, mModel, mNoteFields.getJSONObject(currentPos))
                        save()
                        true
                    } catch (e: ConfirmModSchemaException) {
                        // Should never be reached
                        e.log()
                        false
                    }
                }
                if (!result) {
                    closeActivity()
                }
                initialize()
            }
        }
    }

    /*
     * Creates a dialog to rename the currently selected field
     * Processing time is constant
     */
    private fun renameFieldDialog() {
        fieldNameInput = FixedEditText(this)
        fieldNameInput?.let { _fieldNameInput ->
            _fieldNameInput.isSingleLine = true
            _fieldNameInput.setText(mFieldsLabels[currentPos])
            _fieldNameInput.setSelection(_fieldNameInput.text!!.length)
            MaterialDialog(this).show {
                customView(view = _fieldNameInput, horizontalPadding = true)
                title(R.string.model_field_editor_rename)
                positiveButton(R.string.rename) {
                    if (uniqueName(_fieldNameInput) == null) {
                        return@positiveButton
                    }
                    // Field is valid, now rename
                    try {
                        renameField()
                    } catch (e: ConfirmModSchemaException) {
                        e.log()

                        // Handler mod schema confirmation
                        val c = ConfirmationDialog()
                        c.setArgs(resources.getString(R.string.full_sync_confirmation))
                        val confirm = Runnable {
                            col.modSchemaNoCheck()
                            try {
                                renameField()
                            } catch (e1: ConfirmModSchemaException) {
                                e1.log()
                                // This should never be thrown
                            }
                        }
                        c.setConfirm(confirm)
                        this@ModelFieldEditor.showDialogFragment(c)
                    }
                }
                negativeButton(R.string.dialog_cancel)
                displayKeyboard(_fieldNameInput)
            }
        }
    }

    /*
     * Allows the user to select a number less than the number of fields in the current model to
     * reposition the current field to
     * Processing time is scales with number of items
     */
    private fun repositionFieldDialog() {
        fieldNameInput = FixedEditText(this)
        fieldNameInput?.let { _fieldNameInput ->
            _fieldNameInput.setRawInputType(InputType.TYPE_CLASS_NUMBER)
            MaterialDialog(this).show {
                customView(view = _fieldNameInput, scrollable = true)
                title(text = String.format(resources.getString(R.string.model_field_editor_reposition), 1, mFieldsLabels.size))
                positiveButton(R.string.dialog_ok) {
                    val newPosition = _fieldNameInput.text.toString()
                    val pos: Int = try {
                        newPosition.toInt()
                    } catch (n: NumberFormatException) {
                        Timber.w(n)
                        showThemedToast(this@ModelFieldEditor, resources.getString(R.string.toast_out_of_range), true)
                        return@positiveButton
                    }
                    if (pos < 1 || pos > mFieldsLabels.size) {
                        showThemedToast(this@ModelFieldEditor, resources.getString(R.string.toast_out_of_range), true)
                    } else {
                        // Input is valid, now attempt to modify
                        try {
                            col.modSchema()
                            repositionField(pos - 1)
                        } catch (e: ConfirmModSchemaException) {
                            e.log()

                            // Handle mod schema confirmation
                            val c = ConfirmationDialog()
                            c.setArgs(resources.getString(R.string.full_sync_confirmation))
                            val confirm = Runnable {
                                try {
                                    col.modSchemaNoCheck()
                                    repositionField(pos - 1)
                                } catch (e1: JSONException) {
                                    throw RuntimeException(e1)
                                }
                            }
                            c.setConfirm(confirm)
                            this@ModelFieldEditor.showDialogFragment(c)
                        }
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
                .displayKeyboard(_fieldNameInput)
        }
    }

    private fun repositionField(index: Int) {
        launchCatchingTask {
            withProgress(message = getString(R.string.model_field_editor_changing)) {
                val result = withCol {
                    Timber.d("doInBackgroundRepositionField")
                    try {
                        models.moveField(col, mModel, mNoteFields.getJSONObject(currentPos), index)
                        save()
                        true
                    } catch (e: ConfirmModSchemaException) {
                        e.log()
                        // Should never be reached
                        false
                    }
                }
                if (!result) {
                    closeActivity()
                }
                initialize()
            }
        }
    }

    /*
     * Renames the current field
     */
    @Throws(ConfirmModSchemaException::class)
    private fun renameField() {
        val fieldLabel = fieldNameInput!!.text.toString()
            .replace("[\\n\\r]".toRegex(), "")
        val field = mNoteFields.getJSONObject(currentPos)
        col.models.renameField(col, mModel, field, fieldLabel)
        col.models.save(col)
        initialize()
    }

    /*
     * Changes the sort field (that displays in card browser) to the current field
     */
    private fun sortByField() {
        try {
            col.modSchema()
            launchCatchingTask { changeSortField(mModel, currentPos) }
        } catch (e: ConfirmModSchemaException) {
            e.log()
            // Handler mMod schema confirmation
            val c = ConfirmationDialog()
            c.setArgs(resources.getString(R.string.full_sync_confirmation))
            val confirm = Runnable {
                col.modSchemaNoCheck()
                launchCatchingTask { changeSortField(mModel, currentPos) }
            }
            c.setConfirm(confirm)
            this@ModelFieldEditor.showDialogFragment(c)
        }
    }

    private suspend fun changeSortField(model: Model, idx: Int) {
        withProgress(resources.getString(R.string.model_field_editor_changing)) {
            CollectionManager.withCol {
                Timber.d("doInBackgroundChangeSortField")
                models.setSortIdx(col, model, idx)
                save()
            }
        }
        initialize()
    }

    /*
     * Toggle the "Remember last input" setting AKA the "Sticky" setting
     */
    private fun toggleStickyField() {
        // Get the current field
        val field = mNoteFields.getJSONObject(currentPos)
        // If the sticky setting is enabled then disable it, otherwise enable it
        field.put("sticky", !field.getBoolean("sticky"))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        R.id.action_add_new_model -> {
            addFieldDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun closeActivity() {
        finishWithAnimation(ActivityTransitionAnimation.Direction.END)
    }

    override fun onBackPressed() {
        closeActivity()
    }

    fun handleAction(contextMenuAction: ModelEditorContextMenuAction) {
        when (contextMenuAction) {
            ModelEditorContextMenuAction.Sort -> sortByField()
            ModelEditorContextMenuAction.Reposition -> repositionFieldDialog()
            ModelEditorContextMenuAction.Delete -> deleteFieldDialog()
            ModelEditorContextMenuAction.Rename -> renameFieldDialog()
            ModelEditorContextMenuAction.ToggleSticky -> toggleStickyField()
            ModelEditorContextMenuAction.AddLanguageHint -> {
                Timber.i("displaying locale hint dialog")
                // localeHintDialog() is safe to be called here without the check but we can't
                // suppress @RequiresApi just for the method call, we would have to do it on
                // handleAction() which is not ok
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    localeHintDialog()
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun localeHintDialog() {
        // We don't currently show the current value, but we may want to in the future
        val dialogFragment: DialogFragment = LocaleSelectionDialog.newInstance(this)
        showDialogFragment(dialogFragment)
    }

    /*
     * Sets the Locale Hint of the field to the provided value.
     * This allows some keyboard (GBoard) to change language
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun addFieldLocaleHint(selectedLocale: Locale) {
        setLanguageHintForField(col, col.models, mModel, currentPos, selectedLocale)
        val format = getString(R.string.model_field_editor_language_hint_dialog_success_result, selectedLocale.displayName)
        showSnackbar(format, Snackbar.LENGTH_SHORT)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun onSelectedLocale(selectedLocale: Locale) {
        addFieldLocaleHint(selectedLocale)
        dismissAllDialogFragments()
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun onLocaleSelectionCancelled() {
        dismissAllDialogFragments()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Throws(ConfirmModSchemaException::class)
    fun addField(fieldNameInput: EditText) {
        val fieldName = uniqueName(fieldNameInput)
        addField(fieldName, true)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Throws(ConfirmModSchemaException::class)
    fun renameField(fieldNameInput: EditText?) {
        this.fieldNameInput = fieldNameInput
        renameField()
    }
}
