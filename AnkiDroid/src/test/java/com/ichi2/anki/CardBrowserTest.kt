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

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.ListView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser.CardCache
import com.ichi2.async.CollectionTask.SearchCards
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.SortOrder.NoOrdering
import com.ichi2.testutils.AnkiActivityUtils.getDialogFragment
import com.ichi2.testutils.AnkiAssert
import com.ichi2.testutils.IntentAssert.doesNotHaveExtra
import com.ichi2.testutils.IntentAssert.hasExtra
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.hamcrest.core.Is
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.*
import javax.annotation.CheckReturnValue
import kotlin.test.junit.JUnitAsserter as KotlinJunitAssert

@RunWith(AndroidJUnit4::class)
class CardBrowserTest : RobolectricTest() {
    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithNoCards() {
        val browser = browserWithNoNewCards
        MatcherAssert.assertThat(browser.isInMultiSelectMode, Is.`is`(false))
    }

    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithCards() {
        val browser = browserWithMultipleNotes
        MatcherAssert.assertThat(browser.isInMultiSelectMode, Is.`is`(false))
    }

    @Test
    fun selectAllIsNotVisibleWhenNoCardsInDeck() {
        val browser = browserWithNoNewCards
        MatcherAssert.assertThat(browser.isShowingSelectAll, Is.`is`(false))
    }

    @Test
    fun selectAllIsVisibleWhenCardsInDeck() {
        val browser = browserWithMultipleNotes
        MatcherAssert.assertThat(browser.cardCount(), Matchers.greaterThan(0L))
        MatcherAssert.assertThat(browser.isShowingSelectAll, Is.`is`(true))
    }

    @Test
    @RunInBackground
    fun selectAllIsNotVisibleOnceCalled() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat(browser.isShowingSelectAll, Is.`is`(false))
    }

    @Test
    @RunInBackground
    fun selectNoneIsVisibleOnceSelectAllCalled() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat(browser.isShowingSelectNone, Is.`is`(true))
    }

    @Test
    @RunInBackground
    fun selectNoneIsVisibleWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        advanceRobolectricLooperWithSleep()
        selectOneOfManyCards(browser)
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat(browser.isShowingSelectNone, Is.`is`(true))
    }

    @Test
    fun selectAllIsVisibleWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        MatcherAssert.assertThat(browser.isShowingSelectAll, Is.`is`(true))
    }

    @Test
    fun browserIsInMultiSelectModeWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        MatcherAssert.assertThat(browser.isInMultiSelectMode, Is.`is`(true))
    }

    @Test
    fun browserIsInMultiSelectModeWhenSelectingAll() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        MatcherAssert.assertThat(browser.isInMultiSelectMode, Is.`is`(true))
    }

    @Test
    fun browserIsNotInMultiSelectModeWhenSelectingNone() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        selectMenuItem(browser, R.id.action_select_none)
        MatcherAssert.assertThat(browser.isInMultiSelectMode, Is.`is`(false))
    }

    @Test
    fun browserDoesNotFailWhenSelectingANonExistingCard() {
        // #5900
        val browser = getBrowserWithNotes(6)
        // Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
        deleteCardAtPosition(browser, 0)
        AnkiAssert.assertDoesNotThrow { browser.rerenderAllCards() }
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat(browser.cardCount(), Matchers.equalTo(5L))
    }

    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    fun selectionsAreCorrectWhenNonExistingCardIsRemoved() {
        val browser = getBrowserWithNotes(7)
        browser.checkCardsAtPositions(1, 3, 5, 6)
        deleteCardAtPosition(browser, 2) // delete non-selected
        deleteCardAtPosition(browser, 3) // delete selected, ensure it's not still selected

        // ACT
        browser.rerenderAllCards()
        // ASSERT
        MatcherAssert.assertThat(browser.cardCount(), Matchers.equalTo(6L))
        MatcherAssert.assertThat("A checked card should have been removed", browser.checkedCardCount(), Matchers.equalTo(3))
        MatcherAssert.assertThat("Checked card before should not have changed", browser.hasCheckedCardAtPosition(1), Is.`is`(true))
        MatcherAssert.assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(3), Is.`is`(true))
        MatcherAssert.assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(4), Is.`is`(true))
    }

    @Test
    fun canChangeDeckToRegularDeck() {
        addDeck("Hello")
        val b = getBrowserWithNotes(5)
        val decks = b.validDecksForChangeDeck
        for (d in decks) {
            if (d.getString("name") == "Hello") {
                return
            }
        }
        Assert.fail("Added deck was not found in the Card Browser")
    }

    @Test
    fun cannotChangeDeckToDynamicDeck() {
        // 5932 - dynamic decks are meant to have cards added to them through "Rebuild".
        addDynamicDeck("World")
        val b = getBrowserWithNotes(5)
        val decks = b.validDecksForChangeDeck
        for (d in decks) {
            if (d.getString("name") == "World") {
                Assert.fail("Dynamic decks should not be transferred to by the browser.")
            }
        }
    }

    @Test
    fun changeDeckIntegrationTestDynamicAndNon() {
        addDeck("Hello")
        addDynamicDeck("World")
        val validNames = HashSet<String?>()
        validNames.add("Default")
        validNames.add("Hello")
        val b = getBrowserWithNotes(5)
        val decks = b.validDecksForChangeDeck
        for (d in decks) {
            MatcherAssert.assertThat(validNames, Matchers.hasItem(d.getString("name")))
        }
        MatcherAssert.assertThat("Additional unexpected decks were present", decks.size, Is.`is`(2))
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
        advanceRobolectricLooperWithSleep()
        val cardIds = b.checkedCardIds
        for (cardId in cardIds) {
            MatcherAssert.assertThat("Deck should have been changed yet", col.getCard(cardId).did, Matchers.not(deckIdToChangeTo))
        }

        // act
        AnkiAssert.assertDoesNotThrow { b.moveSelectedCardsToDeck(deckIdToChangeTo) }

        // assert
        advanceRobolectricLooperWithSleep()
        for (cardId in cardIds) {
            MatcherAssert.assertThat("Deck should be changed", col.getCard(cardId).did, Is.`is`(deckIdToChangeTo))
        }
    }

    @Test
    fun changeDeckViaTaskIsHandledCorrectly() {
        val dynId = addDynamicDeck("World")
        selectDefaultDeck()
        val b = getBrowserWithNotes(5)
        b.checkCardsAtPositions(0, 2)
        val cardIds = b.checkedCardIds
        b.executeChangeCollectionTask(cardIds, dynId)
        for (cardId in cardIds) {
            MatcherAssert.assertThat("Deck should not be changed", col.getCard(cardId).did, Matchers.not(dynId))
        }
    }

    @Test
    fun flagsAreShownInBigDecksTest() {
        val numberOfNotes = 75
        val cardBrowser = getBrowserWithNotes(numberOfNotes)

        // select a random card
        val random = Random(1)
        val cardPosition = random.nextInt(numberOfNotes)
        assumeThat("card position to select is 60", cardPosition, Is.`is`(60))
        cardBrowser.checkCardsAtPositions(cardPosition)
        assumeTrue("card at position 60 is selected", cardBrowser.hasCheckedCardAtPosition(cardPosition))

        // flag the selected card with flag = 1
        val flag = 1
        cardBrowser.flagTask(flag)
        advanceRobolectricLooperWithSleep()
        // check if card flag turned to flag = 1
        MatcherAssert.assertThat("Card should be flagged", getCheckedCard(cardBrowser).card.userFlag(), Is.`is`(flag))

        // unflag the selected card with flag = 0
        val unflagFlag = 0
        cardBrowser.flagTask(unflagFlag)
        advanceRobolectricLooperWithSleep()
        // check if card flag actually changed from flag = 1
        MatcherAssert.assertThat("Card flag should be removed", getCheckedCard(cardBrowser).card.userFlag(), Matchers.not(flag))

        // deselect and select all cards
        cardBrowser.onSelectNone()
        cardBrowser.onSelectAll()
        // flag all the cards with flag = 3
        val flagForAll = 3
        cardBrowser.flagTask(flagForAll)
        advanceRobolectricLooperWithSleep()
        // check if all card flags turned to flag = 3
        MatcherAssert.assertThat(
            "All cards should be flagged",
            Arrays.stream(cardBrowser.cardIds)
                .map { cardId: Long -> getCardFlagAfterFlagChangeDone(cardBrowser, cardId).toLong() }
                .noneMatch { flag1: Long -> flag1 != flagForAll.toLong() }
        )
    }

    @Test
    fun flagValueIsShownOnCard() {
        val n = addNoteUsingBasicModel("1", "back")
        flagCardForNote(n, 1)
        val cardId = n.cids()[0]
        val b = browserWithNoNewCards
        val actualFlag = getCardFlagAfterFlagChangeDone(b, cardId)
        MatcherAssert.assertThat("The card flag value should be reflected in the UI", actualFlag, Is.`is`(1))
    }

    private fun getCardFlagAfterFlagChangeDone(cardBrowser: CardBrowser, cardId: Long): Int {
        return cardBrowser.getPropertiesForCardId(cardId).card.userFlag()
    }

    @Test
    fun startupFromCardBrowserActionItemShouldEndActivityIfNoPermissions() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val app = Shadows.shadowOf(application)
        app.denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        app.denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val inputIntent = Intent("android.intent.action.PROCESS_TEXT")
        val browserController = Robolectric.buildActivity(CardBrowser::class.java, inputIntent).create()
        val cardBrowser = browserController.get()
        saveControllerForCleanup(browserController)
        val shadowActivity = Shadows.shadowOf(cardBrowser)
        val outputIntent = shadowActivity.nextStartedActivity
        val component = outputIntent.component
        MatcherAssert.assertThat(component, Matchers.notNullValue())
        val componentName = component!!
        MatcherAssert.assertThat("Deck Picker currently handles permissions, so should be called", componentName.className, Is.`is`("com.ichi2.anki.DeckPicker"))
        MatcherAssert.assertThat("Activity should be finishing", cardBrowser.isFinishing)
        MatcherAssert.assertThat("Activity should be cancelled as it did nothing", shadowActivity.resultCode, Is.`is`(Activity.RESULT_CANCELED))
    }

    @Test
    fun tagWithBracketsDisplaysProperly() {
        val n = addNoteUsingBasicModel("Hello", "World")
        n.addTag("sketchy::(1)")
        n.flush()
        val b = browserWithNoNewCards
        b.filterByTag("sketchy::(1)")
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("tagged card should be returned", b.cardCount, Is.`is`(1))
    }

    @Test
    fun filterByFlagDisplaysProperly() {
        val cardWithRedFlag = addNoteUsingBasicModel("Card with red flag", "Reverse")
        flagCardForNote(cardWithRedFlag, 1)
        val cardWithGreenFlag = addNoteUsingBasicModel("Card with green flag", "Reverse")
        flagCardForNote(cardWithGreenFlag, 3)
        val anotherCardWithRedFlag = addNoteUsingBasicModel("Second card with red flag", "Reverse")
        flagCardForNote(anotherCardWithRedFlag, 1)
        val b = browserWithNoNewCards
        b.filterByFlag(1)
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Flagged cards should be returned", b.cardCount, Is.`is`(2))
    }

    @Test
    fun previewWorksAfterSort() {
        // #7286
        val cid1 = addNoteUsingBasicModel("Hello", "World").cards()[0].id
        val cid2 = addNoteUsingBasicModel("Hello2", "World2").cards()[0].id
        val b = browserWithNoNewCards
        MatcherAssert.assertThat(b.getPropertiesForCardId(cid1).position, Is.`is`(0))
        MatcherAssert.assertThat(b.getPropertiesForCardId(cid2).position, Is.`is`(1))
        b.checkCardsAtPositions(0)
        val previewIntent = b.previewIntent
        MatcherAssert.assertThat("before: index", previewIntent.getIntExtra("index", -100), Is.`is`(0))
        MatcherAssert.assertThat("before: cards", previewIntent.getLongArrayExtra("cardList"), Is.`is`(longArrayOf(cid1, cid2)))

        // reverse
        b.changeCardOrder(1)
        MatcherAssert.assertThat(b.getPropertiesForCardId(cid1).position, Is.`is`(1))
        MatcherAssert.assertThat(b.getPropertiesForCardId(cid2).position, Is.`is`(0))
        b.replaceSelectionWith(intArrayOf(0))
        val intentAfterReverse = b.previewIntent
        MatcherAssert.assertThat("after: index", intentAfterReverse.getIntExtra("index", -100), Is.`is`(0))
        MatcherAssert.assertThat("after: cards", intentAfterReverse.getLongArrayExtra("cardList"), Is.`is`(longArrayOf(cid2, cid1)))
    }

    /** 7420  */
    @Test
    fun addCardDeckIsNotSetIfAllDecksSelectedAfterLoad() {
        addDeck("NotDefault")
        val b = browserWithNoNewCards
        MatcherAssert.assertThat("All decks should not be selected", b.hasSelectedAllDecks(), Is.`is`(false))
        b.selectAllDecks()
        MatcherAssert.assertThat("All decks should be selected", b.hasSelectedAllDecks(), Is.`is`(true))
        val addIntent = b.addNoteIntent
        doesNotHaveExtra(addIntent, NoteEditor.EXTRA_DID)
    }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelected() {
        val targetDid = addDeck("NotDefault")
        val b = browserWithNoNewCards
        MatcherAssert.assertThat("The target deck should not yet be selected", b.lastDeckId, Matchers.not(Is.`is`(targetDid)))
        b.selectDeckAndSave(targetDid)
        MatcherAssert.assertThat("The target deck should be selected", b.lastDeckId, Is.`is`(targetDid))
        val addIntent = b.addNoteIntent
        hasExtra(addIntent, NoteEditor.EXTRA_DID, targetDid)
    }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelectedOnOpen() {
        val initialDid = addDeck("NotDefault")
        col.decks.select(initialDid)
        val b = browserWithNoNewCards
        MatcherAssert.assertThat("The initial deck should be selected", b.lastDeckId, Is.`is`(initialDid))
        val addIntent = b.addNoteIntent
        hasExtra(addIntent, NoteEditor.EXTRA_DID, initialDid)
    }

    @Test
    fun repositionDataTest() {
        val b = getBrowserWithNotes(1)
        b.checkCardsAtPositions(0)
        val card = getCheckedCard(b)
        MatcherAssert.assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("1"))
        b.repositionCardsNoValidation(listOf(card.id), 2)
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("2"))
    }

    @Test
    @Config(qualifiers = "en")
    fun resetDataTest() {
        val c = addNoteUsingBasicModel("Hello", "World").firstCard()
        c.due = 5
        c.queue = Consts.QUEUE_TYPE_REV
        c.type = Consts.CARD_TYPE_REV
        c.flush()
        val b = browserWithNoNewCards
        b.checkCardsAtPositions(0)
        val card = getCheckedCard(b)
        MatcherAssert.assertThat("Initial due of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("8/12/20"))
        b.resetProgressNoConfirm(listOf(card.id))
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Position of checked card after reset", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("1"))
    }

    @Test
    @Config(qualifiers = "en")
    fun rescheduleDataTest() {
        val b = getBrowserWithNotes(1)
        b.checkCardsAtPositions(0)
        val card = getCheckedCard(b)
        MatcherAssert.assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("1"))
        b.rescheduleWithoutValidation(listOf(card.id), 5)
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Due of checked card after reschedule", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("8/12/20"))
    }

    @Test
    @Ignore("Doesn't work - but should")
    fun dataUpdatesAfterUndoReposition() {
        val b = getBrowserWithNotes(1)
        b.checkCardsAtPositions(0)
        val card = getCheckedCard(b)
        MatcherAssert.assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("1"))
        b.repositionCardsNoValidation(listOf(card.id), 2)
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("2"))
        b.onUndo()
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Position of checked card after undo should be reset", card.getColumnHeaderText(CardBrowser.Column.DUE), Is.`is`("1"))
    }

    @Test
    @Ignore("FLAKY: Robolectric getOptionsMenu does not require supportInvalidateOptionsMenu - so would not fail")
    fun rescheduleUndoTest() {
        val b = getBrowserWithNotes(1)
        assertUndoDoesNotContain(b, R.string.deck_conf_cram_reschedule)
        b.checkCardsAtPositions(0)
        b.rescheduleWithoutValidation(listOf(getCheckedCard(b).id), 2)
        advanceRobolectricLooperWithSleep()
        assertUndoContains(b, R.string.deck_conf_cram_reschedule)
    }

    @Test
    fun change_deck_dialog_is_dismissed_on_activity_recreation() {
        val cardBrowser = browserWithNoNewCards
        val dialog = cardBrowser.getChangeDeckDialog(ArrayList())
        cardBrowser.showDialogFragment(dialog)
        val shownDialog: Fragment? = getDialogFragment(cardBrowser)
        KotlinJunitAssert.assertNotNull(null, shownDialog)
        cardBrowser.recreate()
        val dialogAfterRecreate: Fragment? = getDialogFragment(cardBrowser)
        KotlinJunitAssert.assertNull(null, dialogAfterRecreate)
    }

    /** 8027  */
    @Test
    fun checkSearchString() {
        addNoteUsingBasicModel("Hello", "John")
        val deck = addDeck("Deck 1")
        col.decks.select(deck)
        val c2 = addNoteUsingBasicModel("New", "world").firstCard()
        c2.did = deck
        c2.flush()
        val cardBrowser = browserWithNoNewCards
        cardBrowser.searchCards("world or hello")
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Cardbrowser has Deck 1 as selected deck", cardBrowser.selectedDeckNameForUi, Is.`is`("Deck 1"))
        MatcherAssert.assertThat("Results should only be from the selected deck", cardBrowser.cardCount, Is.`is`(1))
    }

    /** PR #8553  */
    @Test
    fun checkDisplayOrderPersistence() {
        // Start the Card Browser with Basic Model
        ensureCollectionLoadIsSynchronous()
        var cardBrowserController = Robolectric.buildActivity(CardBrowser::class.java, Intent())
            .create().start().resume().visible()
        saveControllerForCleanup(cardBrowserController)
        advanceRobolectricLooperWithSleep()

        // Make sure card has default value in sortType field
        MatcherAssert.assertThat("Initially Card Browser has order = noteFld", col.get_config_string("sortType"), Is.`is`("noteFld"))

        // Store the current (before changing the database) Mod Time
        val initialMod = col.mod

        // Change the display order of the card browser
        cardBrowserController.get().changeCardOrder(7) // order no. 7 corresponds to "cardEase"

        // Kill and restart the activity and ensure that display order is preserved
        val outBundle = Bundle()
        cardBrowserController.saveInstanceState(outBundle)
        cardBrowserController.pause().stop().destroy()
        cardBrowserController = Robolectric.buildActivity(CardBrowser::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(cardBrowserController)

        // Find the current (after database has been changed) Mod time
        val finalMod = col.mod
        MatcherAssert.assertThat("Card Browser has the new sortType field", col.get_config_string("sortType"), Is.`is`("cardEase"))
        Assert.assertNotEquals("Modification time must change", initialMod, finalMod)
    }

    @Test
    fun checkIfLongSelectChecksAllCardsInBetween() {
        // #8467 - selecting cards outside the view pane (20) caused a crash as we were using view-based positions
        val browser = getBrowserWithNotes(25)
        selectOneOfManyCards(browser, 7) // HACK: Fix a bug in tests by choosing a value < 8
        selectOneOfManyCards(browser, 24)
        MatcherAssert.assertThat(browser.checkedCardCount(), Is.`is`(18))
    }

    @Test
    fun checkIfSearchAllDecksWorks() {
        addNoteUsingBasicModel("Hello", "World")
        val deck = addDeck("Test Deck")
        col.decks.select(deck)
        val c2 = addNoteUsingBasicModel("Front", "Back").firstCard()
        c2.did = deck
        c2.flush()
        val cardBrowser = browserWithNoNewCards
        cardBrowser.searchCards("Hello")
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Card browser should have Test Deck as the selected deck", cardBrowser.selectedDeckNameForUi, Is.`is`("Test Deck"))
        MatcherAssert.assertThat("Result should be empty", cardBrowser.cardCount, Is.`is`(0))
        cardBrowser.searchAllDecks()
        advanceRobolectricLooperWithSleep()
        MatcherAssert.assertThat("Result should contain one card", cardBrowser.cardCount, Is.`is`(1))
    }

    @KotlinCleanup("Change visibility to private")
    protected fun assertUndoDoesNotContain(browser: CardBrowser, @StringRes resId: Int) {
        val shadowActivity = Shadows.shadowOf(browser)
        val item = shadowActivity.optionsMenu.findItem(R.id.action_undo)
        val expected = browser.getString(resId)
        MatcherAssert.assertThat(item.title.toString(), Matchers.not(Matchers.containsString(expected.lowercase(Locale.getDefault()))))
    }

    @KotlinCleanup("Change visibility to private")
    protected fun assertUndoContains(browser: CardBrowser, @StringRes resId: Int) {
        val shadowActivity = Shadows.shadowOf(browser)
        val item = shadowActivity.optionsMenu.findItem(R.id.action_undo)
        val expected = browser.getString(resId)
        MatcherAssert.assertThat(item.title.toString(), Matchers.containsString(expected.lowercase(Locale.getDefault())))
    }

    private fun getCheckedCard(b: CardBrowser): CardCache {
        val ids = b.checkedCardIds
        MatcherAssert.assertThat("only one card expected to be checked", ids, Matchers.hasSize(1))
        return b.getPropertiesForCardId(ids[0])
    }

    private fun flagCardForNote(n: Note, flag: Int) {
        val c = n.firstCard()
        c.setUserFlag(flag)
        c.flush()
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
        val shadowActivity = Shadows.shadowOf(browser)
        val toSelect = shadowActivity.contentView.findViewById<ListView>(R.id.card_browser_list)

        // Robolectric doesn't easily seem to allow us to fire an onItemLongClick
        val listener = toSelect.onItemLongClickListener
            ?: throw IllegalStateException("no listener found")
        val childAt = toSelect.getChildAt(position)
        if (childAt == null) {
            Timber.w("Can't use childAt on position $position for a single click as it is not visible")
        }
        listener.onItemLongClick(
            null, childAt,
            position, toSelect.getItemIdAtPosition(position)
        )
    }

    private fun selectMenuItem(browser: CardBrowser, action_select_all: Int) {
        Timber.d("Selecting menu item")
        // select seems to run an infinite loop :/
        val shadowActivity = Shadows.shadowOf(browser)
        shadowActivity.clickMenuItem(action_select_all)
    }

    // There has to be a better way :(
    @Suppress("Unused")
    private fun toLongArray(list: List<Long>): LongArray {
        val ret = LongArray(list.size)
        for (i in ret.indices) {
            ret[i] = list[i]
        }
        return ret
    }

    private val browserWithMultipleNotes: CardBrowser
        get() = getBrowserWithNotes(3)

    private class CardBrowserSizeOne : CardBrowser() {
        override fun numCardsToRender(): Int {
            return 1
        }
    }

    private fun getBrowserWithNotes(count: Int): CardBrowser {
        return getBrowserWithNotes(count, CardBrowser::class.java)
    }

    private fun getBrowserWithNotes(count: Int, cardBrowserClass: Class<out CardBrowser>): CardBrowser {
        ensureCollectionLoadIsSynchronous()
        for (i in 0 until count) {
            addNoteUsingBasicModel(@KotlinCleanup("Replace with Kotlin toString()") Integer.toString(i), "back")
        }
        val multimediaController = Robolectric.buildActivity(cardBrowserClass, Intent())
            .create().start()
        multimediaController.resume().visible()
        saveControllerForCleanup(multimediaController)
        advanceRobolectricLooperWithSleep()
        return multimediaController.get()
    }

    private fun removeCardFromCollection(cardId: Long) {
        col.remCards(listOf(cardId))
    }

    @get:CheckReturnValue
    private val browserWithNoNewCards: CardBrowser
        get() {
            ensureCollectionLoadIsSynchronous()
            val multimediaController = Robolectric.buildActivity(CardBrowser::class.java, Intent())
                .create().start()
            multimediaController.resume().visible()
            saveControllerForCleanup(multimediaController)
            advanceRobolectricLooperWithSleep()
            return multimediaController.get()
        }

    // Regression test for #8821
    @Test
    fun emptyScroll() {
        val cardBrowser = getBrowserWithNotes(2)
        val renderOnScroll = cardBrowser.RenderOnScroll()
        renderOnScroll.onScroll(cardBrowser.mCardsListView!!, 0, 0, 2)
    }

    @Test
    fun searchCardsNumberOfResultCount() {
        val cardsToRender = 1
        val cardBrowser = getBrowserWithNotes(2, CardBrowserSizeOne::class.java)
        val task = SearchCards("", NoOrdering(), cardsToRender, 0, 0)
        TaskManager.launchCollectionTask(task, cardBrowser.SearchCardsHandler(cardBrowser))
        val cards = cardBrowser.cards
        MatcherAssert.assertThat(2, Is.`is`(cards.size()))
        Assert.assertTrue(cards[0].isLoaded)
        Assert.assertFalse(cards[1].isLoaded)
    }
}
