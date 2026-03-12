package com.ichi2.anki.notetype.fieldeditor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Locale
import java.util.UUID

@Parcelize
data class NoteTypeFieldRowData(
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val isOrder: Boolean = false,
    val locale: Locale? = null,
) : Parcelable
