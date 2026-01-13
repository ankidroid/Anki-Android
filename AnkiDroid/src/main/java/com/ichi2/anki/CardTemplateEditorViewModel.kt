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
import com.ichi2.anki.libanki.CardOrdinal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CardTemplateEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow<CardTemplateEditorState>(CardTemplateEditorState.Loading)
    val state: StateFlow<CardTemplateEditorState> = _state.asStateFlow()

    /**
     * Transitions to the Loaded state.
     * Called internally after data is successfully loaded.
     */
    internal fun onLoadComplete() {
        _state.value = CardTemplateEditorState.Loaded()
    }

    /**
     * Transitions to the Error state.
     * Called internally when an error occurs.
     */
    internal fun onError(exception: CardTemplateEditorState.ReportableException) {
        _state.value = CardTemplateEditorState.Error(exception)
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
     * Updates the current editor view.
     * Only valid when in Loaded state.
     */
    fun setCurrentEditorView(viewType: EditorViewType) {
        updateLoadedState { it.copy(currentEditorView = viewType) }
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
