/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.Flag
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.flagCardForNote
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.setFlagFilterSync
import com.ichi2.libanki.Consts.QUEUE_TYPE_MANUALLY_BURIED
import com.ichi2.libanki.Consts.QUEUE_TYPE_NEW
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.mockIt
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class CardBrowserViewModelTest : JvmTest() {
    @Test
    fun `delete search history - Issue 14989`() = runViewModelTest {
        saveSearch("hello", "aa")
        savedSearches().also { searches ->
            assertThat("filters after saving", searches.size, equalTo(1))
            assertThat("filters after saving", searches["hello"], equalTo("aa"))
        }
        removeSavedSearch("hello")
        assertThat("filters should be empty after removing", savedSearches().size, equalTo(0))
    }

    @Test
    fun `change deck in notes mode 15444`() = runViewModelTest {
        val newDeck = addDeck("World")
        selectDefaultDeck()

        for (i in 0 until 5) {
            addNoteUsingBasicAndReversedModel()
        }
        setCardsOrNotes(CardsOrNotes.NOTES)
        waitForSearchResults()

        selectRowsWithPositions(0, 2)

        val allCardIds = queryAllSelectedCardIds()
        assertThat(allCardIds.size, equalTo(4))

        moveSelectedCardsToDeck(newDeck).join()

        for (cardId in allCardIds) {
            assertThat("Deck should be changed", col.getCard(cardId).did, equalTo(newDeck))
        }

        val hasSomeDecksUnchanged = cards.any { row -> row.card.did != newDeck }
        assertThat("some decks are unchanged", hasSomeDecksUnchanged)
    }

    /** 7420  */
    @Test
    fun addCardDeckIsNotSetIfAllDecksSelectedAfterLoad() = runViewModelTest {
        addDeck("NotDefault")

        assertThat("All decks should not be selected", !hasSelectedAllDecks())

        setDeckId(DeckSpinnerSelection.ALL_DECKS_ID)

        assertThat("All decks should be selected", hasSelectedAllDecks())

        val addIntent = CardBrowser.createAddNoteIntent(mockIt(), this)

        IntentAssert.doesNotHaveExtra(addIntent, NoteEditor.EXTRA_DID)
    }

    @Test
    fun filterByFlagDisplaysProperly() = runViewModelTest {
        val cardWithRedFlag = addNoteUsingBasicModel("Card with red flag", "Reverse")
        flagCardForNote(cardWithRedFlag, Flag.RED)

        val cardWithGreenFlag = addNoteUsingBasicModel("Card with green flag", "Reverse")
        flagCardForNote(cardWithGreenFlag, Flag.GREEN)

        val anotherCardWithRedFlag = addNoteUsingBasicModel("Second card with red flag", "Reverse")
        flagCardForNote(anotherCardWithRedFlag, Flag.RED)

        setFlagFilterSync(Flag.RED)

        assertThat("Flagged cards should be returned", rowCount, equalTo(2))
    }

    @Test
    fun `toggle bury - single selection`() = runViewModelTest(notes = 1) {
        assertThat("bury with no cards selected does nothing", toggleBury(), nullValue())

        selectRowAtPosition(0)

        // bury & unbury
        toggleBury().also {
            assertNotNull(it)
            assertThat("toggle bury initially buries", it.wasBuried)
            assertThat("1 card is buried", it.count, equalTo(1))
        }
        toggleBury().also {
            assertNotNull(it)
            assertThat("toggle bury unburied on second press", !it.wasBuried)
            assertThat("1 card is unburied", it.count, equalTo(1))
        }
    }

    @Test
    fun `toggle bury - mixed selection`() = runViewModelTest(notes = 2) {
        selectRowAtPosition(0)
        toggleBury()
        selectRowAtPosition(1)

        assertThat(selectedRowCount(), equalTo(2))

        // 1 row is buried and 1 is unburied
        toggleBury().also {
            assertNotNull(it)
            assertThat("toggle bury with mixed selection buried", it.wasBuried)
            assertThat("2 cards are affected", it.count, equalTo(2))
        }

        assertThat(selectedRowCount(), equalTo(2))

        toggleBury().also {
            assertNotNull(it)
            assertThat("toggle bury with all buried performs 'unbury'", !it.wasBuried)
            assertThat("2 cards are affected", it.count, equalTo(2))
        }

        toggleBury().also {
            assertNotNull(it)
            assertThat("toggle bury with all unburied performs 'bury'", it.wasBuried)
            assertThat("2 cards are affected", it.count, equalTo(2))
        }

        assertThat(selectedRowCount(), equalTo(2))
    }

    @Test
    fun `toggle bury - queue changes`() = runViewModelTest(notes = 1) {
        selectRowAtPosition(0)
        fun getQueue() = col.getCard(selectedRowIds.single()).queue

        assertThat("initial queue = NEW", getQueue(), equalTo(QUEUE_TYPE_NEW))

        toggleBury()

        assertThat("bury: queue -> MANUALLY_BURIED", getQueue(), equalTo(QUEUE_TYPE_MANUALLY_BURIED))

        toggleBury()

        assertThat("unbury: queue -> NEW", getQueue(), equalTo(QUEUE_TYPE_NEW))
    }

    private fun runViewModelTest(notes: Int = 0, manualInit: Boolean = true, testBody: suspend CardBrowserViewModel.() -> Unit) = runTest {
        for (i in 0 until notes) {
            addNoteUsingBasicModel()
        }
        val viewModel = CardBrowserViewModel(
            lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
            cacheDir = createTransientDirectory(),
            preferences = AnkiDroidApp.sharedPreferencesProvider,
            manualInit = manualInit
        )
        // makes ignoreValuesFromViewModelLaunch work under test
        if (manualInit) {
            viewModel.manualInit()
        }
        testBody(viewModel)
    }
}

@Suppress("SameParameterValue")
private fun CardBrowserViewModel.selectRowsWithPositions(vararg positions: Int) {
    for (pos in positions) {
        selectRowAtPosition(pos)
    }
}

private suspend fun CardBrowserViewModel.waitForSearchResults() {
    searchJob?.join()
}

private fun CardBrowserViewModel.hasSelectedAllDecks() = lastDeckId == DeckSpinnerSelection.ALL_DECKS_ID
