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
import org.junit.Assert.assertFalse
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
    fun `setCurrentTemplateOrd is ignored when Loading`() {
        val viewModel = createViewModel()
        // Still in Loading state
        viewModel.setCurrentTemplateOrd(2)

        // Should still be Loading
        assertTrue(viewModel.state.value is CardTemplateEditorState.Loading)
    }

    @Test
    fun `onFinish transitions to Finished state`() {
        val viewModel = createViewModel()
        viewModel.onFinish()

        assertTrue(viewModel.state.value is CardTemplateEditorState.Finished)
    }

    @Test
    fun `addTemplate returns false when not in Loaded state`() {
        val viewModel = createViewModel()
        assertFalse(viewModel.addTemplate())
    }

    @Test
    fun `removeTemplate returns false when not in Loaded state`() {
        val viewModel = createViewModel()
        assertFalse(viewModel.removeTemplate(0))
    }

    @Test
    fun `updateTemplateContent does nothing when not in Loaded state`() {
        val viewModel = createViewModel()
        // Should not crash
        viewModel.updateTemplateContent(0, EditorViewType.FRONT, "test content")
        assertTrue(viewModel.state.value is CardTemplateEditorState.Loading)
    }
}
