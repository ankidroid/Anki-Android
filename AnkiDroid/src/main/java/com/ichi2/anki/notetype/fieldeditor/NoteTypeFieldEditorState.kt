package com.ichi2.anki.notetype.fieldeditor

import com.ichi2.anki.libanki.Fields
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson

data class NoteTypeFieldEditorState(
    val notetype: NotetypeJson,
    val noteTypeId: NoteTypeId = 0,
    val currentPos: Int = 0,
    val noteFields: Fields = notetype.fields,
    val fieldsLabels: List<String> = notetype.fieldsNames,
    val isLoading: Boolean = false,
)
