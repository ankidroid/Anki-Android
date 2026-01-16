/*
 * Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.NoteTypeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class CardTemplateEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow<CardTemplateEditorState>(CardTemplateEditorState.Loading)
    val state: StateFlow<CardTemplateEditorState> = _state.asStateFlow()

    /**
     * Loads the notetype from the collection and transitions to Loaded state.
     */
    fun loadNotetype(noteTypeId: NoteTypeId) {
        Timber.d("Loading notetype with id: $noteTypeId")
        viewModelScope.launch {
            try {
                val notetype =
                    withCol {
                        notetypes.clearCache()
                        notetypes.get(noteTypeId)!!.deepClone()
                    }
                val tempNotetype = CardTemplateNotetype(notetype)
                _state.value = CardTemplateEditorState.Loaded(tempNotetype = tempNotetype)
                Timber.d("Notetype loaded successfully: ${notetype.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load notetype")
                _state.value =
                    CardTemplateEditorState.Error(
                        CardTemplateEditorState.ReportableException(e),
                    )
            }
        }
    }

    /**
     * Transitions to the Finished state.
     * Called internally when the activity should close.
     */
    internal fun onFinish() {
        _state.value = CardTemplateEditorState.Finished
    }

    /**
     * Updates the current template ordinal.
     * Only valid when in Loaded state.
     */
    fun setCurrentTemplateOrd(ord: CardOrdinal) {
        updateLoadedState { it.copy(currentTemplateOrd = ord) }
    }

    /**
     * Updates the current editor view (front/back/styling).
     * Only valid when in Loaded state.
     */
    fun setCurrentEditorView(viewType: EditorViewType) {
        updateLoadedState { it.copy(currentEditorView = viewType) }
    }

    /**
     * Updates template content for the given ordinal and view type.
     */
    fun updateTemplateContent(
        ord: CardOrdinal,
        viewType: EditorViewType,
        content: String,
    ) {
        val loadedState = _state.value as? CardTemplateEditorState.Loaded ?: return
        val tempNotetype = loadedState.tempNotetype
        // During ViewPager tab transitions, TextWatcher may fire with a stale ordinal
        // (e.g. when switching from a note type with 3 templates to one with 2).
        // The legacy code path handles the actual update, so we can safely skip here.
        if (ord < 0 || ord >= tempNotetype.templateCount) {
            Timber.w("updateTemplateContent: ord=$ord out of bounds (count=${tempNotetype.templateCount})")
            return
        }
        val template = tempNotetype.getTemplate(ord)
        when (viewType) {
            EditorViewType.STYLING -> tempNotetype.css = content
            EditorViewType.BACK -> template.afmt = content
            EditorViewType.FRONT -> template.qfmt = content
        }
        tempNotetype.updateTemplate(ord, template)
        Timber.d("Template content updated for ord=$ord, viewType=$viewType")
    }

    /**
     * Attempts to add a new template to the notetype.
     * Returns false if the notetype is cloze (cannot add templates).
     */
    fun addTemplate(): Boolean {
        val loadedState = _state.value as? CardTemplateEditorState.Loaded ?: return false
        val tempNotetype = loadedState.tempNotetype
        if (tempNotetype.notetype.isCloze) {
            Timber.w("Cannot add template to cloze notetype")
            updateLoadedState { it.copy(message = CardTemplateEditorState.UserMessage.CantAddTemplateToDynamic) }
            return false
        }
        Timber.d("Adding new template")
        return true
    }

    /**
     * Attempts to remove the template at the given ordinal.
     * Returns false if this is the last template.
     */
    fun removeTemplate(ord: CardOrdinal): Boolean {
        val loadedState = _state.value as? CardTemplateEditorState.Loaded ?: return false
        val tempNotetype = loadedState.tempNotetype
        if (tempNotetype.templateCount < 2) {
            Timber.w("Cannot delete last template")
            updateLoadedState { it.copy(message = CardTemplateEditorState.UserMessage.CantDeleteLastTemplate) }
            return false
        }
        Timber.d("Removing template at ord=$ord")
        tempNotetype.removeTemplate(ord)
        return true
    }

    /**
     * Clears the current message.
     * Only valid when in Loaded state.
     */
    fun clearMessage() {
        updateLoadedState { it.copy(message = null) }
    }

    /**
     * Helper to update only when in Loaded state.
     * Ignores updates when in Loading, Error, or Finished states.
     */
    private inline fun updateLoadedState(transform: (CardTemplateEditorState.Loaded) -> CardTemplateEditorState.Loaded) {
        _state.update { currentState ->
            when (currentState) {
                is CardTemplateEditorState.Loaded -> transform(currentState)
                else -> currentState
            }
        }
    }
}
