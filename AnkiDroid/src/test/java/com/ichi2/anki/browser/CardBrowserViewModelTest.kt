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
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.Flag
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.browser.CardBrowserLaunchOptions.DeepLink
import com.ichi2.anki.browser.CardBrowserLaunchOptions.SystemContextMenu
import com.ichi2.anki.flagCardForNote
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType.EASE
import com.ichi2.anki.model.SortType.NO_SORTING
import com.ichi2.anki.model.SortType.SORT_FIELD
import com.ichi2.anki.setFlagFilterSync
import com.ichi2.libanki.Consts.QUEUE_TYPE_MANUALLY_BURIED
import com.ichi2.libanki.Consts.QUEUE_TYPE_NEW
import com.ichi2.libanki.DeckId
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.mockIt
import kotlinx.coroutines.flow.first
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
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

    @Test
    fun `default init`() = runTest {
        viewModel().apply {
            assertThat(searchTerms, equalTo(""))
        }
    }

    @Test
    fun `Card Browser menu init`() = runTest {
        viewModel(intent = SystemContextMenu("Hello")).apply {
            assertThat(searchTerms, equalTo("Hello"))
        }
    }

    @Test
    fun `Deep Link init`() = runTest {
        viewModel(intent = DeepLink("Hello")).apply {
            assertThat(searchTerms, equalTo("Hello"))
        }
    }

    @Test
    fun `sort order from notes is selected - 16514`() {
        col.config.set("sortType", "noteCrt")
        col.config.set("noteSortType", "_field_Frequency")
        with(col) { CardsOrNotes.NOTES.saveToCollection() }

        runViewModelTest(notes = 1) {
            assertThat("1 row returned", rowCount, equalTo(1))
        }
    }

    fun `selected rows are refreshed`() = runViewModelTest(notes = 2) {
        flowOfSelectedRows.test {
            // initially, flowOfSelectedRows should not have emitted anything
            expectNoEvents()

            selectAll()
            assertThat("initial selection", awaitItem().size, equalTo(2))

            selectNone()
            assertThat("deselected all", awaitItem().size, equalTo(0))

            toggleRowSelectionAtPosition(0)
            assertThat("selected row", awaitItem().size, equalTo(1))

            toggleRowSelectionAtPosition(0)
            assertThat("deselected rows", awaitItem().size, equalTo(0))

            selectRowAtPosition(0)
            assertThat("select rows explicitly", awaitItem().size, equalTo(1))

            selectRowAtPosition(0)
            expectNoEvents()

            selectRowsBetweenPositions(0, 1)
            assertThat("select rows between positions", awaitItem().size, equalTo(2))

            selectRowsBetweenPositions(0, 1)
            expectNoEvents()
        }
    }

    @Test
    fun `selected card and note ids`() {
        val notes = List(2) { addNoteUsingBasicAndReversedModel() }

        val nids = notes.map { it.id }.toTypedArray()
        val cids = notes.flatMap { it.cids() }.toTypedArray()

        runViewModelTest {
            setCardsOrNotes(CardsOrNotes.CARDS).join()
            selectAll()
            assertThat("cards: rowCount", rowCount, equalTo(4))
            assertThat("cards: cids", queryAllSelectedCardIds(), containsInAnyOrder(*cids))
            assertThat("cards: nids", queryAllSelectedNoteIds(), containsInAnyOrder(*nids))

            selectNone()

            setCardsOrNotes(CardsOrNotes.NOTES).join()
            selectAll()
            assertThat("notes: rowCount", rowCount, equalTo(2))
            assertThat("notes: cids", queryAllSelectedCardIds(), containsInAnyOrder(*cids))
            assertThat("notes: nids", queryAllSelectedNoteIds(), containsInAnyOrder(*nids))
        }
    }

    @Test
    fun `changing column index 1`() = runViewModelTest {
        flowOfColumnIndex1.test {
            ignoreEventsDuringViewModelInit()

            assertThat("default column1Index value", column1Index, equalTo(0))

            setColumn1Index(1)

            assertThat("flowOfColumnIndex1", awaitItem(), equalTo(1))
            assertThat("column1Index", column1Index, equalTo(1))

            // expect no change if the value is selected again
            setColumn1Index(1)
            expectNoEvents()
        }
    }

    @Test
    fun `changing column index 2`() = runViewModelTest {
        flowOfColumnIndex2.test {
            ignoreEventsDuringViewModelInit()

            assertThat("default column2Index value", column2Index, equalTo(0))

            setColumn2Index(1)

            assertThat("flowOfColumnIndex2", awaitItem(), equalTo(1))
            assertThat("column2Index", column2Index, equalTo(1))

            // expect no change if the value is selected again
            setColumn2Index(1)
            expectNoEvents()
        }
    }

    @Test
    fun `change card order to NO_SORTING is a no-op if done twice`() = runViewModelTest {
        flowOfSearchState.test {
            ignoreEventsDuringViewModelInit()
            assertThat("initial order", order, equalTo(SORT_FIELD))
            assertThat("initial direction", !orderAsc)

            // changing the order performs a search & changes order
            changeCardOrder(NO_SORTING)
            expectMostRecentItem()
            assertThat("order changed", order, equalTo(NO_SORTING))
            assertThat("changed direction", !orderAsc)

            waitForSearchResults()

            // pressing 'no sorting' again is a no-op
            changeCardOrder(NO_SORTING)
            expectNoEvents()
            assertThat("order unchanged", order, equalTo(NO_SORTING))
            assertThat("unchanged direction", !orderAsc)
        }
    }

    @Test
    fun `change direction of results`() = runViewModelTest {
        flowOfSearchState.test {
            ignoreEventsDuringViewModelInit()
            assertThat("initial order", order, equalTo(SORT_FIELD))
            assertThat("initial direction", !orderAsc)

            // changing the order performs a search & changes order
            changeCardOrder(EASE)
            expectMostRecentItem()
            assertThat("order changed", order, equalTo(EASE))
            assertThat("changed direction is the default", !orderAsc)

            waitForSearchResults()

            // pressing 'ease' again changes direction
            changeCardOrder(EASE)
            expectMostRecentItem()
            assertThat("order unchanged", order, equalTo(EASE))
            assertThat("direction is changed", orderAsc)
        }
    }

    private fun runViewModelTest(notes: Int = 0, manualInit: Boolean = true, testBody: suspend CardBrowserViewModel.() -> Unit) = runTest {
        for (i in 0 until notes) {
            addNoteUsingBasicModel()
        }
        val viewModel = CardBrowserViewModel(
            lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
            cacheDir = createTransientDirectory(),
            options = null,
            preferences = AnkiDroidApp.sharedPreferencesProvider,
            manualInit = manualInit
        )
        // makes ignoreValuesFromViewModelLaunch work under test
        if (manualInit) {
            viewModel.manualInit()
        }
        testBody(viewModel)
    }

    companion object {
        private suspend fun viewModel(
            lastDeckId: DeckId? = null,
            intent: CardBrowserLaunchOptions? = null,
            mode: CardsOrNotes = CardsOrNotes.CARDS
        ): CardBrowserViewModel {
            val lastDeckIdRepository = object : LastDeckIdRepository {
                override var lastDeckId: DeckId? = lastDeckId
            }

            // default is CARDS, do nothing in this case
            if (mode == CardsOrNotes.NOTES) {
                CollectionManager.withCol { mode.saveToCollection() }
            }

            val cache = File(createTempDirectory().pathString)
            return CardBrowserViewModel(lastDeckIdRepository, cache, intent, AnkiDroidApp.sharedPreferencesProvider).apply {
                invokeInitialSearch()
            }
        }
    }
}

@Suppress("SameParameterValue")
private fun CardBrowserViewModel.selectRowsWithPositions(vararg positions: Int) {
    for (pos in positions) {
        selectRowAtPosition(pos)
    }
}

/**
 * Helper for testing flows:
 *
 * A MutableStateFlow can either emit a value or not emit a value
 * depending on whether a consumer subscribes before or after the task launched
 * from init is completed
 */
private fun <T> TurbineTestContext<T>.ignoreEventsDuringViewModelInit() {
    try {
        expectMostRecentItem()
    } catch (e: AssertionError) {
        // explicitly ignored: no items
    }
}

private suspend fun CardBrowserViewModel.waitForSearchResults() {
    searchJob?.join()
}

private fun CardBrowserViewModel.hasSelectedAllDecks() = lastDeckId == DeckSpinnerSelection.ALL_DECKS_ID

private suspend fun CardBrowserViewModel.waitForInit() {
    this.flowOfInitCompleted.first { initCompleted -> initCompleted }
}

internal suspend fun CardBrowserViewModel.invokeInitialSearch() {
    Timber.d("waiting for init")
    waitForInit()
    Timber.d("init completed")
    // For legacy reasons, we need to know the number of cards to render when performing a search
    // This will be removed once we handle #11889
    // numberOfCardsToRenderFlow.emit(1)
    Timber.v("initial search completed")
}
