// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.widgets.DeckAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class StudyOptionsCustomStudyFocusRegressionTest : RobolectricTest() {
    @Test
    fun `cancelling Custom Study keeps the deck list highlight aligned with the panel`() {
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
            val adapter = picker.deckPickerBinding.decks.adapter as DeckAdapter
            // Refresh the list selection without rendering the panel's next state.
            col.decks.select(other)
            picker.viewModel.updateDeckFilter("o")
            advanceRobolectricLooperUntil {
                adapter.currentList.singleOrNull { it.isSelected }?.did == other
            }
            runBlocking { withContext(Dispatchers.Default) { pane.viewModel.refreshData() } }
            assertEquals(other, pane.viewModel.selectedDeckId)
            assertEquals(shown, pane.shownDeckId)

            pane.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_custom_study, 0, "Custom Study"))
            picker.supportFragmentManager.executePendingTransactions()
            val dialog =
                picker.supportFragmentManager.fragments
                    .filterIsInstance<CustomStudyDialog>()
                    .single()
            advanceRobolectricLooperUntil {
                dialog
                    .requireDialog()
                    .findViewById<ViewGroup>(R.id.menu_items)
                    ?.childCount
                    ?.let { it > 0 } == true
            }
            dialog.requireDialog().cancel()
            picker.supportFragmentManager.executePendingTransactions()
            runBlocking { CollectionManager.withCol { } }
            advanceRobolectricLooper()

            val selected = col.decks.selected()
            assertEquals(selected, pane.shownDeckId)
            // List diffs and host refreshes may still be running on worker threads.
            val waitStarted = TimeSource.Monotonic.markNow()
            while (adapter.currentList.single { it.isSelected }.did != selected && waitStarted.elapsedNow() < 5.seconds) {
                Thread.sleep(10)
                advanceRobolectricLooper()
            }
            assertEquals(
                selected,
                adapter.currentList.single { it.isSelected }.did,
                "The deck list must highlight the deck shown in the panel after Custom Study closes",
            )
        }
    }
}
