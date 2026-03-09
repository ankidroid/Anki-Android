package com.ichi2.anki.notetype.fieldeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.collection.copy
import anki.notetypes.Notetype
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.ext.indexOfOrNull
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.UndoStepCounter
import com.ichi2.anki.libanki.backend.BackendUtils.toJsonBytes
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.updateNotetype
import com.ichi2.anki.notetype.fieldeditor.NoteTypeFieldEditor.Companion.EXTRA_NOTETYPE_ID
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
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
    var undoStepCounter: UndoStepCounter? = null
    private val _state by lazy {
        MutableStateFlow(
            NoteTypeFieldEditorState(
                fields = emptyList(),
            ),
        )
    }
    val state: StateFlow<NoteTypeFieldEditorState> = _state.asStateFlow()

    init {
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
            mergeUndoableOp {
                safeAddField(noteTypeId, name)
                    .onSuccess { smartRefresh() }
                    .onFailure { forceRefresh() }
                    .getOrDefault(OpChanges.getDefaultInstance())
            }
        }
    }

    /**
     * Rename the existing field with the given name
     * @param position the position of the field to rename
     * @param name the name of the field, which must be unique, not empty, addowed to the notetype
     */
    fun rename(
        position: Int,
        name: String,
    ) {
        Timber.d("doInBackgroundRenameField")
        viewModelScope.launch {
            mergeUndoableOp {
                safeRenameField(noteTypeId, position, name)
                    .onSuccess { smartRefresh() }
                    .onFailure { forceRefresh() }
                    .getOrDefault(OpChanges.getDefaultInstance())
            }
        }
    }

    /**
     * Delete the existing field with the given name
     * @param position the position of the field to delete
     */
    fun delete(position: Int) {
        Timber.d("doInBackgroundDeleteField")
        viewModelScope.launch {
            mergeUndoableOp {
                safeDeleteField(noteTypeId, position)
                    .onSuccess { smartRefresh() }
                    .onFailure { forceRefresh() }
                    .getOrDefault(OpChanges.getDefaultInstance())
            }
        }
    }

    /**
     * Set the existing field as the order field
     * @param position the position of the target field
     */
    fun changeSort(position: Int) {
        Timber.d("doInBackgroundChangeSortField")
        viewModelScope.launch {
            mergeUndoableOp {
                safeChangeSort(noteTypeId, position)
                    .onSuccess { smartRefresh() }
                    .onFailure { forceRefresh() }
                    .getOrDefault(OpChanges.getDefaultInstance())
            }
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
            mergeUndoableOp {
                safeReposition(noteTypeId, oldPosition, newPosition)
                    .onSuccess { smartRefresh() }
                    .onFailure { forceRefresh() }
                    .getOrDefault(OpChanges.getDefaultInstance())
            }
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
            mergeUndoableOp {
                safeChangeLanguageHint(noteTypeId, position, locale)
                    .onSuccess { smartRefresh() }
                    .onFailure { forceRefresh() }
                    .getOrDefault(OpChanges.getDefaultInstance())
                    .copy {
                        notetype = true
                    }
            }
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
            _state.update {
                val list = it.fields.toMutableList()
                list.apply {
                    this[position] = this[position].copy(uuid = UUID.randomUUID().toString())
                }
                it.copy(fields = list.toList())
            }
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
    fun smartRefresh() {
        viewModelScope.launch {
            val oldData = state.value.fields
            val newData = withCol { obtainData() }
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
    }

    /**
     * Obtain the data from the database and update the state
     * This completely overrides the state.
     * This is useful when something went wrong or you want to update the whole data.
     * @see NoteTypeFieldEditorViewModel.forceRefresh(Int)
     * @see NoteTypeFieldEditorViewModel.smartRefresh()
     */
    fun forceRefresh() {
        viewModelScope.launch {
            _state.update { oldValue ->
                val data = withCol { obtainData() }
                oldValue.copy(fields = data)
            }
        }
    }

    private suspend fun <T : Any> mergeUndoableOp(block: Collection.() -> T) {
        if (undoStepCounter == null) {
            withCol {
                undoStepCounter = addCustomUndoEntry(tr.actionsUpdateNotetype())
            }
        }
        undoableOp {
            block()
        }
        val step = undoStepCounter ?: return
        undoableOp {
            mergeUndoEntries(step)
        }
    }

    private fun Collection.obtainData(): List<NoteTypeFieldRowData> {
        val langugageMap =
            notetypes.get(noteTypeId)!!.fields.associate {
                it.name to it.languageHint
            }
        return getNotetype(noteTypeId).run {
            val sortF = config.sortFieldIdx
            fieldsList.mapIndexed { index, it ->
                NoteTypeFieldRowData(
                    name = it.name,
                    isOrder = index == sortF,
                    locale = langugageMap.getOrDefault(it.name, null),
                )
            }
        }
    }

    private fun Collection.safeAddField(
        ntid: NoteTypeId,
        newName: String,
    ) = runCatching {
        val notetype =
            getNotetype(ntid).copy {
                fields.apply {
                    val field =
                        Notetype.Field
                            .newBuilder()
                            .setName(newName)
                            .build()
                    add(field)
                }
            }
        updateNotetype(notetype)
    }

    private fun Collection.safeRenameField(
        ntid: NoteTypeId,
        position: Int,
        newName: String,
    ) = runCatching {
        val notetype =
            getNotetype(ntid).copy {
                fields.apply {
                    val field = this[position]
                    this[position] = field.copy { name = newName }
                }
            }
        updateNotetype(notetype)
    }

    private fun Collection.safeDeleteField(
        ntid: NoteTypeId,
        position: Int,
    ) = runCatching {
        val notetype =
            getNotetype(ntid).copy {
                fields.apply {
                    val list = this.toMutableList().apply { removeAt(position) }.toList()
                    clear()
                    addAll(list)
                }
            }
        updateNotetype(notetype)
    }

    private fun Collection.safeReposition(
        ntid: NoteTypeId,
        oldPosition: Int,
        newPosition: Int,
    ) = runCatching {
        val notetype =
            getNotetype(ntid).copy {
                fields.apply {
                    val list =
                        this
                            .toMutableList()
                            .apply {
                                val field = this.removeAt(oldPosition)
                                this.add(newPosition, field)
                            }.toList()
                    clear()
                    addAll(list)
                }
            }
        updateNotetype(notetype)
    }

    private fun Collection.safeChangeSort(
        ntid: NoteTypeId,
        position: Int,
    ) = runCatching {
        val notetype =
            getNotetype(ntid).copy {
                val newConfig = configOrNull ?: Notetype.Config.newBuilder().build()
                config = newConfig.copy { sortFieldIdx = position }
            }
        updateNotetype(notetype)
    }

    private fun Collection.safeChangeLanguageHint(
        ntid: NoteTypeId,
        position: Int,
        selectedLocale: LanguageHint?,
    ) = runCatching {
        // notetypes.save(notetype) (which call addOrUpdateNotetype()) will delete OpChanges, so we need to convert Field to Notetype.Field
        val notetypeJson = notetypes.get(ntid)!!
        val field = notetypeJson.getField(position)
        field.languageHint = selectedLocale

        val notetype =
            getNotetype(ntid).copy {
                fields.apply {
                    val list =
                        this
                            .toMutableList()
                            .apply {
                                this[position] = Notetype.Field.parseFrom(toJsonBytes(field))
                            }.toList()
                    clear()
                    addAll(list)
                }
            }
        Timber.i("Set field locale to %s", selectedLocale)
        updateNotetype(notetype)
    }
}
