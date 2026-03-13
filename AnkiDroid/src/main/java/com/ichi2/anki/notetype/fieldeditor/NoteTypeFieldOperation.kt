package com.ichi2.anki.notetype.fieldeditor

import java.util.Locale

sealed interface NoteTypeFieldOperation {
    val isUndoable: Boolean
    val isSchemaChange: Boolean

    data class Add(
        val position: Int,
        val name: String,
    ) : NoteTypeFieldOperation {
        override val isUndoable = true

        override val isSchemaChange = true
    }

    data class Rename(
        val position: Int,
        val oldName: String,
        val newName: String,
    ) : NoteTypeFieldOperation {
        override val isUndoable = true

        override val isSchemaChange = false
    }

    data class Delete(
        val position: Int,
        val fieldData: NoteTypeFieldRowData,
        val isLast: Boolean,
    ) : NoteTypeFieldOperation {
        override val isUndoable = true

        override val isSchemaChange = false
    }

    data class ChangeSort(
        val oldPosition: Int,
        val newPosition: Int,
    ) : NoteTypeFieldOperation {
        override val isUndoable = true

        override val isSchemaChange = true
    }

    data class Reposition(
        val oldPosition: Int,
        val newPosition: Int,
    ) : NoteTypeFieldOperation {
        override val isUndoable = true

        override val isSchemaChange = true
    }

    data class LanguageHint(
        val position: Int,
        val oldLocale: Locale?,
        val newLocale: Locale?,
    ) : NoteTypeFieldOperation {
        override val isUndoable = false

        override val isSchemaChange = true
    }
}
