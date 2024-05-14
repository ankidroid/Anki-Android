/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.instantnoteeditor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CustomActionModeCallback
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.withProgress
import com.ichi2.libanki.NotetypeJson
import com.ichi2.themes.setTransparentBackground
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.jsonObjectIterable
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max

/**
 * Single instance Activity for instantly editing and adding cloze card/s without actually opening the app,
 * uses a custom dialog layout and a transparent activity theme to achieve the functionality.
 **/
class InstantNoteEditorActivity : AnkiActivity(), DeckSelectionDialog.DeckSelectionListener {
    private val viewModel: InstantEditorViewModel by viewModels()

    private var deckSpinnerSelection: DeckSpinnerSelection? = null

    private var dialogView: View? = null

    private var sharedIntentText: IntentSharedText? = null

    private lateinit var singleTapSwitch: MaterialSwitch
    private var editFieldsLayout: LinearLayout? = null
    private lateinit var clozeEditTextField: TextInputEditText
    private lateinit var warningTextField: FixedTextView
    private lateinit var instantAlertDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }
        setTransparentBackground()
        enableEdgeToEdge()

        setContentView(R.layout.activity_instant_note_editor)

        if (Intent.ACTION_SEND == intent.action && intent.type != null && "text/plain" == intent.type) {
            handleSharedText(intent)
        }

        setupErrorListeners()
        prepareEditorDialog()
    }

    private fun prepareEditorDialog() = lifecycleScope.launch {
        Timber.d("Checking for cloze note type")

        viewModel.dialogType.collect { dialogType ->
            dialogType?.let { dialog ->
                when (dialog) {
                    DialogType.NO_CLOZE_NOTE_TYPES_DIALOG -> {
                        Timber.d("Showing no cloze note type dialog")
                        noClozeNoteTypesFoundDialog()
                    }

                    DialogType.SHOW_EDITOR_DIALOG -> {
                        Timber.d("Showing editor dialog")
                        showEditorDialog()
                    }
                }
            }
        }
    }

    /** Setup the deck spinner and custom editor dialog layout **/
    private fun showEditorDialog() {
        showDialog()
        deckSpinnerSelection = DeckSpinnerSelection(
            dialogView!!.context as AppCompatActivity,
            dialogView!!.findViewById(R.id.note_deck_spinner),
            showAllDecks = false,
            alwaysShowDefault = true,
            showFilteredDecks = false
        ).apply {
            initializeNoteEditorDeckSpinner(getColUnsafe)
            launchCatchingTask {
                viewModel.deckId?.let { selectDeckById(it, true) }
            }
        }
    }

    /** Handles the shared text received through an Intent. **/
    private fun handleSharedText(receivedIntent: Intent) {
        val sharedText = receivedIntent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        sharedIntentText = IntentSharedText(sharedText)
    }

    private fun openNoteEditor() {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val noteEditorIntent = Intent(this, NoteEditor::class.java).apply {
            putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.INSTANT_NOTE_EDITOR)
            putExtra(Intent.EXTRA_TEXT, sharedText)
        }
        startActivity(noteEditorIntent)
        finish()
    }

    fun showDialog() {
        Timber.d("Showing Instant Note Editor dialog")
        val dialogView = layoutInflater.inflate(R.layout.instant_editor_dialog, null).also { dv ->
            dialogView = dv
        }
        editFieldsLayout = dialogView.findViewById(R.id.editor_fields_layout)
        singleTapSwitch = dialogView.findViewById(R.id.switch_single_tap_cloze)
        dialogView.findViewById<MaterialButton>(R.id.open_note_editor)?.setOnClickListener {
            openNoteEditor()
        }
        warningTextField = dialogView.findViewById(R.id.warning_text)
        dialogView.findViewById<MaterialButton>(R.id.increment_cloze_button)?.setOnClickListener {
            currentClozeNumber++
            Timber.d("Incrementing cloze number: $currentClozeNumber")
        }

        val editFields = createEditFields(this, viewModel.currentlySelectedNotetype.value)

        Timber.d("Adding edit text fields to the dialog")
        for (editField in editFields) {
            editFieldsLayout?.addView(editField)
        }

        instantAlertDialog = AlertDialog.Builder(this).show {
            setView(dialogView)
            val spinner = dialogView.findViewById<LinearLayout>(R.id.spinner_layout)
            spinner.setOnClickListener {
                launchCatchingTask { deckSpinnerSelection!!.displayDeckSelectionDialog() }
            }
            dialogView.findViewById<MaterialButton>(R.id.action_save_note)?.setOnClickListener {
                Timber.d("Save note button pressed")
                checkAndSave()
            }
            setOnDismissListener {
                finish()
            }
        }
    }

    private fun createEditFields(
        context: Context,
        notetypeJson: NotetypeJson?
    ): List<View> {
        val editLines: MutableList<View> = mutableListOf()

        val clozeFields = viewModel.getClozeFields()
        var clozeFieldsSet = false

        for (i in notetypeJson?.flds!!.jsonObjectIterable()) {
            // Inflate the existing layout
            val inflater = LayoutInflater.from(context)
            val existingLayout = inflater.inflate(R.layout.instant_editor_field_layout, null)

            val textInputLayout =
                existingLayout.findViewById<TextInputLayout>(R.id.edit_text_layout)
            val textInputEditText =
                existingLayout.findViewById<TextInputEditText>(R.id.edit_field_text)

            val name = i.getString("name")
            textInputLayout.hint = name

            Timber.d("Populating the cloze edit text fields")
            // Anki allows multiple cloze fields, we pick the first field
            if (clozeFields.contains(name) && !clozeFieldsSet) {
                setupClozeFields(textInputEditText)
                clozeFieldsSet = true
            }

            editLines.add(existingLayout)
        }
        return editLines
    }

    /** Sets the copied text to the cloze field and enable the single tap gesture for that field**/
    private fun setupClozeFields(textBox: TextInputEditText) {
        clozeEditTextField = textBox
        textBox.setText(sharedIntentText?.sharedTextString)
        val gestureHelper = EditTextGestureHelper(
            textBox,
            EditTextGestureState(singleTapSwitch.isChecked)
        )

        enableErrorMessage()

        setActionModeCallback(textBox)

        singleTapSwitch.setOnCheckedChangeListener { _, check ->
            gestureHelper.toggleGestureState()
            if (check) {
                hideKeyboard()
            }
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(clozeEditTextField.windowToken, 0)
    }

    /** Set the error message to null when the text is changed in the TextInputEditText **/
    private fun enableErrorMessage() {
        clozeEditTextField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No action needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lifecycleScope.launch {
                    viewModel.instantEditorError.emit(null)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // No action needed
            }
        })
    }

    /**
     * Checks if the fields are not empty and contain cloze deletions,
     * retrieves the field content, and saves the note
     */
    private fun checkAndSave() {
        getFieldValues()

        lifecycleScope.launch {
            val result = withProgress(resources.getString(R.string.saving_facts)) {
                viewModel.checkAndSaveNote(this@InstantNoteEditorActivity)
            }
            handleSaveNoteResult(result)
        }
    }

    private fun handleSaveNoteResult(result: SaveNoteResult) {
        when (result) {
            is SaveNoteResult.Failure -> {
                Timber.d("Failed to save note")
                savingErrorDialog(result.getErrorMessage(this))
            }

            SaveNoteResult.Success -> {
                currentClozeNumber = 0
                // Don't show snackbar to avoid blocking parent app
                showThemedToast(this@InstantNoteEditorActivity, TR.addingAdded(), true)
                instantAlertDialog.dismiss()
            }

            is SaveNoteResult.Warning -> {
                Timber.d("Showing warning to the user")
                lifecycleScope.launch { viewModel.instantEditorError.emit(result.message) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentClozeNumber = 0
    }

    /** Gets the field content from the editor **/
    private fun getFieldValues() {
        val editTextValues = mutableListOf<String>()

        editFieldsLayout?.let { layout ->
            for (i in 0 until layout.childCount) {
                val childView = layout.getChildAt(i)

                if (childView is TextInputLayout) {
                    val text = extractTextFromInputField(childView)
                    Timber.d("String values in field are $text")
                    editTextValues.add(text)

                    updateFields(i, childView.findViewById(R.id.edit_field_text))
                }
            }
        }
    }

    private fun extractTextFromInputField(textInputLayout: TextInputLayout): String {
        val textInputEditText =
            textInputLayout.findViewById<TextInputEditText>(R.id.edit_field_text)
        return textInputEditText?.text?.toString() ?: ""
    }

    private fun updateFields(index: Int, field: TextInputEditText?) {
        val fieldContent = field!!.text?.toString() ?: ""
        val correctedFieldContent = NoteService.convertToHtmlNewline(
            fieldContent,
            false
        )

        val note = viewModel.editorNote
        if (note.values()[index] != correctedFieldContent) {
            note.values()[index] = correctedFieldContent
        }
    }

    /** Show a dialog when there is no cloze note type is found, allowing user either to cancel or to open
     * AnkiDroid Note Editor **/
    private fun noClozeNoteTypesFoundDialog() {
        AlertDialog.Builder(this).show {
            title(R.string.cloze_note_required)
            message(R.string.cloze_not_found_message)
            positiveButton(R.string.open) {
                openNoteEditor()
            }
            negativeButton(R.string.dialog_cancel) {
                finish()
            }
        }
    }

    private fun setupErrorListeners() {
        viewModel.onError.flowWithLifecycle(lifecycle).onEach { errorMessage ->
            AlertDialog.Builder(this).setTitle(R.string.vague_error).setMessage(errorMessage)
                .show()
        }.launchIn(lifecycleScope)

        viewModel.instantEditorError.onEach { errorMessage ->
            when (errorMessage) {
                null -> {
                    warningTextField.visibility = View.INVISIBLE
                }

                TR.addingYouHaveAClozeDeletionNote() -> {
                    noClozeDialog(errorMessage)
                }

                else -> {
                    warningTextField.visibility = View.VISIBLE
                    warningTextField.text = errorMessage
                }
            }
        }.launchIn(lifecycleScope)
    }

    /** In case saving the note fails we, want to allow user to cancel and try again, or exist the activity **/
    private fun savingErrorDialog(message: String) {
        AlertDialog.Builder(this).show {
            message(text = message)
            positiveButton(R.string.dialog_cancel) {
                instantAlertDialog.dismiss()
            }
            negativeButton(R.string.try_again)
        }
    }

    /** Warns the user for no cloze in the cloze field, and provide the choice to proceed or
     * to abort save and go back to the editor  **/
    private fun noClozeDialog(errorMessage: String) {
        AlertDialog.Builder(this).show {
            message(text = errorMessage)
            positiveButton(text = TR.actionsSave()) {
                lifecycleScope.launch {
                    val result = withProgress(resources.getString(R.string.saving_facts)) {
                        viewModel.checkAndSaveNote(this@InstantNoteEditorActivity, true)
                    }
                    handleSaveNoteResult(result)
                }
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    override fun onDeckSelected(deck: DeckSelectionDialog.SelectableDeck?) {
        if (deck == null) {
            return
        }
        viewModel.setDeckId(deck.deckId)
        // this is called because DeckSpinnerSelection.onDeckAdded doesn't update the list
        deckSpinnerSelection!!.initializeNoteEditorDeckSpinner(getColUnsafe)
        launchCatchingTask {
            viewModel.deckId?.let { deckSpinnerSelection!!.selectDeckById(it, false) }
        }
    }

    private fun setActionModeCallback(textBox: TextInputEditText) {
        val clozeMenuId = View.generateViewId()
        textBox.customSelectionActionModeCallback = getActionModeCallback(textBox, clozeMenuId)
        textBox.customInsertionActionModeCallback = getActionModeCallback(textBox, clozeMenuId)
        getActionModeCallback(textBox, clozeMenuId)
    }

    private fun getActionModeCallback(
        textBox: TextInputEditText,
        clozeMenuId: Int
    ): ActionMode.Callback {
        return CustomActionModeCallback(
            // we always have cloze type notes here
            isClozeType = true,
            getString(R.string.multimedia_editor_popup_cloze),
            clozeMenuId,
            onActionItemSelected = { mode, item ->
                val itemId = item.itemId
                if (itemId == clozeMenuId) {
                    val selectedText = textBox.text?.substring(
                        textBox.selectionStart,
                        textBox.selectionEnd
                    ) ?: ""
                    convertSelectedTextToCloze(
                        textBox,
                        selectedText,
                        max(currentClozeNumber, 1)
                    )

                    mode.finish()
                    true
                } else {
                    false
                }
            }
        )
    }

    private fun convertSelectedTextToCloze(
        textBox: EditText,
        word: String,
        incrementNumber: Int
    ) {
        val text = textBox.text.toString()
        val selectionStart = textBox.selectionStart

        val start = text.indexOf(word, selectionStart - word.length)
        val end = start + word.length

        if (start != -1 && end != -1) {
            val newText =
                text.substring(0, start) + "{{c$incrementNumber::$word}}" + text.substring(end)

            textBox.setText(newText)
            textBox.setSelection(start + "{{c$incrementNumber::".length)
        }
    }

    /**
     * Enum class that represent the dialog that can be shown when the InstantEditor is initialized
     * **/
    enum class DialogType {
        /** Indicates that no cloze note types were found. **/
        NO_CLOZE_NOTE_TYPES_DIALOG,

        /** Indicates that the editor dialog should be shown. **/
        SHOW_EDITOR_DIALOG
    }

    companion object {
        /** Allows to keep track of the current cloze number, reset to 0 when activity is destroyed **/
        var currentClozeNumber: Int = 0
    }
}

/**
 * Encapsulates the shared text data received through Intent
 **/
data class IntentSharedText(
    val sharedTextString: String
)
