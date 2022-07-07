/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.DeckPickerContextMenu.Companion.instance
import com.ichi2.testutils.assertThrows
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class DeckPickerContextMenuTest : RobolectricTest() {
    @Test
    fun ensure_cannot_be_instantiated_without_arguments() {
        assertThrows<IllegalStateException> { DeckPickerContextMenu(col).deckId }
    }

    @Test
    fun testBrowseCards() {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val deckId = addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(mRecyclerView, 0)

            val browser = shadowOf(this).nextStartedActivity!!
            assertEquals("com.ichi2.anki.CardBrowser", browser.component!!.className)

            assertEquals(deckId, col.decks.selected())
        }
    }

    @Test
    fun testRenameDeck() {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val deckId = addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(mRecyclerView, 1)

            CreateDeckDialog.instance!!.renameDeck("Deck 2")
            assertEquals("Deck 2", col.decks.name(deckId))
        }
    }

    @Test
    fun testCreateSubdeck() {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val deckId = addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(mRecyclerView, 2)

            CreateDeckDialog.instance!!.createSubDeck(deckId, "Deck 2")
            assertThat(col.decks.allNames(), containsInAnyOrder("Default", "Deck 1", "Deck 1::Deck 2"))
        }
    }

    private fun openContextMenuAndSelectItem(contextMenu: RecyclerView, index: Int) {
        contextMenu.postDelayed({
            contextMenu.findViewHolderForAdapterPosition(0)!!
                .itemView.performLongClick()

            val dialogRecyclerView = instance!!.mRecyclerView!!

            dialogRecyclerView.apply {
                scrollToPosition(index)
                postDelayed({
                    findViewHolderForAdapterPosition(index)!!
                        .itemView.performClick()
                }, 1)
            }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        }, 1)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }
}
