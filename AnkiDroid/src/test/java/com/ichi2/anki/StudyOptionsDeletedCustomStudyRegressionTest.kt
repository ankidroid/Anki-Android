// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class StudyOptionsDeletedCustomStudyRegressionTest : RobolectricTest() {
    @Test
    fun `Custom Study for a deleted displayed deck preserves the surviving selection`() {
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
            col.decks.remove(listOf(shown))
            col.decks.select(other)
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
                dialog.dialog?.findViewById<View>(R.id.progress_bar)?.isVisible != true
            }
            dialog.dialog?.cancel()
            picker.supportFragmentManager.executePendingTransactions()
            runBlocking { CollectionManager.withCol { } }
            advanceRobolectricLooper()

            assertEquals(other, col.decks.selected(), "Unavailable Custom Study must not select a deleted deck or Default")
        }
    }
}
