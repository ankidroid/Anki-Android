package com.ichi2.anki.notetype.fieldeditor

import androidx.annotation.StringRes
import com.ichi2.anki.notetype.ManageNoteTypesState

data class NoteTypeFieldEditorState(
    val fields: List<NoteTypeFieldRowData>,
    val action: Action = Action.None,
) {
    sealed interface Action {
        data class Undoable(
            @StringRes val resId: Int,
            val formatArgs: List<Any> = emptyList(),
        ) : Action

        data class Rejected(
            @StringRes val resId: Int,
        ) : Action

        data class Error(
            val e: ManageNoteTypesState.ReportableException,
        ) : Action

        data class SaveRequested(
            val isNotUndoable: Boolean,
            val isSchemaChanges: Boolean,
        ) : Action

        object DiscardRequested : Action

        data class Close(
            @StringRes val resId: Int? = null,
        ) : Action

        object None : Action
    }
}
