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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorViewModelTest : JvmTest() {
    private lateinit var viewModel: CardTemplateEditorViewModel

    @Before
    override fun setUp() {
        super.setUp()
        viewModel = CardTemplateEditorViewModel()
    }

    @Test
    fun `initial state is Loading`() {
        assertIs<CardTemplateEditorState.Loading>(viewModel.state.value)
    }

    @Test
    fun `onLoadComplete transitions to Loaded state`() {
        viewModel.onLoadComplete()

        val state = assertIs<CardTemplateEditorState.Loaded>(viewModel.state.value)
        assertEquals(0, state.currentTemplateOrd)
        assertEquals(EditorViewType.FRONT, state.currentEditorView)
        assertNull(state.message)
    }

    @Test
    fun `setCurrentTemplateOrd updates state when Loaded`() {
        viewModel.onLoadComplete()

        viewModel.setCurrentTemplateOrd(2)

        val state = assertIs<CardTemplateEditorState.Loaded>(viewModel.state.value)
        assertEquals(2, state.currentTemplateOrd)
    }

    @Test
    fun `setCurrentTemplateOrd is ignored when Loading`() {
        // Still in Loading state
        viewModel.setCurrentTemplateOrd(2)

        // Should still be Loading
        assertIs<CardTemplateEditorState.Loading>(viewModel.state.value)
    }

    @Test
    fun `setCurrentEditorView updates state when Loaded`() {
        viewModel.onLoadComplete()

        viewModel.setCurrentEditorView(EditorViewType.STYLING)

        val state = assertIs<CardTemplateEditorState.Loaded>(viewModel.state.value)
        assertEquals(EditorViewType.STYLING, state.currentEditorView)
    }

    @Test
    fun `onFinish transitions to Finished state`() {
        viewModel.onLoadComplete()
        viewModel.onFinish()

        assertIs<CardTemplateEditorState.Finished>(viewModel.state.value)
    }

    @Test
    fun `onError transitions to Error state`() {
        val exception = CardTemplateEditorState.ReportableException(RuntimeException("test"))

        viewModel.onError(exception)

        val state = assertIs<CardTemplateEditorState.InitializationError>(viewModel.state.value)
        assertEquals(exception, state.exception)
    }

    @Test
    fun `clearMessage resets message to null when Loaded`() {
        viewModel.onLoadComplete()

        viewModel.clearMessage()

        val state = assertIs<CardTemplateEditorState.Loaded>(viewModel.state.value)
        assertNull(state.message)
    }
}
