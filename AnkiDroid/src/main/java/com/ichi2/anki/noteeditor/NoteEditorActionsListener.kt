package com.ichi2.anki.noteeditor

interface NoteEditorActionsListener {
    fun performUndo()

    fun saveCurrentTextState(
        fieldId: Int,
        text: String,
    )
}
