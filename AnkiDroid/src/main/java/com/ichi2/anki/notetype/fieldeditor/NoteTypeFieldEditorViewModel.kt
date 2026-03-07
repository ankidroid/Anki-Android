package com.ichi2.anki.notetype.fieldeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.getColUnsafe
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.notetype.fieldeditor.NoteTypeFieldEditor.Companion.EXTRA_NOTETYPE_ID
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class NoteTypeFieldEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state by lazy {
        val noteTypeID = savedStateHandle.get<Long>(EXTRA_NOTETYPE_ID) ?: 0
        val notetype = getColUnsafe().notetypes.get(noteTypeID)!!
        MutableStateFlow(
            NoteTypeFieldEditorState(
                notetype = notetype,
                noteTypeId = noteTypeID,
            ),
        )
    }
    val state: StateFlow<NoteTypeFieldEditorState> = _state.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            refreshNoteTypes()
        }
    }

    fun updateCurrentPosition(position: Int) {
        _state.update { oldState -> oldState.copy(currentPos = position) }
    }

    fun add(name: String) {
        Timber.d("doInBackgroundAddField")
        _state.update { oldState -> oldState.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(state.value.noteTypeId)!!
                    notetypes.addField(notetype, notetypes.newField(name))
                    notetypes.save(notetype)
                }
            }
            initialize()
        }
    }

    fun rename(
        field: Field,
        name: String,
    ) {
        Timber.d("doInBackgroundRenameField")
        _state.update { oldState -> oldState.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(state.value.noteTypeId)!!
                    notetypes.renameField(notetype, field, name)
                    notetypes.save(notetype)
                }
            }
            initialize()
        }
    }

    fun delete(field: Field) {
        Timber.d("doInBackGroundDeleteField")
        _state.update { oldState -> oldState.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(state.value.noteTypeId)!!
                    notetypes.removeField(notetype, field)
                    notetypes.save(notetype)
                }
            }
            initialize()
        }
    }

    fun changeSort(position: Int) {
        Timber.d("doInBackgroundChangeSortField")
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(state.value.noteTypeId)!!
                    notetypes.setSortIndex(notetype, position)
                    notetypes.save(notetype)
                }
            }
            initialize()
        }
    }

    fun reposition(
        field: Field,
        position: Int,
    ) {
        Timber.d("doInBackgroundRepositionField")
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(state.value.noteTypeId)!!
                    notetypes.repositionField(notetype, field, position)
                }
            }
            initialize()
        }
    }

    fun languageHint(
        field: Field,
        locale: LanguageHint,
    ) {
        field.languageHint
        viewModelScope.launch {
            runCatching {
                withCol {
                    setLanguageHintForField(notetypes, state.value.notetype, state.value.currentPos, locale)
                }
            }
            initialize()
        }
    }

    fun undo() {
    }

    suspend fun refreshNoteTypes() {
        val notetype = withCol { notetypes.get(state.value.noteTypeId)!! }
        _state.update { oldState -> oldState.copy(isLoading = false, notetype = notetype) }
    }
}
