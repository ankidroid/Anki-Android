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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.BrowserRow
import anki.search.BrowserRow.Color
import com.ichi2.anki.IntentHandler.Companion.grantedStoragePermissions
import com.ichi2.anki.RobolectricTest.Companion.waitForAsyncTasksToComplete
import com.ichi2.anki.browser.BrowserMultiColumnAdapter
import com.ichi2.anki.browser.BrowserMultiColumnAdapter.Companion.LINES_VISIBLE_WHEN_COLLAPSED
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserColumn.DECK
import com.ichi2.anki.browser.CardBrowserColumn.QUESTION
import com.ichi2.anki.browser.CardBrowserColumn.SFLD
import com.ichi2.anki.browser.CardBrowserColumn.TAGS
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModelTest
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.column1
import com.ichi2.anki.browser.setColumn
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.SortType
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeBrowserColumns.Companion.LEGACY_COLUMN1_KEYS
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeBrowserColumns.Companion.LEGACY_COLUMN2_KEYS
import com.ichi2.anki.utils.ext.getCurrentDialogFragment
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.libanki.BrowserConfig
import com.ichi2.libanki.CardId
import com.ichi2.libanki.CardType
import com.ichi2.libanki.Note
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.QueueType
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.TestClass
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.getSharedPrefs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class CardBrowserTest : RobolectricTest() {
    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithNoCards() =
        runTest {
            val browser = browserWithNoNewCards
            assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
        }

    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithCards() =
        runTest {
            val browser = browserWithMultipleNotes
            assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
        }

    @Test
    fun selectAllIsNotVisibleWhenNoCardsInDeck() =
        runTest {
            val browser = browserWithNoNewCards
            assertThat(browser.isShowingSelectAll, equalTo(false))
        }

    @Test
    fun selectAllIsVisibleWhenCardsInDeck() =
        runTest {
            val browser = browserWithMultipleNotes
            assertThat(browser.viewModel.rowCount, greaterThan(0))
            assertThat(browser.isShowingSelectAll, equalTo(true))
        }

    @Test
    fun selectAllIsNotVisibleOnceCalled() =
        runTest {
            val browser = browserWithMultipleNotes
            selectMenuItem(browser, R.id.action_select_all)
            assertThat(browser.isShowingSelectAll, equalTo(false))
        }

    @Test
    fun selectNoneIsVisibleOnceSelectAllCalled() =
        runTest {
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
    fun testOnDeckSelected() =
        withBrowser(noteCount = 1) {
            // Arrange
            val deckId = 123L
            val selectableDeck = DeckSelectionDialog.SelectableDeck(deckId, "Test Deck")

            // Act
            this.onDeckSelected(selectableDeck)

            // Assert
            assertEquals(deckId, this.lastDeckId)

            // Act again: select the same deck
            this.onDeckSelected(selectableDeck)

            // Assert again: the deck selection should not change
            assertEquals(deckId, this.lastDeckId)
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
    fun browserIsInMultiSelectModeWhenSelectingAll() =
        runTest {
            val browser = browserWithMultipleNotes
            selectMenuItem(browser, R.id.action_select_all)
            assertThat(browser.viewModel.isInMultiSelectMode, equalTo(true))
        }

    @Test
    fun browserIsNotInMultiSelectModeWhenSelectingNone() =
        runTest {
            val browser = browserWithMultipleNotes
            selectMenuItem(browser, R.id.action_select_all)
            selectMenuItem(browser, R.id.action_select_none)
            assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
        }

    @Test
    fun browserDoesNotFailWhenSelectingANonExistingCard() =
        runTest {
            // #5900
            val browser = getBrowserWithNotes(6)
            // Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
            deleteCardAtPosition(browser, 0)
            browser.rerenderAllCards()
            assertThat("the row stays visible", browser.viewModel.rowCount, equalTo(6))
            assertThat(
                "the row is displayed as deleted",
                browser
                    .getVisibleRows()
                    .first()
                    .columnViews[0]
                    .text,
                equalTo("(deleted)"),
            )
        }

    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    fun selectionsAreCorrectWhenNonExistingCardIsRemoved() =
        runTest {
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
                equalTo(3),
            )
            assertThat(
                "Checked card before should not have changed",
                browser.hasSelectedCardAtPosition(1),
                equalTo(true),
            )
            assertThat(
                "Checked card after should have changed by 2 places",
                browser.hasSelectedCardAtPosition(3),
                equalTo(true),
            )
            assertThat(
                "Checked card after should have changed by 2 places",
                browser.hasSelectedCardAtPosition(4),
                equalTo(true),
            )
        }

    @Test
    fun canChangeDeckToRegularDeck() =
        runTest {
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
    fun cannotChangeDeckToDynamicDeck() =
        runTest {
            // 5932 - dynamic decks are meant to have cards added to them through "Rebuild".
            addDynamicDeck("World")
            val b = getBrowserWithNotes(5)

            val decks = b.validDecksForChangeDeck

            if (decks.any { it.name == "World" }) {
                fail("Dynamic decks should not be transferred to by the browser.")
            }
        }

    @Test
    fun changeDeckIntegrationTestDynamicAndNon() =
        runTest {
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
    fun moveToNonDynamicDeckWorks() =
        runTest {
            addDeck("Foo")
            addDynamicDeck("Bar")
            val deckIdToChangeTo = addDeck("Hello")
            addDeck("ZZ")
            selectDefaultDeck()
            val b = getBrowserWithNotes(5)
            b.selectRowsWithPositions(0, 2)

            val cardIds = b.viewModel.queryAllSelectedCardIds()

            for (cardId in cardIds) {
                assertThat(
                    "Deck should have been changed yet",
                    col.getCard(cardId).did,
                    not(deckIdToChangeTo),
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
    fun `change deck does not work for dynamic decks`() =
        runTest {
            throwOnShowError = false

            val dynId = addDynamicDeck("World")
            selectDefaultDeck()
            val b = getBrowserWithNotes(5)
            b.selectRowsWithPositions(0, 2)

            val cardIds = b.viewModel.queryAllSelectedCardIds()

            b.moveSelectedCardsToDeck(dynId).join()

            for (cardId in cardIds) {
                assertThat("Deck should not be changed", col.getCard(cardId).did, not(dynId))
            }
        }

    @Test // see #13391
    fun newlyCreatedDeckIsShownAsOptionInBrowser() =
        runTest {
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
    fun flagsAreShownInBigDecksTest() =
        runTest {
            val numberOfNotes = 75
            val cardBrowser = getBrowserWithNotes(numberOfNotes)

            // select a random card
            val random = Random(1)
            val cardPosition = random.nextInt(numberOfNotes)
            assumeThat("card position to select is 60", cardPosition, equalTo(60))
            cardBrowser.selectRowsWithPositions(cardPosition)
            assumeTrue(
                "card at position 60 is selected",
                cardBrowser.hasSelectedCardAtPosition(cardPosition),
            )

            // flag the selected card
            cardBrowser.updateSelectedCardsFlag(Flag.RED)
            // check if card is red
            assertThat(
                "Card should be flagged",
                getCheckedCard(cardBrowser).color,
                equalTo(Color.COLOR_FLAG_RED),
            )

            // unflag the selected card
            cardBrowser.updateSelectedCardsFlag(Flag.NONE)
            // check if card flag is removed
            assertThat(
                "Card flag should be removed",
                getCheckedCard(cardBrowser).color,
                equalTo(Color.COLOR_DEFAULT),
            )

            // deselect and select all cards
            cardBrowser.viewModel.selectNone()
            cardBrowser.viewModel.selectAll()
            // flag all the cards as Green
            cardBrowser.updateSelectedCardsFlag(Flag.GREEN)
            // check if all card flags turned green
            assertThat(
                "All cards should be flagged",
                cardBrowser.viewModel
                    .queryAllCardIds()
                    .all { cardId -> getCardFlagAfterFlagChangeDone(cardId) == Flag.GREEN },
            )
        }

    @Test
    fun flagValueIsShownOnCard() {
        val n = addBasicNote("1", "back")
        flagCardForNote(n, Flag.RED)

        val cardId = n.cids()[0]

        col.backend.setActiveBrowserColumns(listOf("cardDue")) // arbitrary value
        val actualFlag = getCardFlagAfterFlagChangeDone(cardId)

        assertThat("The card flag value should be reflected in the UI", actualFlag, equalTo(Flag.RED))
    }

    private fun getCardFlagAfterFlagChangeDone(cardId: CardId): Flag {
        val data = col.browserRowForId(cardId)

        return when (data.color) {
            Color.COLOR_FLAG_BLUE -> Flag.BLUE
            Color.COLOR_FLAG_RED -> Flag.RED
            Color.COLOR_FLAG_GREEN -> Flag.GREEN
            Color.COLOR_DEFAULT -> throw IllegalStateException("no flag selected")
            else -> TODO("unhandled: ${data.color}")
        }
    }

    @Test
    fun startupFromCardBrowserActionItemShouldEndActivityIfNoPermissions() {
        try {
            mockkStatic(::isRunningAsUnitTest)
            mockkObject(IntentHandler)

            every { grantedStoragePermissions(any(), any()) } returns false
            every { isRunningAsUnitTest } returns false

            val browserController = Robolectric.buildActivity(CardBrowser::class.java).create()
            val cardBrowser = browserController.get()
            saveControllerForCleanup(browserController)

            assertThat("Activity should be finishing", cardBrowser.isFinishing)
        } finally {
            unmockkStatic(::isRunningAsUnitTest)
            unmockkObject(IntentHandler)
        }
    }

    @Test
    fun tagWithBracketsDisplaysProperly() =
        runTest {
            val n = addBasicNote("Hello", "World")
            n.addTag("sketchy::(1)")
            n.flush()

            val b = browserWithNoNewCards
            b.filterByTagSync("sketchy::(1)")

            assertThat("tagged card should be returned", b.viewModel.rowCount, equalTo(1))
        }

    @Test
    @Flaky(os = OS.WINDOWS, "IllegalStateException: Card '1596783600440' not found")
    fun previewWorksAfterSort() =
        runTest {
            // #7286
            val cid1 = addBasicNote("Hello", "World").cards()[0].id
            val cid2 = addBasicNote("Hello2", "World2").cards()[0].id

            val b = browserWithNoNewCards

            b.selectRowsWithPositions(0)
            val previewIntent = b.viewModel.queryPreviewIntentData()
            assertThat("before: index", previewIntent.currentIndex, equalTo(0))
            assertThat(
                "before: cards",
                previewIntent.previewerIdsFile.getCardIds(),
                equalTo(listOf(cid1, cid2)),
            )

            // reverse
            b.viewModel.changeCardOrder(SortType.SORT_FIELD)

            b.replaceSelectionWith(intArrayOf(0))
            val intentAfterReverse = b.viewModel.queryPreviewIntentData()
            assertThat("after: index", intentAfterReverse.currentIndex, equalTo(0))
            assertThat(
                "after: cards",
                intentAfterReverse.previewerIdsFile.getCardIds(),
                equalTo(listOf(cid2, cid1)),
            )
        }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelected() =
        runTest {
            val targetDid = addDeck("NotDefault")

            val b = browserWithNoNewCards

            assertThat(
                "The target deck should not yet be selected",
                b.lastDeckId,
                not(equalTo(targetDid)),
            )

            b.viewModel.setDeckId(targetDid)

            assertThat("The target deck should be selected", b.lastDeckId, equalTo(targetDid))

            val addIntent = b.addNoteIntent
            val bundle = addIntent.getBundleExtra(SingleFragmentActivity.FRAGMENT_ARGS_EXTRA)
            IntentAssert.hasExtra(bundle, NoteEditor.EXTRA_DID, targetDid)
        }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelectedOnOpen() {
        val initialDid = addDeck("NotDefault", setAsSelected = true)

        val b = browserWithNoNewCards

        assertThat("The initial deck should be selected", b.lastDeckId, equalTo(initialDid))

        val addIntent = b.addNoteIntent
        val bundle = addIntent.getBundleExtra(SingleFragmentActivity.FRAGMENT_ARGS_EXTRA)
        IntentAssert.hasExtra(bundle, NoteEditor.EXTRA_DID, initialDid)
    }

    @Test
    fun repositionDataTest() =
        runTest {
            val b = getBrowserWithNotes(1)

            b.selectRowsWithPositions(0)

            val card = getCheckedCard(b)

            assertThat(
                "Initial position of checked card",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                startsWith("New #\u20681\u2069"),
            )

            b.viewModel.repositionSelectedRows(2)

            assertThat(
                "Position of checked card after reposition",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("New #\u20682\u2069"),
            )
        }

    @Test
    @Config(qualifiers = "en")
    @SuppressLint("DirectCalendarInstanceUsage")
    fun resetDataTest() =
        runTest {
            TimeManager.reset()
            addBasicNote("Hello", "World").firstCard().update {
                due = 5
                queue = QueueType.Rev
                type = CardType.Rev
            }
            val cal = Calendar.getInstance()
            cal.add(Calendar.DATE, 5)
            val expectedDate = SimpleDateFormat("yyyy-MM-dd").format(Date(cal.timeInMillis))

            val b = browserWithNoNewCards
            b.selectRowsWithPositions(0)

            val card = getCheckedCard(b)

            assertThat(
                "Initial due of checked card",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo(expectedDate),
            )

            // simulate the user using the ForgetCardsDialog to start the cards reset process
            b.supportFragmentManager.setFragmentResult(
                ForgetCardsDialog.REQUEST_KEY_FORGET,
                bundleOf(
                    ForgetCardsDialog.ARG_RESTORE_ORIGINAL to true,
                    ForgetCardsDialog.ARG_RESET_REPETITION to false,
                ),
            )

            assertThat(
                "Position of checked card after reset",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("New #\u20682\u2069"),
            )
        }

    @Test
    @Ignore("Doesn't work - but should")
    fun dataUpdatesAfterUndoReposition() =
        runTest {
            val b = getBrowserWithNotes(1)

            b.selectRowsWithPositions(0)

            val card = getCheckedCard(b)

            assertThat(
                "Initial position of checked card",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("1"),
            )

            b.repositionCardsNoValidation(2)

            assertThat(
                "Position of checked card after reposition",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("2"),
            )

            b.onUndo()

            assertThat(
                "Position of checked card after undo should be reset",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("1"),
            )
        }

    @Test
    fun change_deck_dialog_is_dismissed_on_activity_recreation() {
        val cardBrowser = browserWithNoNewCards

        val dialog = cardBrowser.getChangeDeckDialog(listOf())
        cardBrowser.showDialogFragment(dialog)

        val shownDialog: Fragment? = cardBrowser.getCurrentDialogFragment()
        assertNotNull(shownDialog)

        ActivityCompat.recreate(cardBrowser)
        advanceRobolectricUiLooper()
        val dialogAfterRecreate: Fragment? = cardBrowser.getCurrentDialogFragment()
        assertNull(dialogAfterRecreate)
    }

    /** 8027  */
    @Test
    fun checkSearchString() =
        runTest {
            addBasicNote("Hello", "John")
            addBasicNote("New", "world").firstCard().update {
                did = addDeck("Deck 1", setAsSelected = true)
            }

            val cardBrowser = browserWithNoNewCards
            cardBrowser.searchCardsSync("world or hello")

            assertThat(
                "Cardbrowser has Deck 1 as selected deck",
                cardBrowser.selectedDeckNameForUi,
                equalTo("Deck 1"),
            )
            assertThat(
                "Results should only be from the selected deck",
                cardBrowser.viewModel.rowCount,
                equalTo(1),
            )
        }

    /** PR #8553  */
    @Test
    fun checkDisplayOrderPersistence() {
        // Start the Card Browser with Basic Model
        ensureCollectionLoadIsSynchronous()
        var cardBrowserController =
            Robolectric
                .buildActivity(CardBrowser::class.java, Intent())
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(cardBrowserController)

        // Make sure card has default value in sortType field
        assertThat(
            "Initially Card Browser has order = noteFld",
            col.config.get<String>("sortType"),
            equalTo("noteFld"),
        )

        // Change the display order of the card browser
        cardBrowserController.get().viewModel.changeCardOrder(SortType.EASE)

        // Kill and restart the activity and ensure that display order is preserved
        val outBundle = Bundle()
        cardBrowserController.saveInstanceState(outBundle)
        cardBrowserController.pause().stop().destroy()
        cardBrowserController =
            Robolectric
                .buildActivity(CardBrowser::class.java)
                .create(outBundle)
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(cardBrowserController)

        // Find the current (after database has been changed) Mod time

        val updatedMod = col.mod
        assertThat(
            "Card Browser has the new sortType field",
            col.config.get<String>("sortType"),
            equalTo("cardEase"),
        )
        assertNotEquals(0, updatedMod)
    }

    @Test
    fun checkIfLongSelectChecksAllCardsInBetween() =
        runTest {
            // #8467 - selecting cards outside the view pane (20) caused a crash as we were using view-based positions
            val browser = getBrowserWithNotes(25)
            selectOneOfManyCards(browser, 7) // HACK: Fix a bug in tests by choosing a value < 8
            selectOneOfManyCards(browser, 24)
            assertThat(browser.viewModel.selectedRowCount(), equalTo(18))
        }

    @Test
    fun checkIfSearchAllDecksWorks() =
        runTest {
            addBasicNote("Hello", "World")
            addBasicNote("Front", "Back").firstCard().update {
                did = addDeck("Test Deck", setAsSelected = true)
            }

            val cardBrowser = browserWithNoNewCards
            cardBrowser.searchCards("Hello")
            assertThat(
                "Card browser should have Test Deck as the selected deck",
                cardBrowser.selectedDeckNameForUi,
                equalTo("Test Deck"),
            )
            assertThat("Result should be empty", cardBrowser.viewModel.rowCount, equalTo(0))

            cardBrowser.searchAllDecks().join()
            waitForAsyncTasksToComplete()
            assertThat("Result should contain one card", cardBrowser.viewModel.rowCount, equalTo(1))
        }

    @Test
    fun `'notes-only mode' returns one card from each note`() =
        runTest {
            // #14623: The functionality was broken
            addBasicAndReversedNote("Hello", "World")
            addBasicAndReversedNote("Hello", "Anki")

            browserWithNoNewCards.apply {
                searchAllDecks().join()
                waitForAsyncTasksToComplete()
                with(viewModel) {
                    assertThat("Result should contain 4 cards", rowCount, equalTo(4))
                    setCardsOrNotes(NOTES).join()
                    waitForAsyncTasksToComplete()
                    assertThat("Result should contain 2 cards (one per note)", rowCount, equalTo(2))
                }
            }
        }

    /** PR #14859  */
    @Test
    fun checkDisplayOrderAfterTogglingCardsToNotes() {
        browserWithNoNewCards.apply {
            viewModel.changeCardOrder(SortType.EASE) // order no. 7 corresponds to "cardEase"
            viewModel.changeCardOrder(SortType.EASE) // reverse the list

            viewModel.setCardsOrNotes(NOTES)
            searchCards()

            assertThat(
                "Card Browser has the new noteSortType field",
                col.config.get<String>("noteSortType"),
                equalTo("cardEase"),
            )
            assertThat(
                "Card Browser has the new browserNoteSortBackwards field",
                col.config.get<Boolean>("browserNoteSortBackwards"),
                equalTo(true),
            )
        }
    }

    data class CheckedCardResult(
        val row: BrowserRow,
        val id: CardOrNoteId,
    ) {
        val color: Color get() = row.color
        val cardOrNoteId = id.cardOrNoteId
    }

    private fun getCheckedCard(b: CardBrowser): CheckedCardResult {
        assertThat(
            "only one card expected to be checked",
            b.viewModel.selectedRowCount(),
            equalTo(1),
        )
        val id = b.viewModel.selectedRows.single()
        val row = CollectionManager.getBackend().browserRowForId(id.cardOrNoteId)
        return CheckedCardResult(row, id)
    }

    private suspend fun deleteCardAtPosition(
        browser: CardBrowser,
        positionToCorrupt: Int,
    ) {
        val cid = browser.viewModel.queryCardIdAtPosition(positionToCorrupt)
        col.removeNotes(cids = listOf(cid))
    }

    private fun selectOneOfManyCards(
        browser: CardBrowser,
        position: Int = 0,
    ) {
        Timber.d("Selecting single card")
        browser.longClickRowAtPosition(position)
    }

    private fun selectMenuItem(
        browser: CardBrowser,
        actionSelectAll: Int,
    ) {
        Timber.d("Selecting menu item")
        // select seems to run an infinite loop :/
        val shadowActivity = shadowOf(browser)
        shadowActivity.clickMenuItem(actionSelectAll)
        advanceRobolectricUiLooper()
    }

    /** Returns an instance of [CardBrowser] containing [noteCount] notes */
    private fun getBrowserWithNotes(
        noteCount: Int,
        reversed: Boolean = false,
    ): CardBrowser {
        ensureCollectionLoadIsSynchronous()
        if (reversed) {
            for (i in 0 until noteCount) {
                addBasicAndReversedNote(i.toString(), "back")
            }
        } else {
            for (i in 0 until noteCount) {
                addBasicNote(i.toString(), "back")
            }
        }
        return super.startRegularActivity<CardBrowser>(Intent()).also {
            advanceRobolectricUiLooper() // may be a fix for flaky tests
        }
    }

    private val browserWithNoNewCards: CardBrowser
        get() = getBrowserWithNotes(0)

    private val browserWithMultipleNotes: CardBrowser
        get() = getBrowserWithNotes(3)

    @Test
    fun truncateAndExpand() =
        runTest {
            val cardBrowser = getBrowserWithNotes(3)
            cardBrowser.viewModel.setTruncated(true)
            waitForAsyncTasksToComplete()

            // Testing whether each card is truncated and ellipsized
            for (row in cardBrowser.getVisibleRows()) {
                val column1 = row.columnViews[0]
                val column2 = row.columnViews[1]

                // Testing truncation
                assertThat("col 1 max lines", column1.maxLines, equalTo(LINES_VISIBLE_WHEN_COLLAPSED))
                assertThat("col 2 max lines", column2.maxLines, equalTo(LINES_VISIBLE_WHEN_COLLAPSED))

                // Testing ellipses
                assertThat("column 1 ellipses", column1.ellipsize, equalTo(TextUtils.TruncateAt.END))
                assertThat("column 2 ellipses", column2.ellipsize, equalTo(TextUtils.TruncateAt.END))
            }

            cardBrowser.viewModel.setTruncated(false)
            waitForAsyncTasksToComplete()

            // Testing whether each card is expanded and not ellipsized
            for (row in cardBrowser.getVisibleRows()) {
                val column1 = row.columnViews[0]
                val column2 = row.columnViews[1]

                // Testing expansion
                assertThat("column 1 max lines", column1.maxLines, equalTo(Integer.MAX_VALUE))
                assertThat("column 2 max lines", column2.maxLines, equalTo(Integer.MAX_VALUE))

                // Testing not ellipsized
                assertThat("column 1 ellipsize", column1.ellipsize, nullValue())
                assertThat("column 2 ellipsize", column2.ellipsize, nullValue())
            }
        }

    @Test
    @Ignore("flaky")
    fun checkCardsNotesMode() =
        runTest {
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
    fun `column spinner positions are set if no preferences exist`() =
        runBlocking {
            // GIVEN: No shared preferences exist for display column selections
            getSharedPrefs().edit {
                remove("cardBrowserColumn1")
                remove("cardBrowserColumn2")
                remove(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY)
                remove(BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY)
            }

            // WHEN: CardBrowser is created
            val cardBrowser: CardBrowser = getBrowserWithNotes(5)

            // THEN: Display column selections should default to position 0
            assertThat(cardBrowser.columnHeadings[0], equalTo("Sort Field"))
            assertThat(cardBrowser.columnHeadings[1], equalTo("Card Type"))
        }

    @Test
    fun `column spinner positions are initially set from existing preferences`() =
        runTest {
            // GIVEN: Shared preferences exists for display column selections
            getSharedPrefs().edit {
                putString(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, "question|cardEase")
            }

            // WHEN: CardBrowser is created
            val cardBrowser: CardBrowser = getBrowserWithNotes(7)

            // THEN: The display column selections should match the shared preferences values
            assertThat(cardBrowser.columnHeadings[0], equalTo("Question"))
            assertThat(cardBrowser.columnHeadings[1], equalTo("Ease"))
        }

    @Test
    fun `column spinner positions are upgraded`() =
        runTest {
            // GIVEN: Shared preferences exists for display column selections

            // using legacy keys - test of PreferenceUpgradeService
            getSharedPrefs().edit {
                putInt("cardBrowserColumn1", 1)
                putInt("cardBrowserColumn2", 5)
            }

            // meta test
            assertThat(LEGACY_COLUMN1_KEYS[1], equalTo(SFLD))
            assertThat(LEGACY_COLUMN2_KEYS[5], equalTo(TAGS))

            PreferenceUpgradeService.upgradePreferences(getSharedPrefs(), 20300130)

            // WHEN: CardBrowser is created
            val cardBrowser: CardBrowser = getBrowserWithNotes(7)

            // THEN: The display column selections should match the shared preferences values
            assertThat(cardBrowser.columnHeadings[0], equalTo("Sort Field"))
            assertThat(cardBrowser.columnHeadings[1], equalTo("Tags"))

            assertThat("column 1 is cleared", !getSharedPrefs().all.containsKey("cardBrowserColumn1"))
            assertThat("column 2 is cleared", !getSharedPrefs().all.containsKey("cardBrowserColumn2"))
        }

    @Test
    fun `loading corrupt columns returns default`() {
        // GIVEN: Shared preferences exists for display column selections
        // with a corrupt value
        getSharedPrefs().edit {
            putString(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, "question|corrupt")
        }

        // WHEN: CardBrowser is created
        val cardBrowser: CardBrowser = getBrowserWithNotes(7)

        // In future, we may want to keep the 'question' value and only reset
        // the corrupt column.
        assertThat("column 1 reset to default", cardBrowser.columnHeadings[0], equalTo("Sort Field"))
        assertThat("column 2 reset to default", cardBrowser.columnHeadings[1], equalTo("Card Type"))
    }

    @Test
    @Ignore("issues with launchCollectionInLifecycleScope")
    fun `column titles update when moving to notes mode`() =
        withBrowser {
            viewModel.setColumn(0, CardBrowserColumn.INTERVAL)

            assertThat("spinner title: cards", columnHeadings[1], equalTo("Interval"))

            viewModel.setCardsOrNotes(NOTES)
            waitForAsyncTasksToComplete()

            assertThat("spinner title: notes", columnHeadings[1], equalTo("Avg. Interval"))
        }

    @Test
    fun `tapping row toggles state - Issue 14952`() =
        runTest {
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
    fun `deck id is remembered - issue 15072`() =
        runTest {
            // WARN: This doesn't mirror reality due to the use of coroutines
            // in the issue, selectDeckAndSave() was called AFTER the search had been performed
            // due to this being called immediately in a test-based context

            // We're going to move this functionality entirely to the ViewModel over the next few weeks
            // so this test should be updated and working after the refactorings are completed
            addBasicNote().moveToDeck("First")
            addBasicNote().moveToDeck("Second")

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
    fun `tts tags are parsed`() {
        addStandardNoteType(
            "test",
            arrayOf("Front", "Back"),
            "[anki:tts lang=de_DE voices=com.google.android.tts-de-DE-language]{{Front}}[/anki:tts]",
            "",
        ).let { name ->
            col.notetypes.byName(name)!!
        }.addNote("Test", "Blank")

        // PERF: the browser is not necessary
        val card =
            browserWithNoNewCards.let {
                it.viewModel.selectRowAtPosition(0)
                getCheckedCard(it)
            }
        val question = card.getColumnHeaderText(QUESTION)

        assertThat(question, equalTo(CardBrowserViewModelTest.EXPECTED_TTS))
    }

    @Test
    @Ignore("temporarily broken")
    fun `initial value is correct column`() {
        // Column 1 is [QUESTION, SFLD], the values when [SFLD] is selected

        addBasicAndReversedNote("Hello", "World")

        withBrowser {
            assertThat(viewModel.column1, equalTo(SFLD))

            assertThat(column1Text(row = 0), equalTo("Hello"))
            assertThat(column1Text(row = 1), equalTo("Hello"))
        }
    }

    @Test
    @Ignore(
        "issues with launchCollectionInLifecycleScope - provided value is not current" +
            "use an integration test",
    )
    fun `column text is updated - cardsOrNotes and column change`() {
        addBasicAndReversedNote("Hello", "World")

        withBrowser {
            assertThat("cards: original column", columnHeadings[1], equalTo("Card Type"))

            viewModel.setColumn(1, DECK)
            assertThat("cards: changed column", columnHeadings[1], equalTo("Deck"))

            viewModel.setCardsOrNotes(NOTES)
            waitForAsyncTasksToComplete()

            assertThat("notes: default column", columnHeadings[1], equalTo("Note Type"))
            viewModel.setColumn(1, DECK)
            assertThat("notes: changed column", columnHeadings[1], equalTo("Avg. Due"))

            viewModel.setCardsOrNotes(CARDS)
            assertThat("cards: updated column used", columnHeadings[1], equalTo("Deck"))
        }
    }

    fun NotetypeJson.addNote(
        field: String,
        vararg fields: String,
    ): Note = addNoteUsingNoteTypeName(this.name, field, *fields)

    private fun CheckedCardResult.getColumnHeaderText(header: CardBrowserColumn): String? {
        // There's currently a minimum of 2 columns
        col.backend.setActiveBrowserColumns(listOf(header.ankiColumnKey, "answer"))
        return col
            .browserRowForId(this.cardOrNoteId)
            .getCells(0)
            .text
    }

    @Suppress("SameParameterValue")
    private fun withBrowser(
        noteCount: Int = 0,
        block: suspend CardBrowser.() -> Unit,
    ) = runTest {
        getBrowserWithNotes(noteCount).apply {
            block(this)
        }
    }
}

private fun CardBrowser.rerenderAllCards() {
    cardsAdapter.notifyDataSetChanged()
    waitForAsyncTasksToComplete()
}

fun CardBrowser.hasSelectedCardAtPosition(i: Int): Boolean = viewModel.selectedRows.contains(viewModel.getRowAtPosition(i))

fun CardBrowser.replaceSelectionWith(positions: IntArray) {
    viewModel.selectNone()
    selectRowsWithPositions(*positions)
}

fun CardBrowser.column1Text(row: Int): CharSequence? = getVisibleRows()[row].columnViews[0].text

fun CardBrowser.selectRowsWithPositions(vararg positions: Int) {
    // PREF: inefficient as the card flow is updated each iteration
    positions.forEach { pos ->
        check(pos < viewModel.rowCount) {
            "Attempted to check row at index $pos. ${viewModel.rowCount} rows available"
        }
        viewModel.selectRowAtPosition(pos)
    }
}

fun CardBrowser.clickRowAtPosition(pos: Int) = onTap(viewModel.cards[pos])

fun CardBrowser.longClickRowAtPosition(pos: Int) = onLongPress(viewModel.cards[pos])

val CardBrowser.lastDeckId
    get() = viewModel.lastDeckId

val CardBrowser.validDecksForChangeDeck
    get() = runBlocking { getValidDecksForChangeDeck() }

suspend fun CardBrowser.searchCardsSync(query: String) {
    searchCards(query)
    viewModel.searchJob?.join()
}

suspend fun CardBrowser.filterByTagSync(vararg tags: String) {
    filterByTag(*tags)
    viewModel.searchJob?.join()
}

suspend fun CardBrowserViewModel.setFlagFilterSync(flag: Flag) {
    setFlagFilter(flag)
    searchJob?.join()
}

fun TestClass.flagCardForNote(
    n: Note,
    flag: Flag,
) {
    n.firstCard().update {
        setUserFlag(flag)
    }
}

fun CardBrowser.getVisibleRows() =
    sequence {
        for (i in 0 until (cardsListView.childCount)) {
            val row = cardsListView.getChildViewHolder(cardsListView.getChildAt(i))
            yield(row as BrowserMultiColumnAdapter.MultiColumnViewHolder)
        }
    }.toList().also {
        assertThat("has visible rows", it.any())
    }

val CardBrowser.isShowingSelectAll: Boolean
    get() {
        waitForAsyncTasksToComplete()
        return actionBarMenu?.findItem(R.id.action_select_all)?.isVisible == true
    }

val CardBrowser.isShowingSelectNone: Boolean
    get() {
        waitForAsyncTasksToComplete()
        return actionBarMenu?.findItem(R.id.action_select_none)?.isVisible == true
    }

val CardBrowser.columnHeadingViews
    get() =
        this.browserColumnHeadings.children
            .filterIsInstance<TextView>()
            .toList()

val CardBrowser.columnHeadings
    get() =
        columnHeadingViews.map { it.text.toString() }

fun CardBrowser.searchCards(search: String? = null) {
    if (search != null) {
        viewModel.launchSearchForCards(search)
    } else {
        viewModel.launchSearchForCards()
    }
    runBlocking { viewModel.searchJob?.join() }
}
