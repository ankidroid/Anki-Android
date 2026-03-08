package com.ichi2.anki.notetype.fieldeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.getColUnsafe
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.ext.indexOfOrNull
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.notetype.fieldeditor.NoteTypeFieldEditor.Companion.EXTRA_NOTETYPE_ID
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService.clearLanguageHintForField
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import java.util.UUID

class NoteTypeFieldEditorViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val noteTypeId = savedStateHandle.get<Long>(EXTRA_NOTETYPE_ID) ?: 0
    private val _state by lazy {
        MutableStateFlow(
            NoteTypeFieldEditorState(
                fields = getColUnsafe().notetypes.get(noteTypeId)!!.data,
            ),
        )
    }
    val state: StateFlow<NoteTypeFieldEditorState> = _state.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            forceRefresh()
        }
    }

    /**
     * Create new field with the given name
     * @param name the name of the field, which must be unique, not empty, allowed to the notetype
     */
    fun add(name: String) {
        Timber.d("doInBackgroundAddField")

        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(noteTypeId)!!
                    notetypes.addField(notetype, notetypes.newField(name))
                    notetypes.save(notetype)
                }
            }.fold(
                onSuccess = { _state.updateData { this@updateData.add(NoteTypeFieldRowData(name = name)) } },
                onFailure = { forceRefresh() },
            )
        }
    }

    /**
     * Rename the existing field with the given name
     * @param pos the position of the field to rename
     * @param name the name of the field, which must be unique, not empty, addowed to the notetype
     */
    fun rename(
        pos: Int,
        name: String,
    ) {
        Timber.d("doInBackgroundRenameField")
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(noteTypeId)!!
                    val field = notetype.fields[pos]
                    notetypes.renameField(notetype, field, name)
                    notetypes.save(notetype)
                }
            }.fold(
                onSuccess = { _state.updateData { this[pos] = this[pos].copy(name = name) } },
                onFailure = { forceRefresh() },
            )
        }
    }

    /**
     * Delete the existing field with the given name
     * @param pos the position of the field to delete
     */
    fun delete(pos: Int) {
        Timber.d("doInBackgroundDeleteField")
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(noteTypeId)!!
                    val field = notetype.fields[pos]
                    notetypes.removeField(notetype, field)
                    notetypes.save(notetype)
                }
            }.fold(
                onSuccess = { _state.updateData { removeAt(pos) } },
                onFailure = { forceRefresh() },
            )
        }
    }

    /**
     * Set the existing field as the order field
     * @param position the position of the target field
     */
    fun changeSort(position: Int) {
        Timber.d("doInBackgroundChangeSortField")
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(noteTypeId)!!
                    val oldSortPosition = notetype.sortf
                    notetypes.setSortIndex(notetype, position)
                    notetypes.save(notetype)
                    return@withCol oldSortPosition
                }
            }.fold(
                onSuccess = { oldPosition ->
                    _state.updateData {
                        this[oldPosition] = this[oldPosition].copy(isOrder = false)
                        this[position] = this[position].copy(isOrder = true)
                    }
                },
                onFailure = { forceRefresh() },
            )
        }
    }

    /**
     * Move the existing field to the given position in ViewState
     *
     * This method does NOT persist changes to the database
     *
     * @param oldPosition the current position of the target field
     * @param newPosition the new position of the target field
     * @see NoteTypeFieldEditorViewModel.reposition
     */
    fun visuallyReposition(
        oldPosition: Int,
        newPosition: Int,
    ) {
        _state.update { oldValue ->
            val fields = oldValue.fields.toMutableList()
            Collections.swap(fields, oldPosition, newPosition)
            oldValue.copy(fields = fields.toList())
        }
    }

    /**
     * Move the existing field to the given position in ViewState
     *
     * This method DOES persist changes to the database
     *
     * @param oldPosition the current position of the target field
     * @param newPosition the new position of the target field
     * @see NoteTypeFieldEditorViewModel.visuallyReposition
     */
    fun reposition(
        oldPosition: Int,
        newPosition: Int,
    ) {
        Timber.d("doInBackgroundRepositionField")
        Timber.i("Repositioning field from %d to %d", oldPosition, newPosition)
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(noteTypeId)!!
                    val field = notetype.fields[oldPosition]
                    notetypes.repositionField(notetype, field, newPosition)
                    notetypes.save(notetype)
                }
            }.fold(
                onSuccess = { smartRefresh() },
                onFailure = { forceRefresh() },
            )
        }
    }

    /**
     * Set a [Locale] as the keyboard hint for the field
     *
     * @param position the position of the target field
     * @param locale the [Locale] to set, or null to clear the hint
     * @see LanguageHintService
     */
    fun languageHint(
        position: Int,
        locale: LanguageHint?,
    ) {
        viewModelScope.launch {
            runCatching {
                withCol {
                    val notetype = notetypes.get(noteTypeId)!!
                    if (locale != null) {
                        setLanguageHintForField(notetypes, notetype, position, locale)
                    } else {
                        clearLanguageHintForField(notetypes, notetype, position)
                    }
                }
            }.fold(
                onSuccess = { _state.updateData { this[position] = this[position].copy(locale = locale) } },
                onFailure = { forceRefresh() },
            )
        }
    }

    /**
     * Refresh the data of the target field on the given position
     *
     * This is used to clear user's change which is not applied to the database
     * @param position the position of the target field
     * @see NoteTypeFieldEditorViewModel.smartRefresh()
     * @see NoteTypeFieldEditorViewModel.forceRefresh()
     */
    fun forceRefresh(position: Int) {
        viewModelScope.launch {
            _state.updateData { this[position] = this[position].copy(uuid = UUID.randomUUID().toString()) }
        }
    }

    /**
     * Obtain the data from the database and update the state without the identifying data of the data changing
     *
     * This tries to keep the identifying data of the data of the fields if possible to avoid innecessary UI refreshes.
     * This can be invoked ONLY IF the data is NOT corrupted
     * and ONLY AFTER you updated the database successfully.
     * @see NoteTypeFieldEditorViewModel.forceRefresh(Int)
     * @see NoteTypeFieldEditorViewModel.forceRefresh()
     */
    suspend fun smartRefresh() {
        val oldData = state.value.fields
        val newData = withCol { notetypes.get(noteTypeId)!! }.data
        val oldDataNameList = oldData.map { it.name }
        val newNamedDataIndex = newData.indexOfOrNull { !oldDataNameList.contains(it.name) }
        val isRenamed = oldData.size == newData.size && newNamedDataIndex != null
        val updateData =
            newData.mapIndexed { index, new ->
                if (isRenamed && index == newNamedDataIndex) {
                    // when renamed, index is not changed.
                    val renamedDataIndex = newNamedDataIndex
                    // succeed to the previous uuid referring to index
                    new.copy(uuid = oldData[renamedDataIndex].uuid)
                } else {
                    // succeed to the previous uuid referring to name
                    new.copy(uuid = oldData.firstOrNull { it.name == new.name }?.uuid ?: UUID.randomUUID().toString())
                }
            }
        _state.update { oldState -> oldState.copy(fields = updateData) }
    }

    /**
     * Obtain the data from the database and update the state
     * This completely overrides the state.
     * This is useful when something went wrong or you want to update the whole data.
     * @see NoteTypeFieldEditorViewModel.forceRefresh(Int)
     * @see NoteTypeFieldEditorViewModel.smartRefresh()
     */
    suspend fun forceRefresh() {
        val data = withCol { notetypes.get(noteTypeId)!! }.data
        _state.update { oldState -> oldState.copy(fields = data) }
    }

    private fun MutableStateFlow<NoteTypeFieldEditorState>.updateData(edit: MutableList<NoteTypeFieldRowData>.() -> Unit) =
        update {
            val fields = it.fields.toMutableList()
            edit.invoke(fields)
            it.copy(fields = fields.toList())
        }

    private val NotetypeJson.data: List<NoteTypeFieldRowData> get() {
        val sortF = sortf
        return fields.mapIndexed { index, it ->
            NoteTypeFieldRowData(
                name = it.name,
                isOrder = index == sortF,
                locale = it.languageHint,
            )
        }
    }
}
