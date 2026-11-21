// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class StudyOptionsCustomStudyLoadingCancellationTest : RobolectricTest() {
    @Test
    fun `cancelling loading Custom Study before queued selection preserves redo`() =
        assertLoadingCancellation(runSelectionBeforeCancel = false)

    @Test
    fun `cancelling loading Custom Study before defaults start preserves redo`() =
        assertLoadingCancellation(runSelectionBeforeCancel = true)

    private fun assertLoadingCancellation(runSelectionBeforeCancel: Boolean) {
        val shown = addDeck("Shown", setAsSelected = true)
        val other = addDeck("Other")
        addNoteToDeck(shown)
        addNoteToDeck(other)
        col.decks.select(shown)

        withDeckPicker(deckCount = 0) { picker ->
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == shown
            }
            val pane = assertNotNull(picker.fragment)
            col.decks.select(other)
            addBasicNote("Temporary", "Undo this note")
            col.undo()
            val undoStatus = col.undoStatus()
            assertNotNull(undoStatus.redo)
            runBlocking { withContext(Dispatchers.Default) { pane.viewModel.refreshData() } }
            assertEquals(other, pane.viewModel.selectedDeckId)
            assertEquals(shown, pane.shownDeckId)

            val collection = col
            val ioScheduler = TestCoroutineScheduler()
            val collectionScheduler = TestCoroutineScheduler()
            val previousIo = ioDispatcher
            ioDispatcher = StandardTestDispatcher(ioScheduler)
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
            try {
                pane.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_custom_study, 0, "Custom Study"))
                picker.supportFragmentManager.executePendingTransactions()
                val dialog =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<CustomStudyDialog>()
                        .single()
                assertTrue(assertNotNull(dialog.requireDialog().findViewById<View>(R.id.progress_bar)).isVisible)
                advanceRobolectricLooper()
                assertEquals(other, pane.shownDeckId)
                if (runSelectionBeforeCancel) {
                    repeat(10) {
                        collectionScheduler.runCurrent()
                        advanceRobolectricLooper()
                    }
                    assertTrue(assertNotNull(dialog.requireDialog().findViewById<View>(R.id.progress_bar)).isVisible)
                }
                dialog.requireDialog().cancel()
                picker.supportFragmentManager.executePendingTransactions()
                advanceRobolectricLooper()

                repeat(10) {
                    ioScheduler.runCurrent()
                    collectionScheduler.runCurrent()
                    advanceRobolectricLooper()
                }

                assertEquals(undoStatus, collection.undoStatus(), "Cancelling a loading menu must not discard redo or add Select Deck")
                assertEquals(other, collection.decks.selected())
            } finally {
                repeat(10) {
                    ioScheduler.runCurrent()
                    collectionScheduler.runCurrent()
                    advanceRobolectricLooper()
                }
                CollectionManager.setTestDispatcher(previousQueue)
                ioDispatcher = previousIo
            }
        }
    }
}
