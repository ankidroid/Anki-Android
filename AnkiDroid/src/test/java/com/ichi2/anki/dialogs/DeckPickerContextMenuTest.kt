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
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_PINNED
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.internal.rtl.RtlTextView
import com.ichi2.anki.*
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.BackupManagerTestUtilities.setupSpaceForBackup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerContextMenuTest : RobolectricTest() {
    @Before
    fun before() {
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        setupSpaceForBackup(this.targetContext)
    }

    @Test
    fun ensure_cannot_be_instantiated_without_arguments() {
        assertFailsWith<IllegalStateException> { DeckPickerContextMenu(col).deckId }
    }

    @Test
    fun addCards() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val models = col.models
            val didA = addDeck("Deck 1")
            updateDeckList()
            col.decks.select(didA)
            val basic = models.byName(AnkiDroidApp.appResources.getString(R.string.basic_model_name))
            basic!!.put("did", didA)
            addNoteUsingBasicModel("Front", "Back")
            assertEquals(1, visibleDeckCount)
            openContextMenuAndSelectItem(recyclerView, 0)
            assertEquals(1, col.cardCount(didA))
        }
    }

    @Test
    fun testBrowseCards() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val deckId = addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 1)

            val browser = shadowOf(this).nextStartedActivity!!
            assertEquals("com.ichi2.anki.CardBrowser", browser.component!!.className)

            assertEquals(deckId, col.decks.selected())
        }
    }

    @Test
    fun testRenameDeck() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 2)

            assertDialogTitleEquals("Rename deck")
        }
    }

    @Test
    fun testCreateSubdeck() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 3)

            assertDialogTitleEquals("Create subdeck")
        }
    }

    @Test
    fun testShowDeckOptions() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 4)
        }
    }

    @Test
    fun testDeleteDeck() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val deckId = addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 8)

            assertThat(col.decks.allNamesAndIds().map { it.id }, not(containsInAnyOrder(deckId)))
        }
    }

    @Test
    fun testCreateShortcut() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 7)

            assertEquals(
                "Deck 1",
                ShortcutManagerCompat.getShortcuts(this, FLAG_MATCH_PINNED).first().shortLabel
            )
        }
    }

    @Test
    fun testUnbury() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            TimeManager.reset()
            // stop 'next day' code running, which calls 'unbury'
            updateDeckList()

            val deckId = addDeck("Deck 1")
            col.models.byName("Basic")!!.put("did", deckId)
            val card = addNoteUsingBasicModel("front", "back").firstCard()
            col.sched.buryCards(longArrayOf(card.id))
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            assertTrue(col.sched.haveBuried(deckId), "Deck should have buried cards")

            openContextMenuAndSelectItem(recyclerView, 7)

            assertFalse(col.sched.haveBuried(deckId))
        }
    }

    @Test
    fun testCustomStudy() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 5)

            assertDialogTitleEquals("Custom study")
        }
    }

    @Test
    fun testExportDeck() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            addDeck("Deck 1")
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 6)

            assertDialogTitleEquals("Export")
        }
    }

    @Test
    fun testDynRebuildAndEmpty() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val cardIds = (0..3)
                .map { addNoteUsingBasicModel("$it", "").firstCard().id }
            assertTrue(allCardsInSameDeck(cardIds, 1))
            val deckId = addDynamicDeck("Deck 1")
            col.sched.rebuildDyn(deckId)
            assertTrue(allCardsInSameDeck(cardIds, deckId))
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            openContextMenuAndSelectItem(recyclerView, 3) // Empty

            assertTrue(allCardsInSameDeck(cardIds, 1))

            openContextMenuAndSelectItem(recyclerView, 2) // Rebuild

            assertTrue(allCardsInSameDeck(cardIds, deckId))
        }
    }

    private fun assertDialogTitleEquals(expectedTitle: String) {
        val actualTitle =
            (ShadowDialog.getLatestDialog() as MaterialDialog)
                .view
                .findViewById<RtlTextView>(com.afollestad.materialdialogs.R.id.md_text_title)
                ?.text
        Timber.d("titles = \"$actualTitle\", \"$expectedTitle\"")
        assertEquals(expectedTitle, "$actualTitle")
    }

    private fun allCardsInSameDeck(cardIds: List<Long>, deckId: Long): Boolean =
        cardIds.all { col.getCard(it).did == deckId }

    private fun openContextMenuAndSelectItem(contextMenu: RecyclerView, index: Int) {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        contextMenu.postDelayed({
            contextMenu.findViewHolderForAdapterPosition(0)!!
                .itemView.performLongClick()

            val dialogRecyclerView = (ShadowDialog.getLatestDialog() as MaterialDialog?)!!
                .view.findViewById<RecyclerView>(com.afollestad.materialdialogs.R.id.md_recyclerview_content)

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
