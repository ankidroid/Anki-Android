package com.ichi2.anki.notetype.fieldeditor

import com.ichi2.utils.FieldUtil
import java.util.Locale
import java.util.UUID

data class NoteTypeFieldRowData(
    val uuid: String = UUID.randomUUID().toString(),
    val name: Name,
    val isOrder: Boolean = false,
    val locale: Locale? = null,
) {
    val displayName get() = name.editingName ?: name.savedName

    data class Name(
        val savedName: String,
        val editingName: String? = null,
        val valid: FieldUtil.UniqueNameResult,
    )
}
