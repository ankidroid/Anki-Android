// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.databinding.FragmentStudyOptionsBinding
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class StudyOptionsCommittedCustomStudyCancellationTest : RobolectricTest() {
    @Test
    fun `cancelling after Custom Study selects its deck still refreshes the panel before Start`() {
        val shown = addDeck("Shown", setAsSelected = true)
        val other = addDeck("Other")
        addNoteToDeck(shown)
        addNoteToDeck(other)
        col.decks.select(shown)

        withDeckPicker(deckCount = 0) { picker ->
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == shown
            }
            advanceRobolectricLooper()
            val pane = assertNotNull(picker.fragment)
            col.decks.select(other)
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
                advanceRobolectricLooper()
                assertEquals(other, pane.shownDeckId)

                // Commit selection on the real collection path, but leave its return to IO queued.
                ioScheduler.runCurrent()
                collectionScheduler.runCurrent()
                assertEquals(dialog.viewModel.deckId, collection.decks.selected())
                assertTrue(assertNotNull(dialog.requireDialog().findViewById<View>(R.id.progress_bar)).isVisible)
                dialog.requireDialog().cancel()
                picker.supportFragmentManager.executePendingTransactions()

                repeat(10) {
                    ioScheduler.runCurrent()
                    collectionScheduler.runCurrent()
                    advanceRobolectricLooper()
                }

                val displayed = assertNotNull(pane.shownDeckId)
                assertTrue(FragmentStudyOptionsBinding.bind(pane.requireView()).studyoptionsStart.performClick())
                assertEquals(Reviewer::class.java.name, assertNotNull(shadowOf(picker).nextStartedActivity).component?.className)
                assertEquals(displayed, collection.decks.selected(), "Start must study the deck displayed after cancellation")
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
