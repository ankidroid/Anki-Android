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

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteFieldsCheckResult
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.R
import com.ichi2.anki.checkNoteFieldsResponse
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity.DialogType
import com.ichi2.anki.utils.ext.getAllClozeTextFields
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Decks
import com.ichi2.libanki.Note
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for managing instant note editing functionality.
 * This ViewModel provides methods for handling note editing operations and
 * managing the state related to instant note editing.
 */
class InstantEditorViewModel : ViewModel(), OnErrorListener {
    override val onError = MutableSharedFlow<String>()

    /** Errors or Warnings related to the edit fields that might occur when trying to save note */
    val instantEditorError = MutableSharedFlow<String?>()

    /**
     * Gets the current editor note.
     */
    @VisibleForTesting
    lateinit var editorNote: Note

    private val _currentlySelectedNotetype = MutableLiveData<NotetypeJson>()

    /**
     * Representing the currently selected note type.
     *
     * @see NotetypeJson
     */
    val currentlySelectedNotetype: LiveData<NotetypeJson> get() = _currentlySelectedNotetype

    var deckId: DeckId? = null

    private val _dialogType = MutableStateFlow<DialogType?>(null)

    /** Representing the type of dialog to be displayed.
     * @see DialogType*/
    val dialogType: StateFlow<DialogType?> get() = _dialogType

    init {
        viewModelScope.launch {
            // setup the deck Id
            withCol { config.get<Long?>(Decks.CURRENT_DECK) ?: 1L }.let { did ->
                deckId = did
            }

            // setup the note type
            // TODO: Use did here
            val noteType = withCol { notetypes.all().firstOrNull { it.isCloze } }
            if (noteType == null) {
                _dialogType.emit(DialogType.NO_CLOZE_NOTE_TYPES_DIALOG)
                return@launch
            }

            @Suppress("RedundantRequireNotNullCall") // postValue lint requires this
            val clozeNoteType = requireNotNull(noteType)
            Timber.d("Changing to cloze type note")
            _currentlySelectedNotetype.postValue(clozeNoteType)
            Timber.i("Using note type '%d", clozeNoteType.id)
            editorNote = withCol { Note.fromNotetypeId(clozeNoteType.id) }

            _dialogType.emit(DialogType.SHOW_EDITOR_DIALOG)
        }
    }

    /** Update the deck id when changed from deck spinner **/
    fun setDeckId(deckId: DeckId) {
        this.deckId = deckId
    }

    /**
     * Checks the note fields and calls [saveNote] if all fields are valid.
     * If [skipClozeCheck] is set to true, the cloze field check is skipped.
     *
     * @param context The context used to retrieve localized error messages.
     * @param skipClozeCheck Indicates whether to skip the cloze field check.
     * @return A [SaveNoteResult] indicating the outcome of the operation.
     */
    suspend fun checkAndSaveNote(
        context: Context,
        skipClozeCheck: Boolean = false
    ): SaveNoteResult {
        if (skipClozeCheck) {
            return saveNote()
        }

        val note = editorNote
        val result = checkNoteFieldsResponse(note)
        if (result is NoteFieldsCheckResult.Failure) {
            val errorMessage = result.getLocalizedMessage(context)
            return SaveNoteResult.Warning(errorMessage)
        }
        Timber.d("Note fields check successful, saving note")
        instantEditorError.emit(null)
        return saveNote()
    }

    /** Adds the note to the collection.
     * @return If the operation is successful, returns [SaveNoteResult.Success],
     * otherwise returns [SaveNoteResult.Failure].
     */
    private suspend fun saveNote(): SaveNoteResult {
        return try {
            editorNote.notetype.put("did", deckId)

            val note = editorNote
            val deckId = deckId ?: return SaveNoteResult.Failure()

            Timber.d("Note and deck id not null, adding note")
            undoableOp { addNote(note, deckId) }

            SaveNoteResult.Success
        } catch (e: Exception) {
            Timber.w(e, "Error saving note")
            SaveNoteResult.Failure()
        }
    }

    fun getClozeFields(): List<String> {
        return editorNote.notetype.getAllClozeTextFields()
    }
}

/**
 * Represents the result of saving a note operation.
 */
sealed class SaveNoteResult {
    data object Success : SaveNoteResult()

    data class Failure(val message: String? = null) : SaveNoteResult() {
        fun getErrorMessage(context: Context) =
            message ?: context.getString(R.string.something_wrong)
    }

    data class Warning(val message: String?) : SaveNoteResult()
}
