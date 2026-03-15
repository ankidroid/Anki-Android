package com.ichi2.anki.notetype.fieldeditor

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.notetypes.Notetype
import anki.notetypes.NotetypeKt
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.updateNotetype
import com.ichi2.anki.notetype.ManageNoteTypesState.ReportableException
import com.ichi2.anki.notetype.fieldeditor.NoteTypeFieldEditor.Companion.EXTRA_NOTETYPE_ID
import com.ichi2.anki.notetype.fieldeditor.NoteTypeFieldEditorViewModel.Companion.NO_POSITION
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.utils.FieldUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import java.util.UUID

class NoteTypeFieldEditorViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val ntid = savedStateHandle.get<Long>(EXTRA_NOTETYPE_ID)!!
    private val fieldsEditOperationStack = MutableStateFlow(listOf<NoteTypeFieldOperation>())

    /**
     * Checks if there are unsaved undoable changes
     */
    private val hasUndoableOperation get() = fieldsEditOperationStack.value.any { !it.isUndoable }
    private val _state = MutableStateFlow(NoteTypeFieldEditorState(fields = emptyList()))

    val state: StateFlow<NoteTypeFieldEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    /**
     * Creates new field with the given name
     * @param position the position of the new field or [NO_POSITION] to add to the end
     * @param name the name of the field, which must be unique, not empty, allowed for the notetype
     */
    fun add(
        position: Int = NO_POSITION,
        name: String,
    ) {
        Timber.d("addOperationStackAddField")
        Timber.d("add $name at $position")
        val nameList = _state.value.fields.map { it.name.savedName }
        when (val result = FieldUtil.uniqueName(nameList = nameList, newName = name)) {
            is FieldUtil.UniqueNameResult.Success -> {
                val position = if (position == NO_POSITION) _state.value.fields.lastIndex + 1 else position
                _state.update { oldValue ->
                    val fields = oldValue.fields.toMutableList()
                    val name =
                        NoteTypeFieldRowData.Name(
                            savedName = result.name,
                            editingName = null,
                            valid = result,
                        )
                    fields.temporaryAdd(position, name)
                    val action = NoteTypeFieldEditorState.Action.None
                    return@update oldValue.copy(fields = fields.toList(), action = action)
                }
                val operation = NoteTypeFieldOperation.Add(position, result.name)
                fieldsEditOperationStack.update {
                    val mutableStack = it.toMutableList()
                    mutableStack.add(operation)
                    return@update mutableStack.toList()
                }
            }

            FieldUtil.UniqueNameResult.Failure.DuplicateName -> TODO()
            FieldUtil.UniqueNameResult.Failure.EmptyName -> TODO()
        }
    }

    /**
     * Renames the existing field with the given name
     *
     *
     * @param position the position of the field to rename
     * @param name the name of the field
     * @param isEditing true if the user is still typing. If true, undo snackbar doesn't appear.
     */
    fun rename(
        position: Int,
        name: String,
        isEditing: Boolean,
    ) {
        Timber.d("addOperationStackRenameField")
        Timber.d("isEditing: $isEditing")
        val mutableStack = fieldsEditOperationStack.value.toMutableList()
        while (true) {
            // remove previous rename operations
            val last = mutableStack.lastOrNull()
            if (last is NoteTypeFieldOperation.Rename && last.position == position) {
                mutableStack.removeAt(mutableStack.lastIndex)
            } else {
                break
            }
        }

        val oldValue = _state.value
        val oldField = oldValue.fields[position]
        val oldName = oldField.name
        val nameList = _state.value.fields.map { it.name.savedName }
        val result = FieldUtil.uniqueName(nameList, position, name)

        Timber.d("rename $oldName to $name at $position")

        when (result) {
            is FieldUtil.UniqueNameResult.Success -> {
                val fields = oldValue.fields.toMutableList()
                val newName =
                    if (isEditing) {
                        oldName.copy(editingName = name, valid = result)
                    } else {
                        oldName.copy(savedName = result.name, editingName = null, valid = result)
                    }
                fields.temporaryRename(position, newName)
                val action =
                    NoteTypeFieldEditorState.Action.None
                _state.value = oldValue.copy(fields = fields.toList(), action = action)

                if (oldName.savedName == result.name) return

                val operation =
                    NoteTypeFieldOperation.Rename(position, oldName.savedName, result.name)
                mutableStack.add(operation)
                fieldsEditOperationStack.value = mutableStack.toList()
            }

            is FieldUtil.UniqueNameResult.Failure -> {
                Timber.d("rename failed due to $result at $position")
                val fields = oldValue.fields.toMutableList()
                val newName =
                    if (isEditing) {
                        oldName.copy(editingName = name, valid = result)
                    } else {
                        oldName.copy(editingName = null, valid = result)
                    }
                fields.temporaryRename(position, newName)
                val action =
                    if (isEditing) {
                        NoteTypeFieldEditorState.Action.None
                    } else {
                        NoteTypeFieldEditorState.Action.Rejected(result.resId)
                    }
                _state.value = oldValue.copy(fields = fields.toList(), action = action)
            }
        }
    }

    /**
     * Deletes the existing field with the given name
     * @param position the position of the field to delete
     */
    fun delete(position: Int) {
        Timber.d("addOperationStackDeleteField")
        val isLast = position == _state.value.fields.lastIndex

        if (position == 0 && isLast) {
            val action = NoteTypeFieldEditorState.Action.Rejected(R.string.toast_last_field)
            _state.value = _state.value.copy(action = action)
            Timber.d("delete failed: cannot delete the only remaining field")
            return
        }

        val fieldData = _state.value.fields[position]
        Timber.d("delete ${fieldData.name} at $position")
        _state.update { oldValue ->
            val fields = oldValue.fields.toMutableList()
            fields.temporaryDelete(position)
            if (fieldData.isOrder) {
                val newPosition = if (isLast) position - 1 else position
                Timber.d("change sort field from $position to $newPosition because of delete")
                fields.temporaryChangeSort(newPosition)
            }
            val action =
                NoteTypeFieldEditorState.Action.Undoable(
                    R.string.model_field_editor_delete_success_result,
                    arrayListOf(fieldData.name.savedName),
                )
            return@update oldValue.copy(fields = fields.toList(), action = action)
        }
        val operation = NoteTypeFieldOperation.Delete(position, fieldData, isLast)
        fieldsEditOperationStack.update {
            val mutableStack = it.toMutableList()
            mutableStack.add(operation)
            return@update mutableStack.toList()
        }
    }

    /**
     * Moves the existing field to the given position
     *
     * @param oldPosition the position of the target field before repositioning it
     * @param newPosition the new position of the target field
     */
    fun reposition(
        oldPosition: Int,
        newPosition: Int,
    ) {
        Timber.d("addOperationStackRepositionField")
        Timber.d("reposition $oldPosition to $newPosition")
        _state.update { oldValue ->
            val fields = oldValue.fields.toMutableList()
            fields.temporaryReposition(oldPosition, newPosition)
            val action =
                NoteTypeFieldEditorState.Action.None
            return@update oldValue.copy(fields = fields.toList(), action = action)
        }
        val operation = NoteTypeFieldOperation.Reposition(oldPosition, newPosition)
        fieldsEditOperationStack.update {
            val mutableStack = it.toMutableList()
            mutableStack.add(operation)
            return@update mutableStack.toList()
        }
    }

    /**
     * Sets the existing field as the order field
     * @param position the position of the target field
     */
    fun changeSort(position: Int) {
        Timber.d("addOperationStackChangeSortField")
        Timber.d("changeSort to $position")
        val fields = _state.value.fields
        val oldPosition = fields.indexOfFirst { it.isOrder }
        if (oldPosition == position) {
            val action = NoteTypeFieldEditorState.Action.None
            _state.value = _state.value.copy(action = action)
            return
        }
        _state.update { oldValue ->
            val list = fields.toMutableList()
            list.temporaryChangeSort(position)
            val action = NoteTypeFieldEditorState.Action.None
            return@update oldValue.copy(fields = list.toList(), action = action)
        }
        val operation = NoteTypeFieldOperation.ChangeSort(oldPosition, position)
        fieldsEditOperationStack.update {
            val mutableStack = it.toMutableList()
            mutableStack.add(operation)
            return@update mutableStack.toList()
        }
    }

    /**
     * Sets a [LanguageHint] as the keyboard hint for the field
     *
     * @param position the position of the target field
     * @param locale the [LanguageHint] to set, or null to clear the hint
     * @see LanguageHintService
     */
    fun setLanguageHint(
        position: Int,
        locale: LanguageHint?,
    ) {
        Timber.d("addOperationStackSetLanguageHint")
        Timber.d("setLanguageHint to $locale at $position")
        val oldLocale = _state.value.fields[position].locale
        if (oldLocale == locale) {
            val action = NoteTypeFieldEditorState.Action.None
            _state.value = _state.value.copy(action = action)
            return
        }
        _state.update { oldValue ->
            val fields = oldValue.fields.toMutableList()
            fields.temporarySetLanguageHint(position, locale)
            val action = NoteTypeFieldEditorState.Action.None
            return@update oldValue.copy(fields = fields.toList(), action = action)
        }
        val operation = NoteTypeFieldOperation.LanguageHint(position, oldLocale, locale)
        fieldsEditOperationStack.update {
            val mutableStack = it.toMutableList()
            mutableStack.add(operation)
            return@update mutableStack.toList()
        }
    }

    /**
     * Obtains the field list from [Collection] and refresh the state
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { oldValue ->
                val data = withCol { obtainData() }
                val action = NoteTypeFieldEditorState.Action.None
                return@update oldValue.copy(fields = data, action = action)
            }
        }
        fieldsEditOperationStack.update {
            val mutableStack = it.toMutableList()
            mutableStack.clear()
            return@update mutableStack.toList()
        }
    }

    private fun MutableList<NoteTypeFieldRowData>.temporaryAdd(
        position: Int = NO_POSITION,
        name: NoteTypeFieldRowData.Name,
    ) {
        val newField = NoteTypeFieldRowData(name = name)
        if (position != NO_POSITION) {
            this.add(position, newField)
        } else {
            this.add(newField)
        }
    }

    private fun MutableList<NoteTypeFieldRowData>.temporaryRename(
        position: Int,
        newName: NoteTypeFieldRowData.Name,
    ) {
        val field = this[position]
        this[position] = field.copy(name = newName)
    }

    private fun MutableList<NoteTypeFieldRowData>.temporaryDelete(position: Int) {
        removeAt(position)
    }

    private fun MutableList<NoteTypeFieldRowData>.temporaryReposition(
        oldPosition: Int,
        newPosition: Int,
    ) {
        val field = this.removeAt(oldPosition)
        this.add(newPosition, field)
    }

    private fun MutableList<NoteTypeFieldRowData>.temporaryChangeSort(position: Int) {
        for (i in 0..<size) {
            val field = this[i]
            if ((i == position) != field.isOrder) {
                this[i] = field.copy(isOrder = i == position)
            }
        }
    }

    private fun MutableList<NoteTypeFieldRowData>.temporarySetLanguageHint(
        position: Int,
        locale: LanguageHint?,
    ) {
        val field = this[position]
        this[position] = field.copy(locale = locale)
    }

    private fun MutableList<NoteTypeFieldRowData>.temporaryUpdateUuid(
        position: Int,
        uuid: String = UUID.randomUUID().toString(),
    ) {
        val field = this[position]
        this[position] = field.copy(uuid = uuid)
    }

    /**
     * Undo the last unsaved change
     */
    fun undo() {
        val undoOperation = fieldsEditOperationStack.value.lastOrNull()

        if (undoOperation == null) {
            _state.update { oldValue ->
                val e = ReportableException(IllegalStateException("Undo operation is null"))
                oldValue.copy(action = NoteTypeFieldEditorState.Action.Error(e))
            }
            return
        }
        _state.update { oldValue ->
            val list = oldValue.fields.toMutableList()
            list.apply {
                when (undoOperation) {
                    is NoteTypeFieldOperation.Add ->
                        temporaryDelete(undoOperation.position)
                    is NoteTypeFieldOperation.Rename -> {
                        val name =
                            NoteTypeFieldRowData.Name(
                                savedName = undoOperation.oldName,
                                editingName = null,
                                valid = FieldUtil.UniqueNameResult.Success(undoOperation.oldName),
                            )
                        temporaryRename(undoOperation.position, name)
                    }
                    is NoteTypeFieldOperation.Reposition ->
                        temporaryReposition(undoOperation.newPosition, undoOperation.oldPosition)
                    is NoteTypeFieldOperation.ChangeSort ->
                        temporaryChangeSort(undoOperation.oldPosition)
                    is NoteTypeFieldOperation.LanguageHint ->
                        temporarySetLanguageHint(undoOperation.position, undoOperation.oldLocale)
                    is NoteTypeFieldOperation.Delete -> {
                        temporaryAdd(undoOperation.position, undoOperation.fieldData.name)
                        temporaryUpdateUuid(undoOperation.position, undoOperation.fieldData.uuid)
                        temporarySetLanguageHint(undoOperation.position, undoOperation.fieldData.locale)
                        if (undoOperation.isLast && undoOperation.fieldData.isOrder) {
                            temporaryChangeSort(undoOperation.position)
                        }
                    }
                }
            }
            val action = NoteTypeFieldEditorState.Action.None
            return@update oldValue.copy(fields = list.toList(), action = action)
        }
        fieldsEditOperationStack.update {
            val mutableStack = it.toMutableList()
            mutableStack.removeLastOrNull()
            return@update mutableStack.toList()
        }
    }

    /**
     * Checks unsaved changes and requests to show discard confirmation dialog if changes not saved,
     * otherwise close the editor
     * @param force if true, discard changes even if not saved
     */
    fun requestDiscardChangesAndClose(force: Boolean = false) {
        Timber.d("requestDiscardChangesAndClose")
        val action =
            when {
                fieldsEditOperationStack.value.isEmpty() -> NoteTypeFieldEditorState.Action.Close()
                force -> NoteTypeFieldEditorState.Action.Close(R.string.model_field_editor_discard_success_result)
                else -> NoteTypeFieldEditorState.Action.DiscardRequested
            }
        _state.value = _state.value.copy(action = action)
    }

    /**
     * Checks unsaved changes and requests to show save confirmation dialog if changes not saved,
     * otherwise close the editor
     * @param force if true, discard changes even if not saved
     */
    fun requestSaveAndClose(force: Boolean = false) {
        Timber.d("requestSaveAndClose")
        val isSchemaChange = fieldsEditOperationStack.value.any { it.isSchemaChange }
        if (fieldsEditOperationStack.value.isEmpty()) {
            val action = NoteTypeFieldEditorState.Action.Close()
            _state.value = _state.value.copy(action = action)
        } else if (force || (!isSchemaChange && !hasUndoableOperation)) {
            viewModelScope.launch {
                val action =
                    save().fold(
                        onSuccess = { NoteTypeFieldEditorState.Action.Close(R.string.model_field_editor_save_success_result) },
                        onFailure = { NoteTypeFieldEditorState.Action.Error(ReportableException(it, it !is BackendException)) },
                    )
                _state.value = _state.value.copy(action = action)
            }
        } else {
            val action = NoteTypeFieldEditorState.Action.SaveRequested(hasUndoableOperation, isSchemaChange)
            _state.value = _state.value.copy(action = action)
        }
    }

    /**
     * Resets the current action of the state
     *
     * used when the current action is consumed in the side of UI
     */
    fun resetAction() {
        val actionNone = NoteTypeFieldEditorState.Action.None
        _state.value = _state.value.copy(action = actionNone)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun save() =
        withContext(Dispatchers.IO) {
            return@withContext if (hasUndoableOperation) {
                saveIrreversible()
            } else {
                saveReversible()
            }.map { }
        }

    /**
     * Saves changes and notify change subscribers of them
     *
     * Don't use this if [NoteTypeFieldOperation.LanguageHint] is included in fieldsEditOperationStack
     * because it cannot be undone and clear Anki's change history.
     * @see saveIrreversible
     */
    private suspend fun saveReversible() =
        runCatching {
            undoableOp {
                val notetype =
                    getNotetype(ntid).copy {
                        fieldsEditOperationStack.value.forEach { operation ->
                            when (operation) {
                                is NoteTypeFieldOperation.Add ->
                                    addField(operation.name)
                                is NoteTypeFieldOperation.Rename ->
                                    renameField(operation.position, operation.newName)
                                is NoteTypeFieldOperation.Delete ->
                                    deleteField(operation.position)
                                is NoteTypeFieldOperation.Reposition ->
                                    repositionField(operation.oldPosition, operation.newPosition)
                                is NoteTypeFieldOperation.ChangeSort ->
                                    changeSortField(operation.newPosition)
                                is NoteTypeFieldOperation.LanguageHint ->
                                    throw IllegalStateException("Language hint is not reversible.")
                            }
                        }
                    }
                updateNotetype(notetype)
            }
        }

    /**
     * Saves changes
     *
     * Use this if [NoteTypeFieldOperation.LanguageHint] is included in fieldsEditOperationStack
     * because it cannot be undone and clear Anki's change history.
     * @see saveReversible
     */
    private suspend fun saveIrreversible() =
        runCatching {
            withCol {
                notetypes.apply {
                    val notetype = get(ntid)!!
                    fieldsEditOperationStack.value.forEach { operation ->
                        when (operation) {
                            is NoteTypeFieldOperation.Add ->
                                addFieldAlternative(
                                    notetype,
                                    operation.name,
                                )
                            is NoteTypeFieldOperation.Rename ->
                                renameFieldAlternative(
                                    notetype,
                                    operation.position,
                                    operation.newName,
                                )
                            is NoteTypeFieldOperation.Delete ->
                                deleteFieldAlternative(
                                    notetype,
                                    operation.position,
                                )
                            is NoteTypeFieldOperation.Reposition ->
                                repositionAlternative(
                                    notetype,
                                    operation.oldPosition,
                                    operation.newPosition,
                                )
                            is NoteTypeFieldOperation.ChangeSort ->
                                changeSortFieldAlternative(
                                    notetype,
                                    operation.newPosition,
                                )
                            is NoteTypeFieldOperation.LanguageHint ->
                                setLanguageHintAlternative(
                                    notetype,
                                    operation.position,
                                    operation.newLocale,
                                )
                        }
                    }
                }
            }
        }

    /**
     * Obtains the field list from [Collection]
     * @return a list of [NoteTypeFieldRowData]
     */
    private fun Collection.obtainData(): List<NoteTypeFieldRowData> {
        val languageMap =
            notetypes.get(ntid)!!.fields.associate {
                it.name to it.languageHint
            }
        return getNotetype(ntid).run {
            val sortF = config.sortFieldIdx
            fieldsList.mapIndexed { index, it ->
                val name =
                    NoteTypeFieldRowData.Name(
                        savedName = it.name,
                        editingName = null,
                        valid = FieldUtil.UniqueNameResult.Success(it.name),
                    )
                NoteTypeFieldRowData(
                    name = name,
                    isOrder = index == sortF,
                    locale = languageMap.getOrDefault(it.name, null),
                )
            }
        }
    }

    private fun NotetypeKt.Dsl.addField(newName: String) {
        Timber.d("doInBackgroundAddField")
        fields.apply {
            val field =
                Notetype.Field
                    .newBuilder()
                    .setName(newName)
                    .build()
            add(field)
        }
    }

    private fun NotetypeKt.Dsl.renameField(
        position: Int,
        newName: String,
    ) {
        Timber.d("doInBackgroundRenameField")
        fields.apply {
            val field = this[position]
            this[position] = field.copy { name = newName }
        }
    }

    private fun NotetypeKt.Dsl.deleteField(position: Int) {
        Timber.d("doInBackgroundDeleteField")
        fields.apply {
            val list = this.toMutableList().apply { removeAt(position) }.toList()
            clear()
            addAll(list)
        }
    }

    private fun NotetypeKt.Dsl.repositionField(
        oldPosition: Int,
        newPosition: Int,
    ) {
        Timber.d("doInBackgroundRepositionField")
        Timber.i("Repositioning field from %d to %d", oldPosition, newPosition)
        fields.apply {
            val list =
                toMutableList()
                    .apply {
                        val field = this.removeAt(oldPosition)
                        this.add(newPosition, field)
                    }.toList()
            clear()
            addAll(list)
        }
    }

    private fun NotetypeKt.Dsl.changeSortField(position: Int) {
        Timber.d("doInBackgroundChangeSortField")
        config = config.copy { sortFieldIdx = position }
    }

    private fun Notetypes.addFieldAlternative(
        notetype: NotetypeJson,
        name: String,
    ) {
        Timber.d("doInBackgroundAddFieldAlternative")
        val field = newField(name)
        addField(notetype, field)
        save(notetype)
    }

    private fun Notetypes.renameFieldAlternative(
        notetype: NotetypeJson,
        position: Int,
        newName: String,
    ) {
        Timber.d("doInBackgroundRenameFieldAlternative")
        val field = notetype.getField(position)
        renameField(notetype, field, newName)
        save(notetype)
    }

    private fun Notetypes.deleteFieldAlternative(
        notetype: NotetypeJson,
        position: Int,
    ) {
        Timber.d("doInBackgroundDeleteFieldAlternative")
        val field = notetype.getField(position)
        removeField(notetype, field)
        save(notetype)
    }

    private fun Notetypes.repositionAlternative(
        notetype: NotetypeJson,
        oldPosition: Int,
        newPosition: Int,
    ) {
        Timber.d("doInBackgroundRepositionFieldAlternative")
        Timber.i("Repositioning field from %d to %d", oldPosition, newPosition)
        val field = notetype.getField(oldPosition)
        repositionField(notetype, field, newPosition)
        save(notetype)
    }

    private fun Notetypes.changeSortFieldAlternative(
        notetype: NotetypeJson,
        position: Int,
    ) {
        Timber.d("doInBackgroundChangeSortFieldAlternative")
        setSortIndex(notetype, position)
        save(notetype)
    }

    private fun Notetypes.setLanguageHintAlternative(
        notetype: NotetypeJson,
        position: Int,
        locale: LanguageHint?,
    ) {
        Timber.i("Set field locale to %s", locale)
        LanguageHintService.setLanguageHintForField(this, notetype, position, locale)
    }

    private companion object {
        private const val NO_POSITION = -1
    }
}
