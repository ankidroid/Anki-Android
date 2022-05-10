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
import com.google.errorprone.annotations.CheckReturnValue
import com.ichi2.anki.CardBrowser.CardCache
import com.ichi2.async.CollectionTask.SearchCards
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.SortOrder.NoOrdering
import com.ichi2.testutils.AnkiActivityUtils.getDialogFragment
import com.ichi2.testutils.AnkiAssert
import com.ichi2.testutils.IntentAssert
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.core.Is.`is`
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.Arrays.stream
import java.util.Locale
import java.util.Random
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
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
    @RunInBackground
    fun selectAllIsNotVisibleOnceCalled() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        advanceRobolectricLooperWithSleep()
        assertThat(browser.isShowingSelectAll, equalTo(false))
    }

    @Test
    @RunInBackground
    fun selectNoneIsVisibleOnceSelectAllCalled() {
        val browser = browserWithMultipleNotes
        selectMenuItem(browser, R.id.action_select_all)
        advanceRobolectricLooperWithSleep()
        assertThat(browser.isShowingSelectNone, equalTo(true))
    }

    @Test
    @RunInBackground
    fun selectNoneIsVisibleWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        advanceRobolectricLooperWithSleep()
        selectOneOfManyCards(browser)
        advanceRobolectricLooperWithSleep()
        assertThat(browser.isShowingSelectNone, equalTo(true))
    }

    @Test
    fun selectAllIsVisibleWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        assertThat(browser.isShowingSelectAll, equalTo(true))
    }

    @Test
    fun browserIsInMultiSelectModeWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        assertThat(browser.isInMultiSelectMode, equalTo(true))
    }

    @Test
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
    fun browserDoesNotFailWhenSelectingANonExistingCard() {
        // #5900
        val browser = getBrowserWithNotes(6)
        // Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
        deleteCardAtPosition(browser, 0)
        AnkiAssert.assertDoesNotThrow { browser.rerenderAllCards() }
        advanceRobolectricLooperWithSleep()
        assertThat(browser.cardCount(), equalTo(5L))
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
        assertThat(browser.cardCount(), equalTo(6L))
        assertThat("A checked card should have been removed", browser.checkedCardCount(), equalTo(3))
        assertThat("Checked card before should not have changed", browser.hasCheckedCardAtPosition(1), equalTo(true))
        assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(3), equalTo(true))
        assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(4), equalTo(true))
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

    @KotlinCleanup(".any()")
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

        advanceRobolectricLooperWithSleep()

        val cardIds = b.checkedCardIds

        for (cardId in cardIds) {
            assertThat("Deck should have been changed yet", col.getCard(cardId).did, not(deckIdToChangeTo))
        }

        // act
        AnkiAssert.assertDoesNotThrow { b.moveSelectedCardsToDeck(deckIdToChangeTo) }

        // assert
        advanceRobolectricLooperWithSleep()
        for (cardId in cardIds) {
            assertThat("Deck should be changed", col.getCard(cardId).did, equalTo(deckIdToChangeTo))
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
            assertThat("Deck should not be changed", col.getCard(cardId).did, not(dynId))
        }
    }

    @KotlinCleanup("Remove .toLong(), needs additional changes")
    @Test
    fun flagsAreShownInBigDecksTest() {
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
        cardBrowser.flagTask(flag)
        advanceRobolectricLooperWithSleep()
        // check if card flag turned to flag = 1
        assertThat("Card should be flagged", getCheckedCard(cardBrowser).card.userFlag(), equalTo(flag))

        // unflag the selected card with flag = 0
        val unflagFlag = 0
        cardBrowser.flagTask(unflagFlag)
        advanceRobolectricLooperWithSleep()
        // check if card flag actually changed from flag = 1
        assertThat("Card flag should be removed", getCheckedCard(cardBrowser).card.userFlag(), not(flag))

        // deselect and select all cards
        cardBrowser.onSelectNone()
        cardBrowser.onSelectAll()
        // flag all the cards with flag = 3
        val flagForAll = 3
        cardBrowser.flagTask(flagForAll)
        advanceRobolectricLooperWithSleep()
        // check if all card flags turned to flag = 3
        assertThat(
            "All cards should be flagged",
            stream(cardBrowser.cardIds)
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

        assertThat("The card flag value should be reflected in the UI", actualFlag, equalTo(1))
    }

    private fun getCardFlagAfterFlagChangeDone(cardBrowser: CardBrowser, cardId: Long): Int {
        return cardBrowser.getPropertiesForCardId(cardId).card.userFlag()
    }

    @Test
    fun startupFromCardBrowserActionItemShouldEndActivityIfNoPermissions() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val app = shadowOf(application)
        app.denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        app.denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val inputIntent = Intent("android.intent.action.PROCESS_TEXT")

        val browserController = Robolectric.buildActivity(CardBrowser::class.java, inputIntent).create()
        val cardBrowser = browserController.get()
        saveControllerForCleanup(browserController)

        val shadowActivity = shadowOf(cardBrowser)
        val outputIntent = shadowActivity.nextStartedActivity
        val component = outputIntent.component

        assertThat(component, notNullValue())
        val componentName = component!!

        assertThat("Deck Picker currently handles permissions, so should be called", componentName.className, equalTo("com.ichi2.anki.DeckPicker"))
        assertThat("Activity should be finishing", cardBrowser.isFinishing)
        assertThat("Activity should be cancelled as it did nothing", shadowActivity.resultCode, equalTo(Activity.RESULT_CANCELED))
    }

    @Test
    fun tagWithBracketsDisplaysProperly() {
        val n = addNoteUsingBasicModel("Hello", "World")
        n.addTag("sketchy::(1)")
        n.flush()

        val b = browserWithNoNewCards
        b.filterByTag("sketchy::(1)")
        advanceRobolectricLooperWithSleep()

        assertThat("tagged card should be returned", b.cardCount, equalTo(1))
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

        assertThat("Flagged cards should be returned", b.cardCount, equalTo(2))
    }

    @Test
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

        advanceRobolectricLooperWithSleep()

        assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("2"))
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

        assertThat("Initial due of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("8/12/20"))

        b.resetProgressNoConfirm(listOf(card.id))

        advanceRobolectricLooperWithSleep()

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

        advanceRobolectricLooperWithSleep()

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

        advanceRobolectricLooperWithSleep()

        assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("2"))

        b.onUndo()

        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()

        assertThat("Position of checked card after undo should be reset", card.getColumnHeaderText(CardBrowser.Column.DUE), equalTo("1"))
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
        assertNotNull(shownDialog)

        cardBrowser.recreate()
        val dialogAfterRecreate: Fragment? = getDialogFragment(cardBrowser)
        assertNull(dialogAfterRecreate)
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

        assertThat("Cardbrowser has Deck 1 as selected deck", cardBrowser.selectedDeckNameForUi, equalTo("Deck 1"))
        assertThat("Results should only be from the selected deck", cardBrowser.cardCount, equalTo(1))
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
        assertThat("Initially Card Browser has order = noteFld", col.get_config_string("sortType"), equalTo("noteFld"))

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
        assertThat("Card Browser has the new sortType field", col.get_config_string("sortType"), equalTo("cardEase"))
        Assert.assertNotEquals("Modification time must change", initialMod, finalMod)
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
        assertThat("Card browser should have Test Deck as the selected deck", cardBrowser.selectedDeckNameForUi, equalTo("Test Deck"))
        assertThat("Result should be empty", cardBrowser.cardCount, equalTo(0))

        cardBrowser.searchAllDecks()
        advanceRobolectricLooperWithSleep()
        assertThat("Result should contain one card", cardBrowser.cardCount, equalTo(1))
    }

    protected fun assertUndoDoesNotContain(browser: CardBrowser, @StringRes resId: Int) {
        val shadowActivity = shadowOf(browser)
        val item = shadowActivity.optionsMenu.findItem(R.id.action_undo)
        val expected = browser.getString(resId)
        assertThat(item.title.toString(), not(containsString(expected.lowercase(Locale.getDefault()))))
    }

    protected fun assertUndoContains(browser: CardBrowser, @StringRes resId: Int) {
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
            null, childAt,
            position, toSelect.getItemIdAtPosition(position)
        )
    }

    private fun selectMenuItem(browser: CardBrowser, action_select_all: Int) {
        Timber.d("Selecting menu item")
        // select seems to run an infinite loop :/
        val shadowActivity = shadowOf(browser)
        shadowActivity.clickMenuItem(action_select_all)
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
            addNoteUsingBasicModel(Integer.toString(i), "back")
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
        assertThat(2, equalTo(cards.size()))
        assertTrue(cards[0].isLoaded)
        assertFalse(cards[1].isLoaded)
    }
}
