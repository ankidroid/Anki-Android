package com.ichi2.anki.notetype.fieldeditor

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.Locale

@Parcelize
sealed interface NoteTypeFieldOperation : Parcelable {
    val isUndoable: Boolean
    val isSchemaChange: Boolean

    data class Add(
        val position: Int,
        val name: String,
    ) : NoteTypeFieldOperation {
        @IgnoredOnParcel
        override val isUndoable = true

        @IgnoredOnParcel
        override val isSchemaChange = true
    }

    data class Rename(
        val position: Int,
        val oldName: String,
        val newName: String,
    ) : NoteTypeFieldOperation {
        @IgnoredOnParcel
        override val isUndoable = true

        @IgnoredOnParcel
        override val isSchemaChange = false
    }

    data class Delete(
        val position: Int,
        val fieldData: NoteTypeFieldRowData,
        val isLast: Boolean,
    ) : NoteTypeFieldOperation {
        @IgnoredOnParcel
        override val isUndoable = true

        @IgnoredOnParcel
        override val isSchemaChange = false
    }

    data class ChangeSort(
        val oldPosition: Int,
        val newPosition: Int,
    ) : NoteTypeFieldOperation {
        @IgnoredOnParcel
        override val isUndoable = true

        @IgnoredOnParcel
        override val isSchemaChange = true
    }

    data class Reposition(
        val oldPosition: Int,
        val newPosition: Int,
    ) : NoteTypeFieldOperation {
        @IgnoredOnParcel
        override val isUndoable = true

        @IgnoredOnParcel
        override val isSchemaChange = true
    }

    data class LanguageHint(
        val position: Int,
        val oldLocale: Locale?,
        val newLocale: Locale?,
    ) : NoteTypeFieldOperation {
        @IgnoredOnParcel
        override val isUndoable = false

        @IgnoredOnParcel
        override val isSchemaChange = true
    }
}
