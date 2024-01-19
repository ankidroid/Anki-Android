/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ListView
import android.widget.Spinner
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser.CardCache
import com.ichi2.anki.DeckSpinnerSelection.Companion.ALL_DECKS_ID
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel.Companion.DISPLAY_COLUMN_1_KEY
import com.ichi2.anki.browser.CardBrowserViewModel.Companion.DISPLAY_COLUMN_2_KEY
import com.ichi2.anki.introduction.hasCollectionStoragePermissions
import com.ichi2.anki.model.CardsOrNotes.*
import com.ichi2.anki.model.SortType
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.AnkiActivityUtils.getDialogFragment
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrowSuspend
import com.ichi2.testutils.Flaky
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.OS
import com.ichi2.testutils.getSharedPrefs
import com.ichi2.ui.FixedTextView
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.Locale
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class CardBrowserTest : RobolectricTest() {
    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithNoCards() {
        val browser = browserWithNoNewCards
        assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
    }

    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithCards() {
        val browser = browserWithMultipleNotes
        assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
    }

    @Test
    fun selectAllIsNotVisibleWhenNoCardsInDeck() {
        val browser = browserWithNoNewCards
        assertThat(browser.isShowingSelectAll, equalTo(false))
    }

    @Test
    fun selectAllIsVisibleWhenCardsInDeck() {
        val browser = browserWithMultipleNotes
        assertThat(browser.viewModel.rowCount, greaterThan(0))
        assertThat(browser.isShowingSelectAll, equalTo(true))
    }

    @Test
    fun selectAllIsNotVisibleOnceCalled() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        assertThat(browser.isShowingSelectAll, equalTo(false))
    }

    @Test
    fun selectNoneIsVisibleOnceSelectAllCalled() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        assertThat(browser.isShowingSelectNone, equalTo(true))
    }

    @Test
    fun selectNoneIsVisibleWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        assertThat(browser.isShowingSelectNone, equalTo(true))
    }

    @Test
    fun selectAllIsVisibleWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        assertThat(browser.isShowingSelectAll, equalTo(true))
    }

    @Test
    @Flaky(os = OS.WINDOWS, "Index 0 out of bounds for length 0")
    fun browserIsInMultiSelectModeWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        assertThat(browser.viewModel.isInMultiSelectMode, equalTo(true))
    }

    @Test
    @Flaky(os = OS.WINDOWS, "Expected `true`, got `false`")
    fun browserIsInMultiSelectModeWhenSelectingAll() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        assertThat(browser.viewModel.isInMultiSelectMode, equalTo(true))
    }

    @Test
    fun browserIsNotInMultiSelectModeWhenSelectingNone() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        selectMenuItem(browser, R.id.action_select_none)
        assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
    }

    @Test
    fun browserDoesNotFailWhenSelectingANonExistingCard() = runTest {
        // #5900
        val browser = getBrowserWithNotes(6)
        // Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
        deleteCardAtPosition(browser, 0)
        assertDoesNotThrowSuspend { browser.rerenderAllCards() }
        assertThat(browser.viewModel.rowCount, equalTo(5))
    }

    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    fun selectionsAreCorrectWhenNonExistingCardIsRemoved() = runTest {
        val browser = getBrowserWithNotes(7)
        browser.selectRowsWithPositions(1, 3, 5, 6)
        deleteCardAtPosition(browser, 2) // delete non-selected
        deleteCardAtPosition(browser, 3) // delete selected, ensure it's not still selected

        // ACT
        browser.rerenderAllCards()
        // ASSERT
        assertThat(browser.viewModel.rowCount, equalTo(6L))
        assertThat(
            "A checked card should have been removed",
            browser.viewModel.selectedRowCount(),
            equalTo(3)
        )
        assertThat(
            "Checked card before should not have changed",
            browser.hasSelectedCardAtPosition(1),
            equalTo(true)
        )
        assertThat(
            "Checked card after should have changed by 2 places",
            browser.hasSelectedCardAtPosition(3),
            equalTo(true)
        )
        assertThat(
            "Checked card after should have changed by 2 places",
            browser.hasSelectedCardAtPosition(4),
            equalTo(true)
        )
    }

    @Test
    fun canChangeDeckToRegularDeck() = runTest {
        addDeck("Hello")
        val b = getBrowserWithNotes(5)

        val decks = b.validDecksForChangeDeck

        for (d in decks) {
            if (d.name == "Hello") {
                return@runTest
            }
        }
        fail("Added deck was not found in the Card Browser")
    }

    @Test
    fun cannotChangeDeckToDynamicDeck() = runTest {
        // 5932 - dynamic decks are meant to have cards added to them through "Rebuild".
        addDynamicDeck("World")
        val b = getBrowserWithNotes(5)

        val decks = b.validDecksForChangeDeck

        if (decks.any { it.name == "World" }) {
            fail("Dynamic decks should not be transferred to by the browser.")
        }
    }

    @Test
    fun changeDeckIntegrationTestDynamicAndNon() = runTest {
        addDeck("Hello")
        addDynamicDeck("World")

        val validNames = hashSetOf("Default", "Hello")

        val b = getBrowserWithNotes(5)

        val decks = b.validDecksForChangeDeck
        for (d in decks) {
            assertThat(validNames, hasItem(d.name))
        }
        assertThat("Additional unexpected decks were present", decks.size, equalTo(2))
    }

    @Test
    fun moveToNonDynamicDeckWorks() {
        addDeck("Foo")
        addDynamicDeck("Bar")
        val deckIdToChangeTo = addDeck("Hello")
        addDeck("ZZ")
        selectDefaultDeck()
        val b = getBrowserWithNotes(5)
        b.selectRowsWithPositions(0, 2)

        val cardIds = b.viewModel.selectedCardIds

        for (cardId in cardIds) {
            assertThat(
                "Deck should have been changed yet",
                col.getCard(cardId).did,
                not(deckIdToChangeTo)
            )
        }

        // act
        assertDoesNotThrow { b.moveSelectedCardsToDeck(deckIdToChangeTo) }

        // assert
        for (cardId in cardIds) {
            assertThat("Deck should be changed", col.getCard(cardId).did, equalTo(deckIdToChangeTo))
        }
    }

    @Test
    fun changeDeckViaTaskIsHandledCorrectly() = runTest {
        val dynId = addDynamicDeck("World")
        selectDefaultDeck()
        val b = getBrowserWithNotes(5)
        b.selectRowsWithPositions(0, 2)

        val cardIds = b.viewModel.selectedCardIds

        b.moveSelectedCardsToDeck(dynId).join()

        for (cardId in cardIds) {
            assertThat("Deck should not be changed", col.getCard(cardId).did, not(dynId))
        }
    }

    @Test // see #13391
    fun newlyCreatedDeckIsShownAsOptionInBrowser() = runTest {
        val deckOneId = addDeck("one")
        val browser = browserWithNoNewCards
        assertEquals(2, browser.validDecksForChangeDeck.size) // 1 added + default deck
        assertEquals(1, browser.validDecksForChangeDeck.count { it.id == deckOneId })
        val deckTwoId = addDeck("two")
        assertEquals(3, browser.validDecksForChangeDeck.size) // 2 added + default deck
        assertEquals(1, browser.validDecksForChangeDeck.count { it.id == deckOneId })
        assertEquals(1, browser.validDecksForChangeDeck.count { it.id == deckTwoId })
    }

    @Test
    @Flaky(os = OS.ALL, message = "Fails mostly on Mac and occasionally Windows")
    fun flagsAreShownInBigDecksTest() = runTest {
        val numberOfNotes = 75
        val cardBrowser = getBrowserWithNotes(numberOfNotes)

        // select a random card
        val random = Random(1)
        val cardPosition = random.nextInt(numberOfNotes)
        assumeThat("card position to select is 60", cardPosition, equalTo(60))
        cardBrowser.selectRowsWithPositions(cardPosition)
        assumeTrue(
            "card at position 60 is selected",
            cardBrowser.hasSelectedCardAtPosition(cardPosition)
        )

        // flag the selected card
        cardBrowser.updateSelectedCardsFlag(Flag.RED)
        // check if card is red
        assertThat(
            "Card should be flagged",
            getCheckedCard(cardBrowser).card.userFlag(),
            equalTo(Flag.RED.code)
        )

        // unflag the selected card
        cardBrowser.updateSelectedCardsFlag(Flag.NONE)
        // check if card flag is removed
        assertThat(
            "Card flag should be removed",
            getCheckedCard(cardBrowser).card.userFlag(),
            equalTo(Flag.NONE.code)
        )

        // deselect and select all cards
        cardBrowser.viewModel.selectNone()
        cardBrowser.viewModel.selectAll()
        // flag all the cards as Green
        cardBrowser.updateSelectedCardsFlag(Flag.GREEN)
        // check if all card flags turned green
        assertThat(
            "All cards should be flagged",
            cardBrowser.viewModel.allCardIds
                .map { cardId -> getCardFlagAfterFlagChangeDone(cardBrowser, cardId) }
                .all { flag1 -> flag1 == Flag.GREEN.code }
        )
    }

    @Test
    fun flagValueIsShownOnCard() {
        val n = addNoteUsingBasicModel("1", "back")
        flagCardForNote(n, Flag.RED)

        val cardId = n.cids()[0]

        val b = browserWithNoNewCards

        val actualFlag = getCardFlagAfterFlagChangeDone(b, cardId)

        assertThat("The card flag value should be reflected in the UI", actualFlag, equalTo(1))
    }

    private fun getCardFlagAfterFlagChangeDone(cardBrowser: CardBrowser, cardId: CardId): Int {
        return cardBrowser.getPropertiesForCardId(cardId).card.userFlag()
    }

    @Test
    fun startupFromCardBrowserActionItemShouldEndActivityIfNoPermissions() {
        val inputIntent = Intent("android.intent.action.PROCESS_TEXT")

        val mockedMethod = AnkiActivity::hasCollectionStoragePermissions
        try {
            mockkStatic(mockedMethod)

            every { any<AnkiActivity>().hasCollectionStoragePermissions() } returns false

            val browserController =
                Robolectric.buildActivity(CardBrowser::class.java, inputIntent).create()
            val cardBrowser = browserController.get()
            saveControllerForCleanup(browserController)

            val shadowActivity = shadowOf(cardBrowser)
            val outputIntent = shadowActivity.nextStartedActivity
            val component = assertNotNull(outputIntent.component)

            assertThat(
                "Deck Picker currently handles permissions, so should be called",
                component.className,
                equalTo("com.ichi2.anki.DeckPicker")
            )
            assertThat("Activity should be finishing", cardBrowser.isFinishing)
            assertThat(
                "Activity should be cancelled as it did nothing",
                shadowActivity.resultCode,
                equalTo(Activity.RESULT_CANCELED)
            )
        } finally {
            unmockkStatic(mockedMethod)
        }
    }

    @Test
    fun tagWithBracketsDisplaysProperly() = runTest {
        val n = addNoteUsingBasicModel("Hello", "World")
        n.addTag("sketchy::(1)")
        n.flush()

        val b = browserWithNoNewCards
        b.filterByTag("sketchy::(1)")

        assertThat("tagged card should be returned", b.viewModel.rowCount, equalTo(1))
    }

    @Test
    fun filterByFlagDisplaysProperly() = runTest {
        val cardWithRedFlag = addNoteUsingBasicModel("Card with red flag", "Reverse")
        flagCardForNote(cardWithRedFlag, Flag.RED)

        val cardWithGreenFlag = addNoteUsingBasicModel("Card with green flag", "Reverse")
        flagCardForNote(cardWithGreenFlag, Flag.GREEN)

        val anotherCardWithRedFlag = addNoteUsingBasicModel("Second card with red flag", "Reverse")
        flagCardForNote(anotherCardWithRedFlag, Flag.RED)

        val b = browserWithNoNewCards
        b.viewModel.setFlagFilter(Flag.RED)

        assertThat("Flagged cards should be returned", b.viewModel.rowCount, equalTo(2))
    }

    @Test
    @Flaky(os = OS.WINDOWS, "IllegalStateException: Card '1596783600440' not found")
    fun previewWorksAfterSort() {
        // #7286
        val cid1 = addNoteUsingBasicModel("Hello", "World").cards()[0].id
        val cid2 = addNoteUsingBasicModel("Hello2", "World2").cards()[0].id

        val b = browserWithNoNewCards

        assertThat(b.getPropertiesForCardId(cid1).position, equalTo(0))
        assertThat(b.getPropertiesForCardId(cid2).position, equalTo(1))

        b.selectRowsWithPositions(0)
        val previewIntent = b.viewModel.previewIntentData
        assertThat("before: index", previewIntent.index, equalTo(0))
        assertThat(
            "before: cards",
            previewIntent.cardList,
            equalTo(longArrayOf(cid1, cid2))
        )

        // reverse
        b.changeCardOrder(SortType.SORT_FIELD)

        assertThat(b.getPropertiesForCardId(cid1).position, equalTo(1))
        assertThat(b.getPropertiesForCardId(cid2).position, equalTo(0))

        b.replaceSelectionWith(intArrayOf(0))
        val intentAfterReverse = b.viewModel.previewIntentData
        assertThat("after: index", intentAfterReverse.index, equalTo(0))
        assertThat(
            "after: cards",
            intentAfterReverse.cardList,
            equalTo(longArrayOf(cid2, cid1))
        )
    }

    /** 7420  */
    @Test
    fun addCardDeckIsNotSetIfAllDecksSelectedAfterLoad() = runTest {
        addDeck("NotDefault")

        val b = browserWithNoNewCards

        assertThat("All decks should not be selected", b.hasSelectedAllDecks(), equalTo(false))

        b.viewModel.setDeckId(ALL_DECKS_ID)

        assertThat("All decks should be selected", b.hasSelectedAllDecks(), equalTo(true))

        val addIntent = b.addNoteIntent

        IntentAssert.doesNotHaveExtra(addIntent, NoteEditor.EXTRA_DID)
    }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelected() = runTest {
        val targetDid = addDeck("NotDefault")

        val b = browserWithNoNewCards

        assertThat(
            "The target deck should not yet be selected",
            b.lastDeckId,
            not(equalTo(targetDid))
        )

        b.viewModel.setDeckId(targetDid)

        assertThat("The target deck should be selected", b.lastDeckId, equalTo(targetDid))

        val addIntent = b.addNoteIntent

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, targetDid)
    }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelectedOnOpen() {
        val initialDid = addDeck("NotDefault", setAsSelected = true)

        val b = browserWithNoNewCards

        assertThat("The initial deck should be selected", b.lastDeckId, equalTo(initialDid))

        val addIntent = b.addNoteIntent

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, initialDid)
    }

    @Test
    fun repositionDataTest() = runTest {
        val b = getBrowserWithNotes(1)

        b.selectRowsWithPositions(0)

        val card = getCheckedCard(b)

        assertThat(
            "Initial position of checked card",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("1")
        )

        b.viewModel.repositionSelectedRows(2)

        card.reload()

        assertThat(
            "Position of checked card after reposition",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("2")
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun resetDataTest() = runTest {
        addNoteUsingBasicModel("Hello", "World").firstCard().update {
            due = 5
            queue = Consts.QUEUE_TYPE_REV
            type = Consts.CARD_TYPE_REV
        }

        val b = browserWithNoNewCards

        b.selectRowsWithPositions(0)

        val card = getCheckedCard(b)

        assertThat(
            "Initial due of checked card",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("8/12/20")
        )

        b.resetProgressNoConfirm(listOf(card.id))

        card.reload()

        assertThat(
            "Position of checked card after reset",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("2")
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun rescheduleDataTest() = runTest {
        TimeManager.reset()
        val b = getBrowserWithNotes(1)

        b.selectRowsWithPositions(0)

        val card = getCheckedCard(b)

        assertThat(
            "Initial position of checked card",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("1")
        )

        b.rescheduleWithoutValidation(listOf(card.id), 5)

        card.reload()

        assertThat(card.card.due, equalTo(5))
    }

    @Test
    @Ignore("Doesn't work - but should")
    fun dataUpdatesAfterUndoReposition() {
        val b = getBrowserWithNotes(1)

        b.selectRowsWithPositions(0)

        val card = getCheckedCard(b)

        assertThat(
            "Initial position of checked card",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("1")
        )

        b.repositionCardsNoValidation(2)

        assertThat(
            "Position of checked card after reposition",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("2")
        )

        b.onUndo()

        assertThat(
            "Position of checked card after undo should be reset",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            equalTo("1")
        )
    }

    @Test
    @Ignore("FLAKY: Robolectric getOptionsMenu does not require invalidateOptionsMenu - so would not fail")
    fun rescheduleUndoTest() {
        val b = getBrowserWithNotes(1)

        assertUndoDoesNotContain(b, R.string.deck_conf_cram_reschedule)

        b.selectRowsWithPositions(0)

        b.rescheduleWithoutValidation(listOf(getCheckedCard(b).id), 2)

        assertUndoContains(b, R.string.deck_conf_cram_reschedule)
    }

    @Test
    fun change_deck_dialog_is_dismissed_on_activity_recreation() {
        val cardBrowser = browserWithNoNewCards

        val dialog = cardBrowser.getChangeDeckDialog(listOf())
        cardBrowser.showDialogFragment(dialog)

        val shownDialog: Fragment? = cardBrowser.getDialogFragment()
        assertNotNull(shownDialog)

        ActivityCompat.recreate(cardBrowser)
        advanceRobolectricUiLooper()
        val dialogAfterRecreate: Fragment? = cardBrowser.getDialogFragment()
        assertNull(dialogAfterRecreate)
    }

    /** 8027  */
    @Test
    fun checkSearchString() = runTest {
        addNoteUsingBasicModel("Hello", "John")
        addNoteUsingBasicModel("New", "world").firstCard().update {
            did = addDeck("Deck 1", setAsSelected = true)
        }

        val cardBrowser = browserWithNoNewCards
        cardBrowser.searchCards("world or hello")

        assertThat(
            "Cardbrowser has Deck 1 as selected deck",
            cardBrowser.selectedDeckNameForUi,
            equalTo("Deck 1")
        )
        assertThat(
            "Results should only be from the selected deck",
            cardBrowser.viewModel.rowCount,
            equalTo(1)
        )
    }

    /** PR #8553  */
    @Test
    fun checkDisplayOrderPersistence() {
        // Start the Card Browser with Basic Model
        ensureCollectionLoadIsSynchronous()
        var cardBrowserController = Robolectric.buildActivity(CardBrowser::class.java, Intent())
            .create().start().resume().visible()
        saveControllerForCleanup(cardBrowserController)

        // Make sure card has default value in sortType field
        assertThat(
            "Initially Card Browser has order = noteFld",
            col.config.get<String>("sortType"),
            equalTo("noteFld")
        )

        // Change the display order of the card browser
        cardBrowserController.get().changeCardOrder(SortType.EASE)

        // Kill and restart the activity and ensure that display order is preserved
        val outBundle = Bundle()
        cardBrowserController.saveInstanceState(outBundle)
        cardBrowserController.pause().stop().destroy()
        cardBrowserController =
            Robolectric.buildActivity(CardBrowser::class.java).create(outBundle).start().resume()
                .visible()
        saveControllerForCleanup(cardBrowserController)

        // Find the current (after database has been changed) Mod time

        val updatedMod = col.mod
        assertThat(
            "Card Browser has the new sortType field",
            col.config.get<String>("sortType"),
            equalTo("cardEase")
        )
        assertNotEquals(0, updatedMod)
    }

    @Test
    fun checkIfLongSelectChecksAllCardsInBetween() {
        // #8467 - selecting cards outside the view pane (20) caused a crash as we were using view-based positions
        val browser = getBrowserWithNotes(25)
        selectOneOfManyCards(browser, 7) // HACK: Fix a bug in tests by choosing a value < 8
        selectOneOfManyCards(browser, 24)
        assertThat(browser.viewModel.selectedRowCount(), equalTo(18))
    }

    @Test
    fun checkIfSearchAllDecksWorks() = runTest {
        addNoteUsingBasicModel("Hello", "World")
        addNoteUsingBasicModel("Front", "Back").firstCard().update {
            did = addDeck("Test Deck", setAsSelected = true)
        }

        val cardBrowser = browserWithNoNewCards
        cardBrowser.searchCards("Hello")
        assertThat(
            "Card browser should have Test Deck as the selected deck",
            cardBrowser.selectedDeckNameForUi,
            equalTo("Test Deck")
        )
        assertThat("Result should be empty", cardBrowser.viewModel.rowCount, equalTo(0))

        cardBrowser.searchAllDecks()
        assertThat("Result should contain one card", cardBrowser.viewModel.rowCount, equalTo(1))
    }

    @Test
    fun `'notes-only mode' returns one card from each note`() = runTest {
        // #14623: The functionality was broken
        addNoteUsingBasicAndReversedModel("Hello", "World")
        addNoteUsingBasicAndReversedModel("Hello", "Anki")

        browserWithNoNewCards.apply {
            searchAllDecks()
            with(viewModel) {
                assertThat("Result should contain 4 cards", rowCount, equalTo(4))
                setCardsOrNotes(NOTES)
                assertThat("Result should contain 2 cards (one per note)", rowCount, equalTo(2))
            }
        }
    }

    /** PR #14859  */
    @Test
    fun checkDisplayOrderAfterTogglingCardsToNotes() {
        browserWithNoNewCards.apply {
            changeCardOrder(SortType.EASE) // order no. 7 corresponds to "cardEase"
            changeCardOrder(SortType.EASE) // reverse the list

            viewModel.setCardsOrNotes(NOTES)
            searchCards()

            assertThat(
                "Card Browser has the new noteSortType field",
                col.config.get<String>("noteSortType"),
                equalTo("cardEase")
            )
            assertThat(
                "Card Browser has the new browserNoteSortBackwards field",
                col.config.get<Boolean>("browserNoteSortBackwards"),
                equalTo(true)
            )
        }
    }

    private fun assertUndoDoesNotContain(browser: CardBrowser, @StringRes resId: Int) {
        val shadowActivity = shadowOf(browser)
        val item = shadowActivity.optionsMenu.findItem(R.id.action_undo)
        val expected = browser.getString(resId)
        assertThat(
            item.title.toString(),
            not(containsString(expected.lowercase(Locale.getDefault())))
        )
    }

    private fun assertUndoContains(browser: CardBrowser, @StringRes resId: Int) {
        val shadowActivity = shadowOf(browser)
        val item = shadowActivity.optionsMenu.findItem(R.id.action_undo)
        val expected = browser.getString(resId)
        assertThat(item.title.toString(), containsString(expected.lowercase(Locale.getDefault())))
    }

    private fun getCheckedCard(b: CardBrowser): CardCache {
        val ids = b.viewModel.selectedCardIds
        assertThat("only one card expected to be checked", ids, hasSize(1))
        return b.getPropertiesForCardId(ids[0])
    }

    private fun flagCardForNote(n: Note, flag: Flag) {
        n.firstCard().update {
            setUserFlag(flag)
        }
    }

    private fun selectDefaultDeck() {
        col.decks.select(1)
    }

    private fun deleteCardAtPosition(browser: CardBrowser, positionToCorrupt: Int) {
        removeCardFromCollection(browser.viewModel.allCardIds[positionToCorrupt])
        browser.clearCardData(positionToCorrupt)
    }

    private fun selectOneOfManyCards(cardBrowser: CardBrowser) {
        selectOneOfManyCards(cardBrowser, 0)
    }

    private fun selectOneOfManyCards(browser: CardBrowser, position: Int) {
        Timber.d("Selecting single card")
        val shadowActivity = shadowOf(browser)
        val toSelect = shadowActivity.contentView.findViewById<ListView>(R.id.card_browser_list)

        // Robolectric doesn't easily seem to allow us to fire an onItemLongClick
        val listener = toSelect.onItemLongClickListener
            ?: throw IllegalStateException("no listener found")

        val childAt = toSelect.getChildAt(position)
        if (childAt == null) {
            Timber.w("Can't use childAt on position $position for a single click as it is not visible")
        }
        listener.onItemLongClick(
            null,
            childAt,
            position,
            toSelect.getItemIdAtPosition(position)
        )
        advanceRobolectricUiLooper()
    }

    private fun selectMenuItem(browser: CardBrowser, action_select_all: Int) {
        Timber.d("Selecting menu item")
        // select seems to run an infinite loop :/
        val shadowActivity = shadowOf(browser)
        shadowActivity.clickMenuItem(action_select_all)
        advanceRobolectricUiLooper()
    }

    private class CardBrowserSizeOne : CardBrowser() {
        override fun numCardsToRender(): Int {
            return 1
        }
    }

    /** Returns an instance of [CardBrowser] containing [noteCount] notes */
    private fun getBrowserWithNotes(noteCount: Int, reversed: Boolean = false): CardBrowser {
        ensureCollectionLoadIsSynchronous()
        if (reversed) {
            for (i in 0 until noteCount) {
                addNoteUsingBasicAndReversedModel(i.toString(), "back")
            }
        } else {
            for (i in 0 until noteCount) {
                addNoteUsingBasicModel(i.toString(), "back")
            }
        }
        return super.startRegularActivity<CardBrowser>(Intent()).also {
            advanceRobolectricUiLooper() // may be a fix for flaky tests
        }
    }

    private fun removeCardFromCollection(cardId: CardId) {
        col.removeCardsAndOrphanedNotes(listOf(cardId))
    }

    private val browserWithNoNewCards: CardBrowser
        get() = getBrowserWithNotes(0)

    private val browserWithMultipleNotes: CardBrowser
        get() = getBrowserWithNotes(3)

    // Regression test for #8821
    @Test
    fun emptyScroll() {
        val cardBrowser = getBrowserWithNotes(2)

        val renderOnScroll = cardBrowser.RenderOnScroll()
        renderOnScroll.onScroll(cardBrowser.cardsListView, 0, 0, 2)
    }

    @Test
    fun truncateAndExpand() {
        val cardBrowser = getBrowserWithNotes(3)
        cardBrowser.viewModel.setTruncated(true)

        // Testing whether each card is truncated and ellipsized
        for (i in 0 until (cardBrowser.cardsListView.childCount)) {
            val row = cardBrowser.cardsAdapter.getView(i, null, cardBrowser.cardsListView)
            val column1 = row.findViewById<FixedTextView>(R.id.card_sfld)
            val column2 = row.findViewById<FixedTextView>(R.id.card_column2)

            // Testing truncation
            assertThat(column1.maxLines, equalTo(CardBrowser.LINES_VISIBLE_WHEN_COLLAPSED))
            assertThat(column2.maxLines, equalTo(CardBrowser.LINES_VISIBLE_WHEN_COLLAPSED))

            // Testing ellipses
            assertThat(column1.ellipsize, equalTo(TextUtils.TruncateAt.END))
            assertThat(column2.ellipsize, equalTo(TextUtils.TruncateAt.END))
        }

        cardBrowser.viewModel.setTruncated(false)

        // Testing whether each card is expanded and not ellipsized
        for (i in 0 until (cardBrowser.cardsListView.childCount)) {
            val row = cardBrowser.cardsAdapter.getView(i, null, cardBrowser.cardsListView)
            val column1 = row.findViewById<FixedTextView>(R.id.card_sfld)
            val column2 = row.findViewById<FixedTextView>(R.id.card_column2)

            // Testing expansion
            assertThat(column1.maxLines, equalTo(Integer.MAX_VALUE))
            assertThat(column2.maxLines, equalTo(Integer.MAX_VALUE))

            // Testing not ellipsized
            assertThat(column1.ellipsize, nullValue())
            assertThat(column2.ellipsize, nullValue())
        }
    }

    @Test
    @Ignore("flaky")
    fun checkCardsNotesMode() = runTest {
        val cardBrowser = getBrowserWithNotes(3, true)

        cardBrowser.viewModel.setCardsOrNotes(CARDS)
        cardBrowser.searchCards()

        advanceRobolectricUiLooper()
        // check if we get both cards of each note
        assertThat(cardBrowser.viewModel.rowCount, equalTo(6))

        cardBrowser.viewModel.setCardsOrNotes(NOTES)
        cardBrowser.searchCards()

        // check if we get one card per note
        advanceRobolectricUiLooper()
        assertThat(cardBrowser.viewModel.rowCount, equalTo(3))
    }

    @Test
    fun `column spinner positions are set to 0 if no preferences exist`() = runBlocking {
        // GIVEN: No shared preferences exist for display column selections
        getSharedPrefs().edit {
            remove(DISPLAY_COLUMN_1_KEY)
            remove(DISPLAY_COLUMN_2_KEY)
        }

        // WHEN: CardBrowser is created
        val cardBrowser: CardBrowser = getBrowserWithNotes(5)

        // THEN: Display column selections should default to position 0
        val column1Spinner = cardBrowser.findViewById<Spinner>(R.id.browser_column1_spinner)
        val column2Spinner = cardBrowser.findViewById<Spinner>(R.id.browser_column2_spinner)
        val column1SpinnerPosition = column1Spinner.selectedItemPosition
        val column2SpinnerPosition = column2Spinner.selectedItemPosition

        assertThat(column1SpinnerPosition, equalTo(0))
        assertThat(column2SpinnerPosition, equalTo(0))
    }

    @Test
    fun `column spinner positions are initially set from existing preferences`() = runTest {
        // GIVEN: Shared preferences exists for display column selections
        val index1 = 1
        val index2 = 5

        getSharedPrefs().edit {
            putInt(DISPLAY_COLUMN_1_KEY, index1)
            putInt(DISPLAY_COLUMN_2_KEY, index2)
        }

        // WHEN: CardBrowser is created
        val cardBrowser: CardBrowser = getBrowserWithNotes(7)

        // THEN: The display column selections should match the shared preferences values
        val column1Spinner = cardBrowser.findViewById<Spinner>(R.id.browser_column1_spinner)
        val column2Spinner = cardBrowser.findViewById<Spinner>(R.id.browser_column2_spinner)
        val column1SpinnerPosition = column1Spinner.selectedItemPosition
        val column2SpinnerPosition = column2Spinner.selectedItemPosition

        assertThat(column1SpinnerPosition, equalTo(index1))
        assertThat(column2SpinnerPosition, equalTo(index2))
    }

    @Test
    fun `tapping row toggles state - Issue 14952`() = runTest {
        // tapping the row was broken, checkbox was fine
        browserWithMultipleNotes.apply {
            longClickRowAtPosition(0)
            assertThat("select first row: long press", viewModel.selectedRowCount(), equalTo(1))
            clickRowAtPosition(1)
            assertThat("select row 2: tap", viewModel.selectedRowCount(), equalTo(2))
            clickRowAtPosition(0)
            assertThat("deselect row: tap", viewModel.selectedRowCount(), equalTo(1))
        }
    }

    @Test
    fun `deck id is remembered - issue 15072`() = runTest {
        // WARN: This doesn't mirror reality due to the use of coroutines
        // in the issue, selectDeckAndSave() was called AFTER the search had been performed
        // due to this being called immediately in a test-based context

        // We're going to move this functionality entirely to the ViewModel over the next few weeks
        // so this test should be updated and working after the refactorings are completed
        addNoteUsingBasicModel().moveToDeck("First")
        addNoteUsingBasicModel().moveToDeck("Second")

        val secondDeckId = requireNotNull(col.decks.idForName("Second"))

        browserWithNoNewCards.apply {
            selectDeckAndSave(secondDeckId)
            assertThat(viewModel.deckId, equalTo(secondDeckId))
            finish()
        }

        browserWithNoNewCards.apply {
            assertThat("deckId is remembered", viewModel.deckId, equalTo(secondDeckId))
            assertThat("deckId is searched", viewModel.rowCount, equalTo(1))
        }
    }

    @Test
    @Ignore("14950")
    fun `selection is maintained after toggle mark 14950`() = withBrowser(noteCount = 5) {
        // TODO: Once refactoring is completed, move this to the ViewModel Test
        selectRowsWithPositions(0, 1, 2)

        assertThat("3 rows are selected", viewModel.selectedRows.size, equalTo(3))
        assertThat("selection is not marked", viewModel.selectedRows.all { !it.isMarked })

        toggleMark().join()

        assertThat("3 rows are still selected", viewModel.selectedRows.size, equalTo(3))
        assertThat("selection is now marked", viewModel.selectedRows.all { it.isMarked })
    }

    @Suppress("SameParameterValue")
    private fun withBrowser(noteCount: Int = 0, block: suspend CardBrowser.() -> Unit) = runTest {
        getBrowserWithNotes(noteCount).apply {
            block(this)
        }
    }

    private val CardCache.isMarked
        get() = NoteService.isMarked(card.note(col))
}

fun CardBrowser.hasSelectedCardAtPosition(i: Int): Boolean =
    viewModel.selectedRows.contains(viewModel.getRowAtPosition(i))

fun CardBrowser.replaceSelectionWith(positions: IntArray) {
    viewModel.selectNone()
    selectRowsWithPositions(*positions)
}

fun CardBrowser.selectRowsWithPositions(vararg positions: Int) {
    // PREF: inefficient as the card flow is updated each iteration
    positions.forEach { pos ->
        check(pos < viewModel.rowCount) {
            "Attempted to check row at index $pos. ${viewModel.rowCount} rows available"
        }
        viewModel.selectRowAtPosition(pos)
    }
}

fun CardBrowser.getPropertiesForCardId(cardId: CardId): CardCache =
    viewModel.cards.find { c -> c.id == cardId } ?: throw IllegalStateException("Card '$cardId' not found")

fun CardBrowser.clickRowAtPosition(pos: Int) = shadowOf(cardsListView).performItemClick(pos)
fun CardBrowser.longClickRowAtPosition(pos: Int) = cardsListView.getViewByPosition(pos).performLongClick()

// https://stackoverflow.com/a/24864536/13121290
fun ListView.getViewByPosition(pos: Int): View {
    val firstListItemPosition = firstVisiblePosition
    val lastListItemPosition = firstListItemPosition + childCount - 1
    return if (pos < firstListItemPosition || pos > lastListItemPosition) {
        requireNotNull(adapter.getView(pos, null, this)) { "failed to find item at pos: $pos" }
    } else {
        val childIndex = pos - firstListItemPosition
        requireNotNull(getChildAt(childIndex)) {
            "failed to find item at pos: $pos; " +
                "first: $firstListItemPosition; " +
                "last: $lastListItemPosition"
            "childIndex: $childIndex"
        }
    }
}

val CardBrowser.lastDeckId
    get() = viewModel.lastDeckId
