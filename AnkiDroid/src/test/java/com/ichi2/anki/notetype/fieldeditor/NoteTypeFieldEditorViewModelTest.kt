/*
 * Copyright (c) 2026 S-H-Y-A <s_h_y_a2803@outlook.jp>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype.fieldeditor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteTypeFieldEditorViewModelTest : RobolectricTest() {
    private fun getViewModel(): NoteTypeFieldEditorViewModel {
        val noteTypeName = addStandardNoteType(TEST_NAME, arrayOf("front", "back"), "", "")
        val noteTypeId = col.notetypes.idForName(noteTypeName)!!
        val initialState =
            buildMap {
                this[NoteTypeFieldEditor.EXTRA_NOTETYPE_ID] = noteTypeId
            }
        val savedStateHandle = SavedStateHandle(initialState)
        return NoteTypeFieldEditorViewModel(savedStateHandle)
    }

    @Test
    fun testAddField() =
        runTest {
            val viewModel = getViewModel()
            viewModel.refreshNoteTypes()
            viewModel.add(NEW_FIELD_NAME)
            assert(
                viewModel.state.value.fieldsLabels
                    .last() == NEW_FIELD_NAME,
            )
        }

    @Test
    fun testAddBlankNameField() =
        runTest {
            val viewModel = getViewModel()
            viewModel.refreshNoteTypes()
            val originalLabels = viewModel.state.value.fieldsLabels
            viewModel.add(EMPTY_FIELD_NAME)
            assert(viewModel.state.value.fieldsLabels == originalLabels)
        }

    @Test
    fun testRenameField() =
        runTest {
            val viewModel = getViewModel()
            viewModel.refreshNoteTypes()
            val position = 0
            viewModel.rename(position, NEW_FIELD_NAME)
            assert(viewModel.state.value.fieldsLabels[position] == NEW_FIELD_NAME)
        }

    @Test
    fun testDeleteField() =
        runTest {
            val viewModel = getViewModel()
            viewModel.refreshNoteTypes()
            val originalLabels = viewModel.state.value.fieldsLabels
            val position = 0
            viewModel.delete(position)
            val expectedLabels =
                originalLabels
                    .toMutableList()
                    .apply {
                        removeAt(position)
                    }.toList()
            assert(viewModel.state.value.fieldsLabels == expectedLabels)
        }

    @Test
    fun testDeleteLastField() =
        runTest {
            val viewModel = getViewModel()
            viewModel.refreshNoteTypes()
            val originalLabels = viewModel.state.value.fieldsLabels
            val originalSize = originalLabels.size
            val position = 0
            repeat(originalSize) {
                viewModel.delete(position)
            }
            val expectedLabels = listOf(originalLabels.last())
            assert(viewModel.state.value.fieldsLabels == expectedLabels)
        }

    @Test
    fun testChangeSort() =
        runTest {
            val viewModel = getViewModel()
            viewModel.refreshNoteTypes()
            val position = 1
            viewModel.changeSort(position)
            assert(viewModel.state.value.sortf == position)
        }

    @Test
    fun testReposition() =
        runTest {
            val viewModel = getViewModel()
            viewModel.refreshNoteTypes()
            val originalLabels = viewModel.state.value.fieldsLabels
            val oldPosition = 0
            val newPosition = 1
            viewModel.reposition(oldPosition, newPosition)
            val expectedLabels =
                originalLabels
                    .toMutableList()
                    .apply {
                        val label = removeAt(oldPosition)
                        add(newPosition, label)
                    }.toList()
            assert(viewModel.state.value.fieldsLabels == expectedLabels)
        }

    companion object {
        private const val TEST_NAME = "BasicTestNoteType"
        private const val NEW_FIELD_NAME = "Extra Field"
        private const val EMPTY_FIELD_NAME = "   "
    }
}
