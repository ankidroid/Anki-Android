// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.databinding.FragmentStudyOptionsBinding
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class StudyOptionsCustomStudySelectionTest : RobolectricTest() {
    @Test
    fun `cancelling Custom Study opened before a redraw keeps Start aligned with the displayed deck`() {
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
            // Complete the worker refresh while its main-thread render remains queued.
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

            val displayed = assertNotNull(pane.shownDeckId)
            assertEquals(
                col.decks.name(displayed),
                pane
                    .requireView()
                    .findViewById<TextView>(R.id.studyoptions_deck_name)
                    .text
                    .toString(),
            )
            assertTrue(FragmentStudyOptionsBinding.bind(pane.requireView()).studyoptionsStart.performClick())
            assertEquals(Reviewer::class.java.name, assertNotNull(shadowOf(picker).nextStartedActivity).component?.className)
            // Reviewer.getIntent() carries no deck ID: Start studies the collection's current deck.
            assertEquals(displayed, col.decks.selected(), "Start must study the deck still displayed after dismissing Custom Study")
        }
    }
}
