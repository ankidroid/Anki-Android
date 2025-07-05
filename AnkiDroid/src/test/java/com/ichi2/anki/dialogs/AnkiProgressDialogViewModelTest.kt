/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2025 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
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

package com.ichi2.anki.dialogs

import androidx.lifecycle.SavedStateHandle
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Unit tests for [AnkiProgressDialogViewModel] */
@ExperimentalCoroutinesApi
class AnkiProgressDialogViewModelTest {
    private lateinit var viewModel: AnkiProgressDialogViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private val testDispatcher = StandardTestDispatcher()

    private var cancelCallbackCalled = false
    private val testCancelListener: () -> Unit = { cancelCallbackCalled = true }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle()
        viewModel = AnkiProgressDialogViewModel(savedStateHandle)
        cancelCallbackCalled = false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default values`() =
        runTest {
            // Assert default values
            assertEquals("", viewModel.message.value)
            assertNull(viewModel.progress.value) // No progress set initially
            assertFalse(viewModel.cancelableViaBackButton.value)
            assertNull(viewModel.cancelButtonText.value)
            assertFalse(viewModel.hasCancelListener())
        }

    @Test
    fun `setup properly initializes values`() =
        runTest {
            val initialMessage = "Initial Test Message"
            val cancelable = true
            val buttonText = "Cancel"

            viewModel.setup(
                message = initialMessage,
                cancelableViaBackButton = cancelable,
                cancelButtonText = buttonText,
                onCancelListener = testCancelListener,
            )

            // Verify all values were set correctly
            assertEquals(initialMessage, viewModel.message.value)
            assertEquals(cancelable, viewModel.cancelableViaBackButton.value)
            assertEquals(buttonText, viewModel.cancelButtonText.value)
            assertTrue(viewModel.hasCancelListener())
        }

    @Test
    fun `updateMessage updates valid messages`() =
        runTest {
            val initialMessage = "Initial message"
            val newMessage = "Updated message"

            // Start with initial message
            viewModel.updateMessage(initialMessage)
            assertEquals(initialMessage, viewModel.message.value)
            assertEquals(initialMessage, savedStateHandle.get<String>("message"))

            // Update with new message
            viewModel.updateMessage(newMessage)
            assertEquals(newMessage, viewModel.message.value)
            assertEquals(newMessage, savedStateHandle.get<String>("message"))
        }

    @Test
    fun `updateMessage handles empty messages`() =
        runTest {
            val initialMessage = "Initial message"

            // Set initial message
            viewModel.updateMessage(initialMessage)
            assertEquals(initialMessage, viewModel.message.value)

            // Try updating with empty message - should keep initial message
            viewModel.updateMessage("")
            assertEquals(initialMessage, viewModel.message.value)

            // Try updating with blank message (spaces) - should keep initial message
            viewModel.updateMessage("   ")
            assertEquals(initialMessage, viewModel.message.value)
        }

    @Test
    fun `updateProgress sets determinate progress values`() =
        runTest {
            // Initial state should be null progress (indeterminate)
            assertNull(viewModel.progress.value)

            // Update progress
            viewModel.updateProgress(30, 100)

            // Should now have a Progress object with correct values
            val progress = viewModel.progress.value
            assertNotNull(progress)
            assertEquals(30, progress!!.currentProgress)
            assertEquals(100, progress.maxProgress)

            // Progress should be saved to savedStateHandle
            val savedProgress = savedStateHandle.get<AnkiProgressDialogViewModel.Progress>("progress")
            assertNotNull(savedProgress)
            assertEquals(30, savedProgress!!.currentProgress)
            assertEquals(100, savedProgress.maxProgress)
        }

    @Test
    fun `cancel method emits cancel event and calls listener`() =
        runTest {
            // Set up cancel listener
            viewModel.setOnCancelListener(testCancelListener)

            // Initially not called
            assertFalse(cancelCallbackCalled)

            // Trigger cancel
            viewModel.cancel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify callback was called
            assertTrue(cancelCallbackCalled)
        }

    @Test
    fun `state is preserved through SavedStateHandle`() =
        runTest {
            // Set initial state
            viewModel.setup(
                message = "Test Message",
                cancelableViaBackButton = true,
                cancelButtonText = "OK",
                onCancelListener = testCancelListener,
            )
            viewModel.updateProgress(50, 200)

            // Create new ViewModel with same SavedStateHandle to simulate recreation
            val restoredViewModel = AnkiProgressDialogViewModel(savedStateHandle)

            // Verify state is restored
            assertEquals("Test Message", restoredViewModel.message.value)
            assertEquals(true, restoredViewModel.cancelableViaBackButton.value)
            assertEquals("OK", restoredViewModel.cancelButtonText.value)

            // Verify progress state is restored
            val progress = restoredViewModel.progress.value
            assertNotNull(progress)
            assertEquals(50, progress!!.currentProgress)
            assertEquals(200, progress.maxProgress)

            // Note: The cancel listener can't be persisted through SavedStateHandle
            // so we have separate handling for that in the Fragment
        }

    @Test
    fun `setCancelable updates cancelable state`() =
        runTest {
            // Initial state
            assertFalse(viewModel.cancelableViaBackButton.value)

            // Update cancelable
            viewModel.setCancelable(true)

            // Verify updated
            assertTrue(viewModel.cancelableViaBackButton.value)
            assertEquals(true, savedStateHandle.get<Boolean>("cancelableViaBackButton"))
        }

    @Test
    fun `setCancelButtonText updates button text`() =
        runTest {
            // Initial state
            assertNull(viewModel.cancelButtonText.value)

            // Update button text
            viewModel.setCancelButtonText("Cancel")

            // Verify updated
            assertEquals("Cancel", viewModel.cancelButtonText.value)
            assertEquals("Cancel", savedStateHandle.get<String>("cancelButtonText"))
        }

    @Test
    fun `updateMessage with empty string preserves non-empty initial message`() =
        runTest {
            // Set up a non-empty initial message
            val initialMessage = "Initial message"
            viewModel.updateMessage(initialMessage)

            // Update with an empty string
            viewModel.updateMessage("")

            // The initial message should be preserved
            assertEquals(initialMessage, viewModel.message.value)
        }

    @Test
    fun `setup preserves message in SavedStateHandle`() =
        runTest {
            val testMessage = "Test message"

            viewModel.setup(message = testMessage)

            // Message should be in SavedStateHandle
            assertEquals(testMessage, savedStateHandle.get<String>("message"))
        }

    @Test
    fun `multiple rapid message updates are processed correctly`() =
        runTest {
            // Set multiple messages in quick succession
            for (i in 1..5) {
                viewModel.updateMessage("Message $i")
            }

            // The final message should be the one that's displayed
            assertEquals("Message 5", viewModel.message.value)
        }
}
