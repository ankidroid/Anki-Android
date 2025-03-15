/****************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.notetype

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.getNotetypeNames
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Note: all tests related to the notetypes count should take in consideration the standard notes(6 entries) that are
 * already present.
 */
@RunWith(AndroidJUnit4::class)
class ManageNotetypeViewModelTest : RobolectricTest() {
    @Test
    fun `Deleting a notetype emits the expected state`() =
        runTest {
            val notetypeToBeDeleted = "ToBeDeleted"
            addNoteType(notetypeToBeDeleted)
            val viewModel = ManageNotetypeViewModel()
            viewModel.refresh()
            advanceUntilIdle()
            assertTrue(
                viewModel.currentState.notetypes
                    .map { it.name }
                    .contains(notetypeToBeDeleted),
                "ViewModel doesn't contain the expected test notetype",
            )
            val notetypeId = withCol { getNotetypeNames() }.first { it.name == notetypeToBeDeleted }.id
            viewModel.delete(notetypeId)
            advanceUntilIdle()
            assertEquals(
                PLATFORM_NOTETYPES_COUNT,
                viewModel.currentState.notetypes.size,
                "ViewModel notetype count doesn't math expected count after notetype deletion",
            )
            assertFalse(
                viewModel.currentState.notetypes
                    .map { it.name }
                    .contains(notetypeToBeDeleted),
                "ViewModel still contains the previously deleted notetype",
            )
        }

    @Test
    fun `Renaming a notetype emits the expected state`() =
        runTest {
            val initialNotetypeName = "InitialNotetypeName"
            val newNotetypeName = "NewNotetypeName"
            addNoteType(initialNotetypeName)
            val viewModel = ManageNotetypeViewModel()
            viewModel.refresh()
            advanceUntilIdle()
            assertTrue(
                viewModel.currentState.notetypes
                    .map { it.name }
                    .contains(initialNotetypeName),
                "ViewModel doesn't contain the expected test notetype",
            )
            val notetypeId = withCol { getNotetypeNames() }.first { it.name == initialNotetypeName }.id
            viewModel.rename(notetypeId, newNotetypeName)
            advanceUntilIdle()
            assertFalse(
                viewModel.currentState.notetypes
                    .map { it.name }
                    .contains(initialNotetypeName),
                "ViewModel still contains the initial notetype that was renamed",
            )
            assertTrue(
                viewModel.currentState.notetypes
                    .map { it.name }
                    .contains(newNotetypeName),
                "ViewModel doesn't contain the renamed notetype",
            )
        }

    @Test
    fun `Filtering the notetypes emits the expected state`() =
        runTest {
            addNoteType("Test abc")
            addNoteType("Test abz")
            addNoteType("Test abxw")
            addNoteType("Test abxq")
            addNoteType("Test cdf")
            val viewModel = ManageNotetypeViewModel()
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(
                PLATFORM_NOTETYPES_COUNT + 5,
                viewModel.currentState.notetypes.size,
                "ViewModel doesn't contain the expected test notetypes",
            )
            viewModel.filter("Test ab")
            advanceUntilIdle()
            assertFilteredNotetypes(viewModel, 4, "Test abc", "Test abz", "Test abxw", "Test abxq")
            viewModel.filter("Test abx")
            advanceUntilIdle()
            assertFilteredNotetypes(viewModel, 2, "Test abxw", "Test abxq")
            viewModel.filter("Test cdf")
            advanceUntilIdle()
            assertFilteredNotetypes(viewModel, 1, "Test cdf")
            // an empty query shows all notetypes
            viewModel.filter("")
            advanceUntilIdle()
            assertFilteredNotetypes(
                viewModel,
                PLATFORM_NOTETYPES_COUNT + 5,
                "Basic",
                "Basic (and reversed card)",
                "Basic (optional reversed card)",
                "Basic (type in the answer)",
                "Cloze",
                "Image Occlusion",
                "Test abc",
                "Test abz",
                "Test abxw",
                "Test abxq",
                "Test cdf",
            )
        }

    private fun assertFilteredNotetypes(
        viewModel: ManageNotetypeViewModel,
        count: Int,
        vararg expectedNames: String,
    ) {
        assertEquals(
            count,
            viewModel.currentState.notetypes.size,
            "ViewModel doesn't contain the expected count of notetypes after filtering",
        )
        expectedNames.forEach { testName ->
            assertTrue(
                viewModel.currentState.notetypes
                    .map { it.name }
                    .contains(testName),
                "ViewModel doesn't contain the expected test note `$testName` after filtering",
            )
        }
    }

    @Test
    fun `At start emits a loading state`() =
        runTest {
            val viewModel = ManageNotetypeViewModel()
            assertTrue(
                viewModel.currentState.isProcessing,
                "ViewModel is not in the loading state at start",
            )
            viewModel.refresh()
        }

    @Test
    fun `Adding new notetypes is present in emitted state after refresh`() =
        runTest {
            val viewModel = ManageNotetypeViewModel()
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(
                PLATFORM_NOTETYPES_COUNT,
                viewModel.currentState.notetypes.size,
                "Unexpected number of notes after refresh at startup",
            )
            addNoteType("Test abc")
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(
                PLATFORM_NOTETYPES_COUNT + 1,
                viewModel.currentState.notetypes.size,
                "Unexpected number of notes after a new notetype is added",
            )
        }

    private fun addNoteType(name: String) = addStandardNoteType(name, arrayOf("Field one"), "{{Field one}}", "{{Field one}}")

    private val ManageNotetypeViewModel.currentState: NotetypesUiState
        get() = uiState.value

    companion object {
        /**
         * The count of the already registered standard notetypes.
         */
        private const val PLATFORM_NOTETYPES_COUNT = 6
    }
}
