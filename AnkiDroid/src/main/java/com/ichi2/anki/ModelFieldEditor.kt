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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.ListCallback
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.saveCollectionInBackground
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.LocaleSelectionDialogHandler
import com.ichi2.anki.dialogs.ModelEditorContextMenu
import com.ichi2.anki.dialogs.ModelEditorContextMenu.Companion.newInstance
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
import com.ichi2.widget.WidgetStatus
import timber.log.Timber
import java.lang.NumberFormatException
import java.lang.RuntimeException
import java.util.*
import kotlin.Throws

@KotlinCleanup("long-term: make `mod` non-null")
class ModelFieldEditor : AnkiActivity(), LocaleSelectionDialogHandler {
    // Position of the current field selected
    private var currentPos = 0
    private var fieldLabelView: ListView? = null
    private var fieldLabels: List<String>? = null
    private var progressDialog: MaterialDialog? = null
    private var collection: Collection? = null
    private var noteFields: JSONArray? = null
    private var mod: Model? = null
    private var contextMenu: ModelEditorContextMenu? = null
    private var fieldNameInput: EditText? = null
    private val confirmDialogCancel = Runnable { dismissContextMenu() }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.model_field_editor)

        fieldLabelView = findViewById(R.id.note_type_editor_fields)
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
        collection = col
        setupLabels()
        createFieldLabels()
    }

    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------
    /*
     * Sets up the main ListView and ArrayAdapters
     * Containing clickable labels for the fields
     */
    private fun createFieldLabels() {
        val fieldLabelAdapter = ArrayAdapter(this, R.layout.model_field_editor_list_item, fieldLabels!!)
        fieldLabelView?.let {
            it.adapter = fieldLabelAdapter
            it.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                contextMenu = newInstance(fieldLabels!![position], mContextMenuListener)
                showDialogFragment(contextMenu)
                currentPos = position
            }
        }
    }

    /*
      * Sets up the ArrayList containing the text for the main ListView
      */
    private fun setupLabels() {
        val noteTypeID = intent.getLongExtra("noteTypeID", 0)
        mod = collection!!.models.get(noteTypeID)

        noteFields = mod!!.getJSONArray("flds")
        fieldLabels = noteFields!!.toStringList("name")
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
        if (containsField(input)) {
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
            MaterialEditTextDialog.Builder(this, it)
                .title(R.string.model_field_editor_add)
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
                            dismissContextMenu()
                        }
                        c.setConfirm(confirm)
                        c.setCancel(confirmDialogCancel)
                        this@ModelFieldEditor.showDialogFragment(c)
                    }
                    collection!!.models.update(mod!!)
                    fullRefreshList()
                }
                .negativeText(R.string.dialog_cancel)
                .show()
        }
    }

    @Throws(ConfirmModSchemaException::class)
    @KotlinCleanup("Check if we can make fieldName non-null")
    private fun addField(fieldName: String?, listener: ChangeHandler, modSchemaCheck: Boolean) {
        if (fieldName == null) {
            return
        }
        // Name is valid, now field is added
        if (modSchemaCheck) {
            collection!!.modSchema()
        } else {
            collection!!.modSchemaNoCheck()
        }
        TaskManager.launchCollectionTask(AddField(mod!!, fieldName), listener)
    }

    /*
     * Creates a dialog to delete the currently selected field
     */
    private fun deleteFieldDialog() {
        val confirm = Runnable {
            collection!!.modSchemaNoCheck()
            deleteField()
            dismissContextMenu()
        }

        if (fieldLabels!!.size < 2) {
            showThemedToast(this, resources.getString(R.string.toast_last_field), true)
        } else {
            try {
                collection!!.modSchema()
                ConfirmationDialog().let {
                    it.setArgs(resources.getString(R.string.field_delete_warning))
                    it.setConfirm(confirm)
                    it.setCancel(confirmDialogCancel)
                    showDialogFragment(it)
                }
            } catch (e: ConfirmModSchemaException) {
                e.log()
                ConfirmationDialog().let {
                    it.setConfirm(confirm)
                    it.setCancel(confirmDialogCancel)
                    it.setArgs(resources.getString(R.string.full_sync_confirmation))
                    showDialogFragment(it)
                }
            }
        }
    }

    private fun deleteField() {
        TaskManager.launchCollectionTask(DeleteField(mod!!, noteFields!!.getJSONObject(currentPos)), changeFieldHandler())
    }

    /*
     * Creates a dialog to rename the currently selected field
     * Processing time is constant
     */
    private fun renameFieldDialog() {
        fieldNameInput = FixedEditText(this)
        fieldNameInput?.let {
            it.isSingleLine = true
            it.setText(fieldLabels!![currentPos])
            it.setSelection(it.text!!.length)
            MaterialEditTextDialog.Builder(this, fieldNameInput)
                .title(R.string.model_field_editor_rename)
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
                            collection!!.modSchemaNoCheck()
                            try {
                                renameField()
                            } catch (e1: ConfirmModSchemaException) {
                                e1.log()
                                // This should never be thrown
                            }
                            dismissContextMenu()
                        }
                        c.setConfirm(confirm)
                        c.setCancel(confirmDialogCancel)
                        this@ModelFieldEditor.showDialogFragment(c)
                    }
                }
                .negativeText(R.string.dialog_cancel)
                .show()
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
            MaterialEditTextDialog.Builder(this, it)
                .title(String.format(resources.getString(R.string.model_field_editor_reposition), 1, fieldLabels!!.size))
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
                    if (pos < 1 || pos > fieldLabels!!.size) {
                        showThemedToast(this, resources.getString(R.string.toast_out_of_range), true)
                    } else {
                        val listener = changeFieldHandler()
                        // Input is valid, now attempt to modify
                        try {
                            collection!!.modSchema()
                            TaskManager.launchCollectionTask(RepositionField(mod!!, noteFields!!.getJSONObject(currentPos), pos - 1), listener)
                        } catch (e: ConfirmModSchemaException) {
                            e.log()

                            // Handle mod schema confirmation
                            val c = ConfirmationDialog()
                            c.setArgs(resources.getString(R.string.full_sync_confirmation))
                            val confirm = Runnable {
                                try {
                                    collection!!.modSchemaNoCheck()
                                    TaskManager.launchCollectionTask(
                                        RepositionField(
                                            mod!!,
                                            noteFields!!.getJSONObject(currentPos), pos - 1
                                        ),
                                        listener
                                    )
                                    dismissContextMenu()
                                } catch (e1: JSONException) {
                                    throw RuntimeException(e1)
                                }
                            }
                            c.setConfirm(confirm)
                            c.setCancel(confirmDialogCancel)
                            this@ModelFieldEditor.showDialogFragment(c)
                        }
                    }
                }
                .negativeText(R.string.dialog_cancel)
                .show()
        }
    }

    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------
    /*
     * Useful when a confirmation dialog is created within another dialog
     */
    private fun dismissContextMenu() {
        if (contextMenu != null) {
            contextMenu!!.dismiss()
            contextMenu = null
        }
    }

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
        val field = noteFields!!.getJSONObject(currentPos)
        collection!!.models.renameField(mod!!, field, fieldLabel)
        collection!!.models.save()
        fullRefreshList()
    }

    /*
     * Changes the sort field (that displays in card browser) to the current field
     */
    private fun sortByField() {
        val listener = changeFieldHandler()
        try {
            collection!!.modSchema()
            TaskManager.launchCollectionTask(ChangeSortField(mod!!, currentPos), listener)
        } catch (e: ConfirmModSchemaException) {
            e.log()
            // Handler mMod schema confirmation
            val c = ConfirmationDialog()
            c.setArgs(resources.getString(R.string.full_sync_confirmation))
            val confirm = Runnable {
                collection!!.modSchemaNoCheck()
                TaskManager.launchCollectionTask(ChangeSortField(mod!!, currentPos), listener)
                dismissContextMenu()
            }
            c.setConfirm(confirm)
            c.setCancel(confirmDialogCancel)
            this@ModelFieldEditor.showDialogFragment(c)
        }
    }

    /*
     * Toggle the "Remember last input" setting AKA the "Sticky" setting
     */
    private fun toggleStickyField() {
        // Get the current field
        val field = noteFields!!.getJSONObject(currentPos)
        // If the sticky setting is enabled then disable it, otherwise enable it
        field.put("sticky", !field.getBoolean("sticky"))
    }

    /*
     * Reloads everything
     */
    private fun fullRefreshList() {
        setupLabels()
        createFieldLabels()
    }

    /*
     * Checks if there exists a field with this name in the current model
     */
    @KotlinCleanup("Stream/extension function")
    private fun containsField(field: String): Boolean {
        for (s in fieldLabels!!) {
            if (field.compareTo(s) == 0) {
                return true
            }
        }
        return false
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

    private class ChangeHandler(modelFieldEditor: ModelFieldEditor?) : TaskListenerWithContext<ModelFieldEditor?, Void?, Boolean?>(modelFieldEditor) {
        override fun actualOnPreExecute(context: ModelFieldEditor?) {
            if (context != null && context.progressDialog == null) {
                context.progressDialog = show(
                    context, context.intent.getStringExtra("title"),
                    context.resources.getString(R.string.model_field_editor_changing), false
                )
            }
        }

        @KotlinCleanup("Convert result to non-null")
        override fun actualOnPostExecute(context: ModelFieldEditor?, result: Boolean?) {
            if (result == false) {
                context?.closeActivity()
            }
            context?.dismissProgressBar()
            context?.fullRefreshList()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (itemId == R.id.action_add_new_model) {
            addFieldDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun closeActivity() {
        finishWithAnimation(ActivityTransitionAnimation.Direction.END)
    }

    override fun onBackPressed() {
        closeActivity()
    }

    @KotlinCleanup("Add @RequiresApi instead of using check in if condition")
    private val mContextMenuListener = ListCallback { _: MaterialDialog?, _: View?, selection: Int, _: CharSequence? ->
        when (selection) {
            ModelEditorContextMenu.SORT_FIELD -> sortByField()
            ModelEditorContextMenu.FIELD_REPOSITION -> repositionFieldDialog()
            ModelEditorContextMenu.FIELD_DELETE -> deleteFieldDialog()
            ModelEditorContextMenu.FIELD_RENAME -> renameFieldDialog()
            ModelEditorContextMenu.FIELD_TOGGLE_STICKY -> toggleStickyField()
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (selection == ModelEditorContextMenu.FIELD_ADD_LANGUAGE_HINT) {
                        Timber.i("displaying locale hint dialog")
                        localeHintDialog()
                    }
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
        setLanguageHintForField(col.models, mod!!, currentPos, selectedLocale)
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

    companion object {
        private const val NORMAL_EXIT = 100001
    }
}
