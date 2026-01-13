/*
 * Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorViewModelTest : JvmTest() {
    private fun createViewModel() = CardTemplateEditorViewModel()

    @Test
    fun `initial state is Loading`() {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value is CardTemplateEditorState.Loading)
    }

    @Test
    fun `onLoadComplete transitions to Loaded state`() {
        val viewModel = createViewModel()
        viewModel.onLoadComplete()

        val state = viewModel.state.value
        assertTrue(state is CardTemplateEditorState.Loaded)
        assertEquals(0, (state as CardTemplateEditorState.Loaded).currentTemplateOrd)
        assertEquals(EditorViewType.FRONT, state.currentEditorView)
        assertNull(state.message)
    }

    @Test
    fun `setCurrentTemplateOrd updates state when Loaded`() {
        val viewModel = createViewModel()
        viewModel.onLoadComplete()

        viewModel.setCurrentTemplateOrd(2)

        val state = viewModel.state.value as CardTemplateEditorState.Loaded
        assertEquals(2, state.currentTemplateOrd)
    }

    @Test
    fun `setCurrentTemplateOrd is ignored when Loading`() {
        val viewModel = createViewModel()
        // Still in Loading state
        viewModel.setCurrentTemplateOrd(2)

        // Should still be Loading
        assertTrue(viewModel.state.value is CardTemplateEditorState.Loading)
    }

    @Test
    fun `setCurrentEditorView updates state when Loaded`() {
        val viewModel = createViewModel()
        viewModel.onLoadComplete()

        viewModel.setCurrentEditorView(EditorViewType.STYLING)

        val state = viewModel.state.value as CardTemplateEditorState.Loaded
        assertEquals(EditorViewType.STYLING, state.currentEditorView)
    }

    @Test
    fun `onFinish transitions to Finished state`() {
        val viewModel = createViewModel()
        viewModel.onLoadComplete()
        viewModel.onFinish()

        assertTrue(viewModel.state.value is CardTemplateEditorState.Finished)
    }

    @Test
    fun `onError transitions to Error state`() {
        val viewModel = createViewModel()
        val exception = CardTemplateEditorState.ReportableException(RuntimeException("test"))

        viewModel.onError(exception)

        val state = viewModel.state.value
        assertTrue(state is CardTemplateEditorState.Error)
        assertEquals(exception, (state as CardTemplateEditorState.Error).exception)
    }

    @Test
    fun `clearMessage resets message to null when Loaded`() {
        val viewModel = createViewModel()
        viewModel.onLoadComplete()

        viewModel.clearMessage()

        val state = viewModel.state.value as CardTemplateEditorState.Loaded
        assertNull(state.message)
    }
}
