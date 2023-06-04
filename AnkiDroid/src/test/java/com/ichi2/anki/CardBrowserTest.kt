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
import android.widget.ListView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser.CardCache
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.testutils.AnkiActivityUtils.getDialogFragment
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrowSuspend
import com.ichi2.testutils.Flaky
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.OS
import com.ichi2.testutils.withNoWritePermission
import com.ichi2.ui.FixedTextView
import net.ankiweb.rsdroid.RustCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.Locale
import java.util.Random
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CardBrowserTest : RobolectricTest() {
    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithNoCards() {
        val browser = browserWithNoNewCards
        assertThat(browser.isInMultiSelectMode, equalTo(false))
    }

    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithCards() {
        val browser = browserWithMultipleNotes
        assertThat(browser.isInMultiSelectMode, equalTo(false))
    }

    @Test
    fun selectAllIsNotVisibleWhenNoCardsInDeck() {
        val browser = browserWithNoNewCards
        assertThat(browser.isShowingSelectAll, equalTo(false))
    }

    @Test
    fun selectAllIsVisibleWhenCardsInDeck() {
        val browser = browserWithMultipleNotes
        assertThat(browser.cardCount(), greaterThan(0L))
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
        assertThat(browser.isInMultiSelectMode, equalTo(true))
    }

    @Test
    @Flaky(os = OS.WINDOWS, "Expected `true`, got `false`")
    fun browserIsInMultiSelectModeWhenSelectingAll() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        assertThat(browser.isInMultiSelectMode, equalTo(true))
    }

    @Test
    fun browserIsNotInMultiSelectModeWhenSelectingNone() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        selectMenuItem(browser, R.id.action_select_none)
        assertThat(browser.isInMultiSelectMode, equalTo(false))
    }

    @Test
    fun browserDoesNotFailWhenSelectingANonExistingCard() = runTest {
        // #5900
        val browser = getBrowserWithNotes(6)
        // Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
        deleteCardAtPosition(browser, 0)
        assertDoesNotThrowSuspend { browser.rerenderAllCards() }
        assertThat(browser.cardCount(), equalTo(5L))
    }

    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    fun selectionsAreCorrectWhenNonExistingCardIsRemoved() = runTest {
        val browser = getBrowserWithNotes(7)
        browser.checkCardsAtPositions(1, 3, 5, 6)
        deleteCardAtPosition(browser, 2) // delete non-selected
        deleteCardAtPosition(browser, 3) // delete selected, ensure it's not still selected

        // ACT
        browser.rerenderAllCards()
        // ASSERT
        assertThat(browser.cardCount(), equalTo(6L))
        assertThat("A checked card should have been removed", browser.checkedCardCount(), equalTo(3))
        assertThat("Checked card before should not have changed", browser.hasCheckedCardAtPosition(1), equalTo(true))
        assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(3), equalTo(true))
        assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(4), equalTo(true))
    }

    @Test
    fun canChangeDeckToRegularDeck() = runTest {
        addDeck("Hello")
        val b = getBrowserWithNotes(5)

        val decks = b.validDecksForChangeDeck

        for (d in decks) {
            if (d.getString("name") == "Hello") {
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

        if (decks.any { it.getString("name") == "World" }) {
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
            assertThat(validNames, hasItem(d.getString("name")))
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
        b.checkCardsAtPositions(0, 2)

        val cardIds = b.checkedCardIds

        for (cardId in cardIds) {
            assertThat("Deck should have been changed yet", col.getCard(cardId).did, not(deckIdToChangeTo))
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
        b.checkCardsAtPositions(0, 2)

        val cardIds = b.checkedCardIds

        b.executeChangeCollectionTask(cardIds, dynId)

        for (cardId in cardIds) {
            assertThat("Deck should not be changed", col.getCard(cardId).did, not(dynId))
        }
    }

    @Test // see #13391
    fun newlyCreatedDeckIsShownAsOptionInBrowser() = runTest {
        val deckOneId = addDeck("one")
        val browser = browserWithNoNewCards
        assertEquals(1, browser.validDecksForChangeDeck.size)
        assertEquals(deckOneId, browser.validDecksForChangeDeck.first().id)
        val deckTwoId = addDeck("two")
        assertEquals(2, browser.validDecksForChangeDeck.size)
        assertArrayEquals(longArrayOf(deckOneId, deckTwoId), browser.validDecksForChangeDeck.map { it.id }.toLongArray())
    }

    @Test
    fun flagsAreShownInBigDecksTest() = runTest {
        val numberOfNotes = 75
        val cardBrowser = getBrowserWithNotes(numberOfNotes)

        // select a random card
        val random = Random(1)
        val cardPosition = random.nextInt(numberOfNotes)
        assumeThat("card position to select is 60", cardPosition, equalTo(60))
        cardBrowser.checkCardsAtPositions(cardPosition)
        assumeTrue("card at position 60 is selected", cardBrowser.hasCheckedCardAtPosition(cardPosition))

        // flag the selected card with flag = 1
        val flag = 1
        cardBrowser.updateSelectedCardsFlag(flag)
        // check if card flag turned to flag = 1
        assertThat("Card should be flagged", getCheckedCard(cardBrowser).card.userFlag(), equalTo(flag))

        // unflag the selected card with flag = 0
        val unflagFlag = 0
        cardBrowser.updateSelectedCardsFlag(unflagFlag)
        // check if card flag actually changed from flag = 1
        assertThat("Card flag should be removed", getCheckedCard(cardBrowser).card.userFlag(), not(flag))

        // deselect and select all cards
        cardBrowser.onSelectNone()
        cardBrowser.onSelectAll()
        // flag all the cards with flag = 3
        val flagForAll = 3
        cardBrowser.updateSelectedCardsFlag(flagForAll)
        // check if all card flags turned to flag = 3
        assertThat(
            "All cards should be flagged",
            cardBrowser.cardIds
                .map { cardId -> getCardFlagAfterFlagChangeDone(cardBrowser, cardId) }
                .all { flag1 -> flag1 == flagForAll }
        )
    }

    @Test
    fun flagValueIsShownOnCard() {
        val n = addNoteUsingBasicModel("1", "back")
        flagCardForNote(n, 1)

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
        withNoWritePermission {
            val inputIntent = Intent("android.intent.action.PROCESS_TEXT")

            val browserController = Robolectric.buildActivity(CardBrowser::class.java, inputIntent).create()
            val cardBrowser = browserController.get()
            saveControllerForCleanup(browserController)

            val shadowActivity = shadowOf(cardBrowser)
            val outputIntent = shadowActivity.nextStartedActivity
            val component = assertNotNull(outputIntent.component)

            assertThat("Deck Picker currently handles permissions, so should be called", component.className, equalTo("com.ichi2.anki.DeckPicker"))
            assertThat("Activity should be finishing", cardBrowser.isFinishing)
            assertThat("Activity should be cancelled as it did nothing", shadowActivity.resultCode, equalTo(Activity.RESULT_CANCELED))
        }
    }

    @Test
    fun tagWithBracketsDisplaysProperly() = runTest {
        val n = addNoteUsingBasicModel("Hello", "World")
        n.addTag("sketchy::(1)")
        n.flush()

        val b = browserWithNoNewCards
        b.filterByTag("sketchy::(1)")

        assertThat("tagged card should be returned", b.cardCount, equalTo(1))
    }

    @Test
    fun filterByFlagDisplaysProperly() = runTest {
        val cardWithRedFlag = addNoteUsingBasicModel("Card with red flag", "Reverse")
        flagCardForNote(cardWithRedFlag, 1)

        val cardWithGreenFlag = addNoteUsingBasicModel("Card with green flag", "Reverse")
        flagCardForNote(cardWithGreenFlag, 3)

        val anotherCardWithRedFlag = addNoteUsingBasicModel("Second card with red flag", "Reverse")
        flagCardForNote(anotherCardWithRedFlag, 1)

        val b = browserWithNoNewCards
        b.filterByFlag(1)

        assertThat("Flagged cards should be returned", b.cardCount, equalTo(2))
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

        b.checkCardsAtPositions(0)
        val previewIntent = b.previewIntent
        assertThat("before: index", previewIntent.getIntExtra("index", -100), equalTo(0))
        assertThat("before: cards", previewIntent.getLongArrayExtra("cardList"), equalTo(longArrayOf(cid1, cid2)))

        // reverse
        b.changeCardOrder(1)

        assertThat(b.getPropertiesForCardId(cid1).position, equalTo(1))
        assertThat(b.getPropertiesForCardId(cid2).position, equalTo(0))

        b.replaceSelectionWith(intArrayOf(0))
        val intentAfterReverse = b.previewIntent
        assertThat("after: index", intentAfterReverse.getIntExtra("index", -100), equalTo(0))
        assertThat("after: cards", intentAfterReverse.getLongArrayExtra("cardList"), equalTo(longArrayOf(cid2, cid1)))
    }

    /** 7420  */
    @Test
    fun addCardDeckIsNotSetIfAllDecksSelectedAfterLoad() {
        addDeck("NotDefault")

        val b = browserWithNoNewCards

        assertThat("All decks should not be selected", b.hasSelectedAllDecks(), equalTo(false))

        b.selectAllDecks()

        assertThat("All decks should be selected", b.hasSelectedAllDecks(), equalTo(true))

        val addIntent = b.addNoteIntent

        IntentAssert.doesNotHaveExtra(addIntent, NoteEditor.EXTRA_DID)
    }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelected() {
        val targetDid = addDeck("NotDefault")

        val b = browserWithNoNewCards

        assertThat("The target deck should not yet be selected", b.lastDeckId, not(equalTo(targetDid)))

        b.selectDeckAndSave(targetDid)

        assertThat("The target deck should be selected", b.lastDeckId, equalTo(targetDid))

        val addIntent = b.addNoteIntent

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, targetDid)
    }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelectedOnOpen() {
        val initialDid = addDeck("NotDefault")

        col.decks.select(initialDid)

        val b = browserWithNoNewCards

        assertThat("The initial deck should be selected", b.lastDeckId, equalTo(initialDid))

        val addIntent = b.addNoteIntent

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, initialDid)
    }

    @Test
    fun repositionDataTest() {
        val b = getBrowserWithNotes(1)

        b.checkCardsAtPositions(0)

        val card = getCheckedCard(b)

        assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("1"))

        b.repositionCardsNoValidation(listOf(card.id), 2)

        assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("2"))
    }

    @Test
    @Config(qualifiers = "en")
    fun resetDataTest() {
        addNoteUsingBasicModel("Hello", "World").firstCard().apply {
            due = 5
            queue = Consts.QUEUE_TYPE_REV
            type = Consts.CARD_TYPE_REV
            flush(col)
        }

        val b = browserWithNoNewCards

        b.checkCardsAtPositions(0)

        val card = getCheckedCard(b)

        assertThat("Initial due of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("8/12/20"))

        b.resetProgressNoConfirm(listOf(card.id))

        assertThat("Position of checked card after reset", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("1"))
    }

    @Test
    @Config(qualifiers = "en")
    fun rescheduleDataTest() {
        val b = getBrowserWithNotes(1)

        b.checkCardsAtPositions(0)

        val card = getCheckedCard(b)

        assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("1"))

        b.rescheduleWithoutValidation(listOf(card.id), 5)

        assertThat("Due of checked card after reschedule", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("8/12/20"))
    }

    @Test
    @Ignore("Doesn't work - but should")
    fun dataUpdatesAfterUndoReposition() {
        val b = getBrowserWithNotes(1)

        b.checkCardsAtPositions(0)

        val card = getCheckedCard(b)

        assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("1"))

        b.repositionCardsNoValidation(listOf(card.id), 2)

        assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("2"))

        b.onUndo()

        assertThat("Position of checked card after undo should be reset", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("1"))
    }

    @Test
    @Ignore("FLAKY: Robolectric getOptionsMenu does not require invalidateOptionsMenu - so would not fail")
    fun rescheduleUndoTest() {
        val b = getBrowserWithNotes(1)

        assertUndoDoesNotContain(b, R.string.deck_conf_cram_reschedule)

        b.checkCardsAtPositions(0)

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

        cardBrowser.recreate()
        advanceRobolectricUiLooper()
        val dialogAfterRecreate: Fragment? = cardBrowser.getDialogFragment()
        assertNull(dialogAfterRecreate)
    }

    /** 8027  */
    @Test
    fun checkSearchString() = runTest {
        addNoteUsingBasicModel("Hello", "John")
        val deck = addDeck("Deck 1")
        col.decks.select(deck)
        val c2 = addNoteUsingBasicModel("New", "world").firstCard()
        c2.did = deck
        c2.flush(col)

        val cardBrowser = browserWithNoNewCards
        cardBrowser.searchCards("world or hello")

        assertThat("Cardbrowser has Deck 1 as selected deck", cardBrowser.selectedDeckNameForUi, equalTo("Deck 1"))
        assertThat("Results should only be from the selected deck", cardBrowser.cardCount, equalTo(1))
    }

    /** PR #8553  */
    @Test
    @RustCleanup("after legacy schema dropped, col.save() can be dropped and updatedMod can be taken from col.mod")
    fun checkDisplayOrderPersistence() {
        // Start the Card Browser with Basic Model
        ensureCollectionLoadIsSynchronous()
        var cardBrowserController = Robolectric.buildActivity(CardBrowser::class.java, Intent())
            .create().start().resume().visible()
        saveControllerForCleanup(cardBrowserController)

        // Make sure card has default value in sortType field
        assertThat("Initially Card Browser has order = noteFld", col.get_config_string("sortType"), equalTo("noteFld"))

        col.db.execute("update col set mod = 0")

        // Change the display order of the card browser
        cardBrowserController.get().changeCardOrder(7) // order no. 7 corresponds to "cardEase"

        // Kill and restart the activity and ensure that display order is preserved
        val outBundle = Bundle()
        cardBrowserController.saveInstanceState(outBundle)
        cardBrowserController.pause().stop().destroy()
        cardBrowserController = Robolectric.buildActivity(CardBrowser::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(cardBrowserController)

        // Find the current (after database has been changed) Mod time
        col.save()
        val updatedMod = col.db.queryScalar("select mod from col")
        assertThat("Card Browser has the new sortType field", col.get_config_string("sortType"), equalTo("cardEase"))
        assertNotEquals("Modification time must change", 0, updatedMod)
    }

    @Test
    fun checkIfLongSelectChecksAllCardsInBetween() {
        // #8467 - selecting cards outside the view pane (20) caused a crash as we were using view-based positions
        val browser = getBrowserWithNotes(25)
        selectOneOfManyCards(browser, 7) // HACK: Fix a bug in tests by choosing a value < 8
        selectOneOfManyCards(browser, 24)
        assertThat(browser.checkedCardCount(), equalTo(18))
    }

    @Test
    fun checkIfSearchAllDecksWorks() = runTest {
        addNoteUsingBasicModel("Hello", "World")
        val deck = addDeck("Test Deck")
        col.decks.select(deck)
        val c2 = addNoteUsingBasicModel("Front", "Back").firstCard()
        c2.did = deck
        c2.flush(col)

        val cardBrowser = browserWithNoNewCards
        cardBrowser.searchCards("Hello")
        assertThat("Card browser should have Test Deck as the selected deck", cardBrowser.selectedDeckNameForUi, equalTo("Test Deck"))
        assertThat("Result should be empty", cardBrowser.cardCount, equalTo(0))

        cardBrowser.searchAllDecks()
        assertThat("Result should contain one card", cardBrowser.cardCount, equalTo(1))
    }

    private fun assertUndoDoesNotContain(browser: CardBrowser, @StringRes resId: Int) {
        val shadowActivity = shadowOf(browser)
        val item = shadowActivity.optionsMenu.findItem(R.id.action_undo)
        val expected = browser.getString(resId)
        assertThat(item.title.toString(), not(containsString(expected.lowercase(Locale.getDefault()))))
    }

    private fun assertUndoContains(browser: CardBrowser, @StringRes resId: Int) {
        val shadowActivity = shadowOf(browser)
        val item = shadowActivity.optionsMenu.findItem(R.id.action_undo)
        val expected = browser.getString(resId)
        assertThat(item.title.toString(), containsString(expected.lowercase(Locale.getDefault())))
    }

    private fun getCheckedCard(b: CardBrowser): CardCache {
        val ids = b.checkedCardIds
        assertThat("only one card expected to be checked", ids, hasSize(1))
        return b.getPropertiesForCardId(ids[0])
    }

    private fun flagCardForNote(n: Note, flag: Int) {
        val c = n.firstCard()
        c.setUserFlag(flag)
        c.flush(col)
    }

    private fun selectDefaultDeck() {
        col.decks.select(1)
    }

    private fun deleteCardAtPosition(browser: CardBrowser, positionToCorrupt: Int) {
        removeCardFromCollection(browser.cardIds[positionToCorrupt])
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
        // "isTruncated" variable set to true
        cardBrowser.isTruncated = true

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

        // "isTruncate" variable set to false
        cardBrowser.isTruncated = false

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
    fun checkCardsNotesMode() = runTest {
        val cardBrowser = getBrowserWithNotes(3, true)

        // set browser to be in cards mode
        cardBrowser.inCardsMode = true
        cardBrowser.searchCards()

        advanceRobolectricUiLooper()
        // check if we get both cards of each note
        assertThat(cardBrowser.mCards.size(), equalTo(6))

        // set browser to be in notes mode
        cardBrowser.inCardsMode = false
        cardBrowser.searchCards()

        // check if we get one card per note
        advanceRobolectricUiLooper()
        assertThat(cardBrowser.mCards.size(), equalTo(3))
    }
}
