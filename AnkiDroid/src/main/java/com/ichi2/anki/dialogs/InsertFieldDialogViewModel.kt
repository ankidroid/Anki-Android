/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.model.FieldName
import com.ichi2.anki.model.SpecialFields
import com.ichi2.anki.utils.ext.require
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.parcelize.Parcelize

private typealias SpecialFieldModel = com.ichi2.anki.model.SpecialField

/**
 * ViewModel for [InsertFieldDialog]
 *
 * Handles availability of fields
 */
class InsertFieldDialogViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /** The field names of the note type */
    val fieldNames = savedStateHandle.require<ArrayList<String>>(KEY_FIELD_ITEMS).map(::FieldName)

    private val metadata = savedStateHandle.require<InsertFieldMetadata>(KEY_INSERT_FIELD_METADATA)

    val selectedFieldFlow = MutableStateFlow<SelectedField?>(null)

    /**
     * An ordered list of special fields which may be used
     *
     * @see com.ichi2.anki.model.SpecialField
     */
    val specialFields = SpecialFields.all(side = metadata.side)

    /**
     * Select a named field defined on the note type
     */
    fun selectNamedField(fieldName: FieldName) {
        if (!fieldNames.contains(fieldName)) return
        selectedFieldFlow.value = SelectedField.NoteTypeField.from(fieldName)
    }

    /**
     * Select a usable special field
     */
    fun selectSpecialField(field: SpecialFieldModel) {
        if (!specialFields.contains(field)) return
        selectedFieldFlow.value = SelectedField.SpecialField(model = field)
    }

    sealed class SelectedField {
        /**
         * A field defined on the note type
         *
         * e.g `Front`
         */
        class NoteTypeField(
            val name: FieldName,
        ) : SelectedField() {
            override fun renderToTemplateTag(): String = "{{$name}}"

            companion object {
                fun from(fieldName: FieldName) = NoteTypeField(fieldName)
            }
        }

        class SpecialField(
            val model: SpecialFieldModel,
        ) : SelectedField() {
            override fun renderToTemplateTag(): String = "{{${model.name}}}"
        }

        /**
         * Renders the field for use in the Card Template
         *
         * Example: `{{type:Front}}`
         */
        @CheckResult
        abstract fun renderToTemplateTag(): String
    }

    companion object {
        const val KEY_FIELD_ITEMS = "key_field_items"
        const val KEY_INSERT_FIELD_METADATA = "key_field_options"
        const val KEY_REQUEST_KEY = "key_request_key"
    }
}

@Parcelize
data class InsertFieldMetadata(
    val side: SingleCardSide,
) : Parcelable
