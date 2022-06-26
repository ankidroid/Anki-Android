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
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.saveCollectionInBackground
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.LocaleSelectionDialogHandler
import com.ichi2.anki.dialogs.ModelEditorContextMenu.Companion.newInstance
import com.ichi2.anki.dialogs.ModelEditorContextMenu.ModelEditorContextMenuAction
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import com.ichi2.async.CollectionTask.AddField
import com.ichi2.async.CollectionTask.ChangeSortField
import com.ichi2.async.CollectionTask.DeleteField
import com.ichi2.async.CollectionTask.RepositionField
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.themes.StyledProgressDialog.Companion.show
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONException
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.showWithKeyboard
import com.ichi2.widget.WidgetStatus
import timber.log.Timber
import java.lang.NumberFormatException
import java.lang.RuntimeException
import java.util.*
import kotlin.Throws

class ModelFieldEditor : AnkiActivity(), LocaleSelectionDialogHandler {
    // Position of the current field selected
    private var currentPos = 0
    private lateinit var mFieldsListView: ListView
    private var progressDialog: MaterialDialog? = null
    private var fieldNameInput: EditText? = null
    private lateinit var collection: Collection
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
            WidgetStatus.update(this, lifecycleScope)
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
        collection = col
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
        val collectionModel = collection.models.get(noteTypeID)
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
        fieldNameInput?.let {
            it.isSingleLine = true
            MaterialDialog.Builder(this)
                .title(R.string.model_field_editor_add)
                .customView(it, true)
                .positiveText(R.string.dialog_ok)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    // Name is valid, now field is added
                    val listener = changeFieldHandler()
                    val fieldName = uniqueName(it)
                    try {
                        addField(fieldName, listener, true)
                    } catch (e: ConfirmModSchemaException) {
                        e.log()

                        // Create dialogue to for schema change
                        val c = ConfirmationDialog()
                        c.setArgs(resources.getString(R.string.full_sync_confirmation))
                        val confirm = Runnable {
                            try {
                                addField(fieldName, listener, false)
                            } catch (e1: ConfirmModSchemaException) {
                                e1.log()
                                // This should never be thrown
                            }
                        }
                        c.setConfirm(confirm)
                        this@ModelFieldEditor.showDialogFragment(c)
                    }
                    collection.models.update(mModel)
                    initialize()
                }
                .negativeText(R.string.dialog_cancel)
                .showWithKeyboard()
        }
    }

    @Throws(ConfirmModSchemaException::class)
    private fun addField(fieldName: String?, listener: ChangeHandler, modSchemaCheck: Boolean) {
        fieldName ?: return
        // Name is valid, now field is added
        if (modSchemaCheck) {
            collection.modSchema()
        } else {
            collection.modSchemaNoCheck()
        }
        TaskManager.launchCollectionTask(AddField(mModel, fieldName), listener)
    }

    /*
     * Creates a dialog to delete the currently selected field
     */
    private fun deleteFieldDialog() {
        val confirm = Runnable {
            collection.modSchemaNoCheck()
            deleteField()
        }

        if (mFieldsLabels.size < 2) {
            showThemedToast(this, resources.getString(R.string.toast_last_field), true)
        } else {
            try {
                collection.modSchema()
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
        TaskManager.launchCollectionTask(DeleteField(mModel, mNoteFields.getJSONObject(currentPos)), changeFieldHandler())
    }

    /*
     * Creates a dialog to rename the currently selected field
     * Processing time is constant
     */
    private fun renameFieldDialog() {
        fieldNameInput = FixedEditText(this)
        fieldNameInput?.let {
            it.isSingleLine = true
            it.setText(mFieldsLabels[currentPos])
            it.setSelection(it.text!!.length)
            MaterialDialog.Builder(this)
                .title(R.string.model_field_editor_rename)
                .customView(it, true)
                .positiveText(R.string.rename)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    if (uniqueName(it) == null) {
                        return@onPositive
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
                            collection.modSchemaNoCheck()
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
                .negativeText(R.string.dialog_cancel)
                .showWithKeyboard()
        }
    }

    /*
     * Allows the user to select a number less than the number of fields in the current model to
     * reposition the current field to
     * Processing time is scales with number of items
     */
    private fun repositionFieldDialog() {
        fieldNameInput = FixedEditText(this)
        fieldNameInput?.let {
            it.setRawInputType(InputType.TYPE_CLASS_NUMBER)
            MaterialDialog.Builder(this)
                .title(String.format(resources.getString(R.string.model_field_editor_reposition), 1, mFieldsLabels.size))
                .customView(it, true)
                .positiveText(R.string.dialog_ok)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    val newPosition = it.text.toString()
                    val pos: Int = try {
                        newPosition.toInt()
                    } catch (n: NumberFormatException) {
                        Timber.w(n)
                        showThemedToast(this, resources.getString(R.string.toast_out_of_range), true)
                        return@onPositive
                    }
                    if (pos < 1 || pos > mFieldsLabels.size) {
                        showThemedToast(this, resources.getString(R.string.toast_out_of_range), true)
                    } else {
                        val listener = changeFieldHandler()
                        // Input is valid, now attempt to modify
                        try {
                            collection.modSchema()
                            TaskManager.launchCollectionTask(RepositionField(mModel, mNoteFields.getJSONObject(currentPos), pos - 1), listener)
                        } catch (e: ConfirmModSchemaException) {
                            e.log()

                            // Handle mod schema confirmation
                            val c = ConfirmationDialog()
                            c.setArgs(resources.getString(R.string.full_sync_confirmation))
                            val confirm = Runnable {
                                try {
                                    collection.modSchemaNoCheck()
                                    TaskManager.launchCollectionTask(
                                        RepositionField(
                                            mModel,
                                            mNoteFields.getJSONObject(currentPos), pos - 1
                                        ),
                                        listener
                                    )
                                } catch (e1: JSONException) {
                                    throw RuntimeException(e1)
                                }
                            }
                            c.setConfirm(confirm)
                            this@ModelFieldEditor.showDialogFragment(c)
                        }
                    }
                }
                .negativeText(R.string.dialog_cancel)
                .showWithKeyboard()
        }
    }

    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------
    private fun dismissProgressBar() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
        }
        progressDialog = null
    }

    /*
     * Renames the current field
     */
    @Throws(ConfirmModSchemaException::class)
    private fun renameField() {
        val fieldLabel = fieldNameInput!!.text.toString()
            .replace("[\\n\\r]".toRegex(), "")
        val field = mNoteFields.getJSONObject(currentPos)
        collection.models.renameField(mModel, field, fieldLabel)
        collection.models.save()
        initialize()
    }

    /*
     * Changes the sort field (that displays in card browser) to the current field
     */
    private fun sortByField() {
        val listener = changeFieldHandler()
        try {
            collection.modSchema()
            TaskManager.launchCollectionTask(ChangeSortField(mModel, currentPos), listener)
        } catch (e: ConfirmModSchemaException) {
            e.log()
            // Handler mMod schema confirmation
            val c = ConfirmationDialog()
            c.setArgs(resources.getString(R.string.full_sync_confirmation))
            val confirm = Runnable {
                collection.modSchemaNoCheck()
                TaskManager.launchCollectionTask(ChangeSortField(mModel, currentPos), listener)
            }
            c.setConfirm(confirm)
            this@ModelFieldEditor.showDialogFragment(c)
        }
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

    // ----------------------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------------------
    /*
     * Called during the desk task when any field is modified
     */
    private fun changeFieldHandler(): ChangeHandler {
        return ChangeHandler(this)
    }

    private class ChangeHandler(modelFieldEditor: ModelFieldEditor) : TaskListenerWithContext<ModelFieldEditor, Void?, Boolean?>(modelFieldEditor) {
        override fun actualOnPreExecute(context: ModelFieldEditor) {
            if (context.progressDialog == null) {
                context.progressDialog = show(
                    context, context.intent.getStringExtra("title"),
                    context.resources.getString(R.string.model_field_editor_changing), false
                )
            }
        }

        @KotlinCleanup("Convert result to non-null")
        override fun actualOnPostExecute(context: ModelFieldEditor, result: Boolean?) {
            if (result == false) {
                context.closeActivity()
            }
            context.dismissProgressBar()
            context.initialize()
        }
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
        supportFragmentManager.popBackStackImmediate()
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
        setLanguageHintForField(col.models, mModel, currentPos, selectedLocale)
        val format = getString(R.string.model_field_editor_language_hint_dialog_success_result, selectedLocale.displayName)
        showSimpleSnackbar(this, format, true)
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
        addField(fieldName, ChangeHandler(this), true)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Throws(ConfirmModSchemaException::class)
    fun renameField(fieldNameInput: EditText?) {
        this.fieldNameInput = fieldNameInput
        renameField()
    }
}
