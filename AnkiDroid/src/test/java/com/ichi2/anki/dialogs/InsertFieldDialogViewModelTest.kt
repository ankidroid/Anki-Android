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

import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.SelectedField.NoteTypeField
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import kotlin.test.assertNotNull

/**
 * Test for [InsertFieldDialogViewModel]
 */
class InsertFieldDialogViewModelTest {
    @Test
    fun `expected fields are exposed`() =
        withViewModel {
            assertThat(
                "Note type fields are copied",
                fieldNames.map { it.name },
                equalTo(listOf("Front", "Back")),
            )
        }

    @Test
    fun `field selection emits data`() =
        withViewModel {
            assertThat(selectedFieldFlow.value, nullValue())

            selectNamedField(fieldNames[0])

            val selectedField = assertNotNull(selectedFieldFlow.value)
            val field = assertInstanceOf<NoteTypeField>(selectedField)
            assertThat(field.renderToTemplateTag(), equalTo("{{Front}}"))
        }

    fun withViewModel(
        fieldList: List<String> = listOf("Front", "Back"),
        block: InsertFieldDialogViewModel.() -> Unit,
    ) {
        val savedStateHandle =
            SavedStateHandle().apply {
                this[InsertFieldDialogViewModel.KEY_FIELD_ITEMS] = ArrayList(fieldList)
            }
        withViewModel(savedStateHandle, block)
    }

    fun withViewModel(
        savedStateHandle: SavedStateHandle,
        block: InsertFieldDialogViewModel.() -> Unit,
    ) {
        InsertFieldDialogViewModel(savedStateHandle).run(block)
    }
}
