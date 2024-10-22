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

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteFieldsCheckResult
import com.ichi2.anki.OnErrorListener
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max

/**
 * ViewModel for managing instant note editing functionality.
 * This ViewModel provides methods for handling note editing operations and
 * managing the state related to instant note editing.
 */
class InstantEditorViewModel : ViewModel(), OnErrorListener {
    override val onError = MutableSharedFlow<String>()

    /** Errors or Warnings related to the edit fields that might occur when trying to save note */
    val instantEditorError = MutableSharedFlow<String?>()

    private val _currentClozeNumber = MutableStateFlow(1)

    val currentClozeNumber: Int
        get() = _currentClozeNumber.value

    // List to store the cloze integers
    private val intClozeList = mutableListOf<Int>()

    /** The number of words which are marked as cloze deletions */
    @VisibleForTesting
    val clozeDeletionCount get() = intClozeList.size

    private val _currentClozeMode = MutableStateFlow(InstantNoteEditorActivity.ClozeMode.INCREMENT)

    val currentClozeMode: StateFlow<InstantNoteEditorActivity.ClozeMode> = _currentClozeMode.asStateFlow()

    private val _actualClozeFieldText = MutableStateFlow<String?>(null)

    val actualClozeFieldText: StateFlow<String?> = _actualClozeFieldText.asStateFlow()

    private val _editorMode = MutableStateFlow(InstantNoteEditorActivity.EditMode.SINGLE_TAP)

    val editorMode: StateFlow<InstantNoteEditorActivity.EditMode> = _editorMode.asStateFlow()

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
            editorNote = withCol { Note.fromNotetypeId(this@withCol, clozeNoteType.id) }

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
     * @param skipClozeCheck Indicates whether to skip the cloze field check.
     * @return A [SaveNoteResult] indicating the outcome of the operation.
     */
    suspend fun checkAndSaveNote(
        skipClozeCheck: Boolean = false
    ): SaveNoteResult {
        if (skipClozeCheck) {
            return saveNote()
        }

        val note = editorNote
        val result = checkNoteFieldsResponse(note)
        if (result is NoteFieldsCheckResult.Failure) {
            return SaveNoteResult.Warning(result.localizedMessage)
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

    private fun shouldResetClozeNumber(number: Int) {
        intClozeList.remove(number)

        // Reset cloze number if the list is empty
        if (intClozeList.isEmpty()) {
            _currentClozeNumber.value = 1
        } else {
            // not null for sure
            _currentClozeNumber.value = intClozeList.maxOrNull()!! + 1
        }
    }

    /**
     * Retrieves all cloze text fields from the current editor note's note type.
     *
     * This method accesses the `editorNote` property to fetch its associated note type
     * and then retrieves all cloze text fields using the [getAllClozeTextFields] method.
     *
     * @return A list of strings representing the cloze text fields in the current editor note's note type.
     */
    fun getClozeFields(): List<String> {
        return editorNote.notetype.getAllClozeTextFields()
    }

    /**
     * Set the warning message to be displayed in editor dialog
     */
    fun setWarningMessage(message: String?) {
        viewModelScope.launch {
            instantEditorError.emit(message)
        }
    }

    private fun incrementClozeNumber() {
        Timber.d("Incrementing cloze number: $currentClozeNumber")
        _currentClozeNumber.value++
    }

    fun setClozeFieldText(text: String?) {
        _actualClozeFieldText.value = text
    }

    /**
     * Creates or removes the cloze deletion for a word around the given offset.
     *
     * This method first checks if the provided text already contains a cloze deletion.
     * If it does, it returns the clean text. If not, it creates a new cloze
     * deletion for the text, optionally including punctuation if present at the end of the text.
     *
     * The method also handles incrementing the cloze number based on the current cloze mode.
     *
     * @param text the text to be converted into a cloze deletion
     * @return the cloze-deleted version of the input text or clean text if already cloze
     */
    fun buildClozeText(text: String): String {
        val cloze = processClozeUndo(text)
        if (cloze != null) {
            Timber.d("Text contains cloze, removed cloze")
            return cloze
        }

        Timber.d("Text doesn't have cloze, selecting and adding cloze")

        val matcher = clozeBuilderPattern.findAll(text).firstOrNull()

        val clozeText: String?

        val clozeNumber = currentClozeNumber
        if (currentClozeMode.value == InstantNoteEditorActivity.ClozeMode.INCREMENT) {
            incrementClozeNumber()
        }
        intClozeList.add(clozeNumber)

        // Extract the first, second, and third regex groups from the matcher
        val punctuationAtStart: String? = matcher?.groups?.get(1)?.value
        val capturedWord: String? = matcher?.groups?.get(2)?.value
        val punctuationAtEnd: String? = matcher?.groups?.get(4)?.value

        clozeText = "$punctuationAtStart{{c$clozeNumber::$capturedWord}}$punctuationAtEnd"

        return clozeText
    }

    /**
     * This method extracts the cloze number (if present) from a given word
     * that indicates a cloze deletion (a blank replaced with a number).
     * If the pattern matches, it extracts the number from the captured group
     * and converts it to an integer, otherwise it returns null.
     *
     * Example: word is {{c`number`::`text`}} then it extracts `number`
     *
     * @param word The word to be analyzed for a cloze number.
     * @return The extracted cloze number as an integer if found, otherwise null.
     */
    fun getWordClozeNumber(word: String): Int? {
        val matcher = clozePattern.find(word)
        return matcher?.groups?.get(2)?.value?.toIntOrNull()
    }

    fun getWordsFromFieldText(): List<String> {
        val sentence = actualClozeFieldText.value ?: ""

        val words = mutableListOf<String>()
        var lastIndex = 0

        clozePattern.findAll(sentence).forEach { matchResult ->
            val cloze = matchResult.value
            // Add any words between the last match and this match
            val inBetween = sentence.substring(lastIndex, matchResult.range.first)
            words.addAll(inBetween.trim().split(spaceRegex).filter { it.isNotEmpty() })
            words.add(cloze)
            lastIndex = matchResult.range.last + 1
        }

        // Add any remaining words after the last cloze
        if (lastIndex < sentence.length) {
            val remaining = sentence.substring(lastIndex).trim()
            words.addAll(remaining.split(spaceRegex).filter { it.isNotEmpty() })
        }

        return combineWordsWithPunctuation(words)
    }

    private fun combineWordsWithPunctuation(words: List<String>): List<String> {
        val combinedWords = mutableListOf<String>()

        var clozeEncountered = false

        words.forEachIndexed { _, word ->
            if (punctuationPattern.matches(word) && clozeEncountered) {
                // Combine punctuation with the previous cloze
                combinedWords[combinedWords.size - 1] += word
            } else {
                clozeEncountered = clozePattern.matches(word)
                combinedWords.add(word)
            }
        }

        return combinedWords
    }

    fun updateClozeNumber(word: String, newClozeNumber: Int): String {
        return clozePattern.replace(word) { matchResult ->
            val punctutationAtStart = matchResult.groupValues[1]
            val content = matchResult.groupValues[3]
            val punctutationAtEnd = matchResult.groupValues[4]
            "$punctutationAtStart{{c$newClozeNumber::$content}}$punctutationAtEnd"
        }
    }

    /**
     * Removes the cloze deletion marker and surrounding delimiters from a word.
     *
     * @param word The word having a potential cloze deletion.
     * @return The cleaned word with the cloze deletion marker and delimiters removed,
     * or the original word if no match is found.
     */
    fun getCleanClozeWords(word: String): String {
        val regex = clozePattern
        return regex.replace(word) { matchResult ->
            (matchResult.groups[1]?.value ?: "") + (matchResult.groups[3]?.value ?: "") + (matchResult.groups[4]?.value ?: "")
        }
    }

    /**
     * Processes a cloze-deleted word and removes cloze if any, if a user tap the words twice then
     * it will detect the cloze and remove it and revert the text to its original state.
     *
     * @param text The text to be analyzed for cloze deletion.
     * @return The processed text with the cloze deletion marker and delimiters removed
     *         (if applicable), the original text if no match is found, or null
     *         if an undo is confirmed.
     */
    private fun processClozeUndo(text: String): String? {
        val matchResult = clozePattern.find(text)
        val capturedClozeNumber = matchResult?.groups?.get(2)?.value
        if (capturedClozeNumber != null && currentClozeNumber - capturedClozeNumber.toInt() == 1) {
            decrementClozeNumber()
        }

        if (matchResult == null) {
            Timber.d("No match found for the input text")
            return null
        }

        matchResult.groups[2]?.value?.toInt()?.let { shouldResetClozeNumber(it) }

        val punctuationAtStart: String? = matchResult?.groups?.get(1)?.value ?: ""
        val capturedWord: String? = matchResult?.groups?.get(3)?.value ?: ""
        val punctuationAtEnd: String? = matchResult?.groups?.get(4)?.value ?: ""

        return punctuationAtStart + capturedWord + punctuationAtEnd
    }

    fun setEditorMode(mode: InstantNoteEditorActivity.EditMode) {
        _editorMode.value = mode
    }

    private fun decrementClozeNumber() {
        val newValue = _currentClozeNumber.value - 1
        _currentClozeNumber.value = max(1, newValue)
    }

    fun toggleClozeMode() {
        val newMode = when (_currentClozeMode.value) {
            InstantNoteEditorActivity.ClozeMode.INCREMENT -> {
                decrementClozeNumber()
                InstantNoteEditorActivity.ClozeMode.NO_INCREMENT
            }
            InstantNoteEditorActivity.ClozeMode.NO_INCREMENT -> {
                incrementClozeNumber()
                InstantNoteEditorActivity.ClozeMode.INCREMENT
            }
        }
        _currentClozeMode.value = newMode
    }
}

/**
 * Represents the result of saving a note operation.
 * Has three possible outcomes: `Success`, `Failure`, and `Warning`.
 */
sealed class SaveNoteResult {
    /**
     * Indicates that the save note operation was successful.
     */
    data object Success : SaveNoteResult()

    /**
     * Indicates that the save note operation failed.
     *
     * @property message An optional message describing the reason for the failure.
     */
    data class Failure(val message: String? = null) : SaveNoteResult()

    /**
     * Indicates that the save note operation completed with a warning.
     *
     * Example, when user tries to save cloze field with no cloze
     *
     * @property message A message describing the warning.
     */
    data class Warning(val message: String?) : SaveNoteResult()
}

/**
 * A compiled regular expression pattern used to match cloze deletions within a string.
 *
 * This pattern is designed to identify text formatted as cloze deletions, which are commonly
 * used in educational materials. The pattern follows the format:
 * {{c`number`::`content`}} (optional punctuation)
 */
val clozePattern = Regex("""(\p{Punct}+)?\{\{c(\d+)::([^}]+?)\}\}(\p{Punct}+)?""")

private val punctuationPattern = Regex("""\p{Punct}+$""")

/** Used when splitting words **/
private val spaceRegex = Regex("\\s+")

/** Used to build cloze text here word is not null **/
private val clozeBuilderPattern = "(\\p{Punct}*)((?:\\w|\\p{Pd}|\\p{Pc}|'|(\\(\\w+\\)))+)(\\p{Punct}*)".toRegex()
