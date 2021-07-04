package com.ichi2.anki;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.AnkiAssert;
import com.ichi2.testutils.IntentAssert;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import androidx.annotation.StringRes;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static java.util.Arrays.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
public class CardBrowserForegroundTest extends RobolectricBackgroundTest {
    private final CardBrowserTestDelegate mDelegate = new CardBrowserTestDelegate(this);

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithNoCards() {
        CardBrowser browser = mDelegate.getBrowserWithNoNewCards();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithCards() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void selectAllIsNotVisibleWhenNoCardsInDeck() {
        CardBrowser browser = mDelegate.getBrowserWithNoNewCards();
        assertThat(browser.isShowingSelectAll(), is(false));
    }

    @Test
    public void selectAllIsVisibleWhenCardsInDeck() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        assertThat(browser.cardCount(), greaterThan(0L));
        assertThat(browser.isShowingSelectAll(), is(true));
    }

    @Test
    public void selectAllIsVisibleWhenSelectingOne() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        mDelegate.selectOneOfManyCards(browser);
        assertThat(browser.isShowingSelectAll(), is(true));
    }

    @Test
    public void browserIsInMultiSelectModeWhenSelectingOne() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        mDelegate.selectOneOfManyCards(browser);
        assertThat(browser.isInMultiSelectMode(), is(true));
    }

    @Test
    public void browserIsInMultiSelectModeWhenSelectingAll() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        mDelegate.selectMenuItem(browser, R.id.action_select_all);
        assertThat(browser.isInMultiSelectMode(), is(true));
    }

    @Test
    public void browserIsNotInMultiSelectModeWhenSelectingNone() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        mDelegate.selectMenuItem(browser, R.id.action_select_all);
        mDelegate.selectMenuItem(browser, R.id.action_select_none);
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void browserDoesNotFailWhenSelectingANonExistingCard() {
        //#5900
        CardBrowser browser = mDelegate.getBrowserWithNotes(6);
        //Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
        mDelegate.deleteCardAtPosition(browser, 0);
        AnkiAssert.assertDoesNotThrow(browser::rerenderAllCards);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.cardCount(), equalTo(5L));
    }


    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    public void selectionsAreCorrectWhenNonExistingCardIsRemoved() {
        CardBrowser browser = mDelegate.getBrowserWithNotes(7);
        browser.checkCardsAtPositions(1, 3, 5, 6);
        mDelegate.deleteCardAtPosition(browser, 2); //delete non-selected
        mDelegate.deleteCardAtPosition(browser, 3); //delete selected, ensure it's not still selected

        //ACT
        browser.rerenderAllCards();
        //ASSERT
        assertThat(browser.cardCount(), equalTo(6L));
        assertThat("A checked card should have been removed", browser.checkedCardCount(), equalTo(3));
        assertThat("Checked card before should not have changed", browser.hasCheckedCardAtPosition(1), is(true));
        assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(3), is(true));
        assertThat("Checked card after should have changed by 2 places", browser.hasCheckedCardAtPosition(4), is(true));
    }

    @Test
    public void canChangeDeckToRegularDeck() {
        addDeck("Hello");
        CardBrowser b = mDelegate.getBrowserWithNotes(5);

        List<Deck> decks = b.getValidDecksForChangeDeck();

        for (Deck d : decks) {
            if (d.getString("name").equals("Hello")) {
                return;
            }
        }
        Assert.fail("Added deck was not found in the Card Browser");
    }

    @Test
    public void cannotChangeDeckToDynamicDeck() {
        //5932 - dynamic decks are meant to have cards added to them through "Rebuild".
        addDynamicDeck("World");
        CardBrowser b = mDelegate.getBrowserWithNotes(5);

        List<Deck> decks = b.getValidDecksForChangeDeck();

        for (Deck d : decks) {
            if (d.getString("name").equals("World")) {
                Assert.fail("Dynamic decks should not be transferred to by the browser.");
            }
        }

    }

    @Test
    public void changeDeckIntegrationTestDynamicAndNon() {
        addDeck("Hello");
        addDynamicDeck("World");

        HashSet<String> validNames = new HashSet<>();
        validNames.add("Default");
        validNames.add("Hello");

        CardBrowser b = mDelegate.getBrowserWithNotes(5);

        List<Deck> decks = b.getValidDecksForChangeDeck();
        for (Deck d : decks) {
            assertThat(validNames, hasItem(d.getString("name")));
        }
        assertThat("Additional unexpected decks were present", decks.size(), is(2));
    }

    @Test
    public void moveToNonDynamicDeckWorks() {
        addDeck("Foo");
        addDynamicDeck("Bar");
        long deckIdToChangeTo = addDeck("Hello");
        addDeck("ZZ");
        mDelegate.selectDefaultDeck();
        CardBrowser b = mDelegate.getBrowserWithNotes(5);
        b.checkCardsAtPositions(0, 2);

        advanceRobolectricLooperWithSleep();

        List<Long> cardIds = b.getCheckedCardIds();

        for (Long cardId : cardIds) {
            assertThat("Deck should have been changed yet", getCol().getCard(cardId).getDid(), not(deckIdToChangeTo));
        }

        final int deckPosition = b.getChangeDeckPositionFromId(deckIdToChangeTo);

        //act
        AnkiAssert.assertDoesNotThrow(() -> b.moveSelectedCardsToDeck(deckPosition));

        //assert
        advanceRobolectricLooperWithSleep();
        for (Long cardId : cardIds) {
            assertThat("Deck should be changed", getCol().getCard(cardId).getDid(), is(deckIdToChangeTo));
        }
    }

    @Test
    public void changeDeckViaTaskIsHandledCorrectly() {
        long dynId = addDynamicDeck("World");
        mDelegate.selectDefaultDeck();
        CardBrowser b = mDelegate.getBrowserWithNotes(5);
        b.checkCardsAtPositions(0, 2);

        List<Long> cardIds = b.getCheckedCardIds();

        b.executeChangeCollectionTask(cardIds, dynId);

        for (Long cardId: cardIds) {
            assertThat("Deck should not be changed", getCol().getCard(cardId).getDid(), not(dynId));
        }
    }


    @Test
    public void flagsAreShownInBigDecksTest() {
        int numberOfNotes = 75;
        CardBrowser cardBrowser = mDelegate.getBrowserWithNotes(numberOfNotes);

        // select a random card
        Random random = new Random(1);
        int cardPosition = random.nextInt(numberOfNotes);
        assumeThat("card position to select is 60", cardPosition, is(60));
        cardBrowser.checkCardsAtPositions(cardPosition);
        assumeTrue("card at position 60 is selected", cardBrowser.hasCheckedCardAtPosition(cardPosition));

        // flag the selected card with flag = 1
        final int flag = 1;
        cardBrowser.flagTask(flag);
        advanceRobolectricLooperWithSleep();
        // check if card flag turned to flag = 1
        assertThat("Card should be flagged", getCheckedCard(cardBrowser).getCard().userFlag(), is(flag));

        // unflag the selected card with flag = 0
        final int unflagFlag = 0;
        cardBrowser.flagTask(unflagFlag);
        advanceRobolectricLooperWithSleep();
        // check if card flag actually changed from flag = 1
        assertThat("Card flag should be removed", getCheckedCard(cardBrowser).getCard().userFlag(), not(flag));

        // deselect and select all cards
        cardBrowser.onSelectNone();
        cardBrowser.onSelectAll();
        // flag all the cards with flag = 3
        final int flagForAll = 3;
        cardBrowser.flagTask(flagForAll);
        advanceRobolectricLooperWithSleep();
        // check if all card flags turned to flag = 3
        assertThat(
                "All cards should be flagged",
                stream(cardBrowser.getCardIds())
                        .map(cardId -> getCardFlagAfterFlagChangeDone(cardBrowser, cardId))
                        .noneMatch(flag1 -> flag1 != flagForAll)
        );
    }


    @Test
    public void flagValueIsShownOnCard() {
        Note n = addNoteUsingBasicModel("1", "back");
        mDelegate.flagCardForNote(n, 1);

        long cardId = n.cids().get(0);

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();

        int actualFlag = getCardFlagAfterFlagChangeDone(b, cardId);

        assertThat("The card flag value should be reflected in the UI", actualFlag, is(1));
    }


    private int getCardFlagAfterFlagChangeDone(CardBrowser cardBrowser, long cardId) {
        return cardBrowser.getPropertiesForCardId(cardId).getCard().userFlag();
    }


    @Test
    public void startupFromCardBrowserActionItemShouldEndActivityIfNoPermissions() {
        Application application = ApplicationProvider.getApplicationContext();
        ShadowApplication app = shadowOf(application);
        app.denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        app.denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        Intent inputIntent = new Intent("android.intent.action.PROCESS_TEXT");

        ActivityController<CardBrowser> browserController = Robolectric.buildActivity(CardBrowser.class, inputIntent).create();
        CardBrowser cardBrowser = browserController.get();
        saveControllerForCleanup(browserController);

        ShadowActivity shadowActivity = shadowOf(cardBrowser);
        Intent outputIntent = shadowActivity.getNextStartedActivity();
        ComponentName component = outputIntent.getComponent();

        assertThat(component, notNullValue());
        ComponentName componentName = Objects.requireNonNull(component);

        assertThat("Deck Picker currently handles permissions, so should be called", componentName.getClassName(), is("com.ichi2.anki.DeckPicker"));
        assertThat("Activity should be finishing", cardBrowser.isFinishing());
        assertThat("Activity should be cancelled as it did nothing", shadowActivity.getResultCode(), is(Activity.RESULT_CANCELED));
    }

    @Test
    public void tagWithBracketsDisplaysProperly() {
        Note n = addNoteUsingBasicModel("Hello", "World");
        n.addTag("sketchy::(1)");
        n.flush();

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();
        b.filterByTag("sketchy::(1)");
        advanceRobolectricLooperWithSleep();

        assertThat("tagged card should be returned", b.getCardCount(), is(1));
    }

    @Test
    public void filterByFlagDisplaysProperly() {
        Note cardWithRedFlag = addNoteUsingBasicModel("Card with red flag", "Reverse");
        mDelegate.flagCardForNote(cardWithRedFlag, 1);

        Note cardWithGreenFlag = addNoteUsingBasicModel("Card with green flag", "Reverse");
        mDelegate.flagCardForNote(cardWithGreenFlag, 3);

        Note anotherCardWithRedFlag = addNoteUsingBasicModel("Second card with red flag", "Reverse");
        mDelegate.flagCardForNote(anotherCardWithRedFlag, 1);

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();
        b.filterByFlag(1);
        advanceRobolectricLooperWithSleep();

        assertThat("Flagged cards should be returned", b.getCardCount(), is(2));
    }

    @Test
    public void previewWorksAfterSort() {
        // #7286
        long cid1 = addNoteUsingBasicModel("Hello", "World").cards().get(0).getId();
        long cid2 = addNoteUsingBasicModel("Hello2", "World2").cards().get(0).getId();

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();

        assertThat(b.getPropertiesForCardId(cid1).getPosition(), is(0));
        assertThat(b.getPropertiesForCardId(cid2).getPosition(), is(1));

        b.checkCardsAtPositions(0);
        Intent previewIntent = b.getPreviewIntent();
        assertThat("before: index", previewIntent.getIntExtra("index", -100), is(0));
        assertThat("before: cards", previewIntent.getLongArrayExtra("cardList"), is(new long[] { cid1, cid2 }));

        // reverse
        b.changeCardOrder(1);

        assertThat(b.getPropertiesForCardId(cid1).getPosition(), is(1));
        assertThat(b.getPropertiesForCardId(cid2).getPosition(), is(0));

        b.replaceSelectionWith(new int[] { 0 });
        Intent intentAfterReverse = b.getPreviewIntent();
        assertThat("after: index", intentAfterReverse.getIntExtra("index", -100), is(0));
        assertThat("after: cards", intentAfterReverse.getLongArrayExtra("cardList"), is(new long[] { cid2, cid1 }));
    }

    /** 7420 */
    @Test
    public void addCardDeckIsNotSetIfAllDecksSelectedAfterLoad() {
        addDeck("NotDefault");

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();

        assertThat("All decks should not be selected", b.hasSelectedAllDecks(), is(false));

        b.selectAllDecks();

        assertThat("All decks should be selected", b.hasSelectedAllDecks(), is(true));

        Intent addIntent = b.getAddNoteIntent();

        IntentAssert.doesNotHaveExtra(addIntent, NoteEditor.EXTRA_DID);
    }

    /** 7420 */
    @Test
    public void addCardDeckISetIfDeckIsSelected() {
        long targetDid = addDeck("NotDefault");

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();

        assertThat("The target deck should not yet be selected", b.getLastDeckId(), not(is(targetDid)));

        b.selectDeckAndSave(targetDid);

        assertThat("The target deck should be selected", b.getLastDeckId(), is(targetDid));

        Intent addIntent = b.getAddNoteIntent();

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, targetDid);
    }

    /** 7420 */
    @Test
    public void addCardDeckISetIfDeckIsSelectedOnOpen() {
        long initialDid = addDeck("NotDefault");

        getCol().getDecks().select(initialDid);

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();

        assertThat("The initial deck should be selected", b.getLastDeckId(), is(initialDid));

        Intent addIntent = b.getAddNoteIntent();

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, initialDid);
    }


    @Test
    public void repositionDataTest() {
        CardBrowser b = mDelegate.getBrowserWithNotes(1);

        b.checkCardsAtPositions(0);

        CardBrowser.CardCache card = getCheckedCard(b);

        assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), is("1"));

        b.repositionCardsNoValidation(Collections.singletonList(card.getId()), 2);

        advanceRobolectricLooperWithSleep();

        assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), is("2"));
    }

    @Test
    @Config(qualifiers = "en")
    public void resetDataTest() {
        Card c = addNoteUsingBasicModel("Hello", "World").firstCard();
        c.setDue(5);
        c.setQueue(Consts.QUEUE_TYPE_REV);
        c.setType(Consts.CARD_TYPE_REV);
        c.flush();

        CardBrowser b = mDelegate.getBrowserWithNoNewCards();

        b.checkCardsAtPositions(0);

        CardBrowser.CardCache card = getCheckedCard(b);

        assertThat("Initial due of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), is("8/12/20"));

        b.resetProgressNoConfirm(Collections.singletonList(card.getId()));

        advanceRobolectricLooperWithSleep();

        assertThat("Position of checked card after reset", card.getColumnHeaderText(CardBrowser.Column.DUE), is("1"));
    }

    @Test
    @Config(qualifiers = "en")
    public void rescheduleDataTest() {
        CardBrowser b = mDelegate.getBrowserWithNotes(1);

        b.checkCardsAtPositions(0);

        CardBrowser.CardCache card = getCheckedCard(b);

        assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), is("1"));

        b.rescheduleWithoutValidation(Collections.singletonList(card.getId()), 5);

        advanceRobolectricLooperWithSleep();

        assertThat("Due of checked card after reschedule", card.getColumnHeaderText(CardBrowser.Column.DUE), is("8/12/20"));
    }

    @Test
    @Ignore("Doesn't work - but should")
    public void dataUpdatesAfterUndoReposition() {
        CardBrowser b = mDelegate.getBrowserWithNotes(1);

        b.checkCardsAtPositions(0);

        CardBrowser.CardCache card = getCheckedCard(b);

        assertThat("Initial position of checked card", card.getColumnHeaderText(CardBrowser.Column.DUE), is("1"));

        b.repositionCardsNoValidation(Collections.singletonList(card.getId()), 2);

        advanceRobolectricLooperWithSleep();

        assertThat("Position of checked card after reposition", card.getColumnHeaderText(CardBrowser.Column.DUE), is("2"));

        b.onUndo();

        advanceRobolectricLooperWithSleep();
        advanceRobolectricLooperWithSleep();

        assertThat("Position of checked card after undo should be reset", card.getColumnHeaderText(CardBrowser.Column.DUE), is("1"));
    }

    @Test
    @Ignore("FLAKY: Robolectric getOptionsMenu does not require supportInvalidateOptionsMenu - so would not fail")
    public void rescheduleUndoTest() {
        CardBrowser b = mDelegate.getBrowserWithNotes(1);

        assertUndoDoesNotContain(b, R.string.deck_conf_cram_reschedule);

        b.checkCardsAtPositions(0);

        b.rescheduleWithoutValidation(Collections.singletonList(getCheckedCard(b).getId()), 2);

        advanceRobolectricLooperWithSleep();

        assertUndoContains(b, R.string.deck_conf_cram_reschedule);
    }

    /** 8027 */
    @Test
    public void checkSearchString() {
        addNoteUsingBasicModel("Hello", "John");
        long deck = addDeck("Deck 1");
        getCol().getDecks().select(deck);
        Card c2 = addNoteUsingBasicModel("New", "world").firstCard();
        c2.setDid(deck);
        c2.flush();

        CardBrowser cardBrowser = mDelegate.getBrowserWithNoNewCards();
        cardBrowser.searchCards("world or hello");
        advanceRobolectricLooperWithSleep();

        assertThat("Cardbrowser has Deck 1 as selected deck", cardBrowser.getSelectedDeckNameForUi(), is("Deck 1"));
        assertThat("Results should only be from the selected deck", cardBrowser.getCardCount(), is(1));
    }

    /** PR #8553 **/
    @Test
    public void checkDisplayOrderPersistence() {
        // Start the Card Browser with Basic Model
        ensureCollectionLoadIsSynchronous();
        ActivityController<CardBrowser> cardBrowserController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start().resume().visible();
        saveControllerForCleanup(cardBrowserController);
        advanceRobolectricLooperWithSleep();

        // Make sure card has default value in sortType field
        assertThat("Initially Card Browser has order = noteFld", getCol().getConf().get("sortType"), is("noteFld"));

        // Store the current (before changing the database) Mod Time
        long initialMod = getCol().getMod();

        // Change the display order of the card browser
        cardBrowserController.get().changeCardOrder(7);     // order no. 7 corresponds to "cardEase"

        // Kill and restart the activity and ensure that display order is preserved
        Bundle outBundle = new Bundle();
        cardBrowserController.saveInstanceState(outBundle);
        cardBrowserController.pause().stop().destroy();
        cardBrowserController = Robolectric.buildActivity(CardBrowser.class).create(outBundle).start().resume().visible();
        saveControllerForCleanup(cardBrowserController);

        // Find the current (after database has been changed) Mod time
        long finalMod = getCol().getMod();

        assertThat("Card Browser has the new sortType field", getCol().getConf().get("sortType"), is("cardEase"));
        Assert.assertNotEquals("Modification time must change", initialMod, finalMod);
    }

    @Test
    public void checkIfLongSelectChecksAllCardsInBetween() {
        // #8467 - selecting cards outside the view pane (20) caused a crash as we were using view-based positions
        CardBrowser browser = mDelegate.getBrowserWithNotes(25);
        mDelegate.selectOneOfManyCards(browser, 7); // HACK: Fix a bug in tests by choosing a value < 8
        mDelegate.selectOneOfManyCards(browser, 24);
        assertThat(browser.checkedCardCount(), is(18));
    }

    @Test
    public void checkIfSearchAllDecksWorks() {
        addNoteUsingBasicModel("Hello", "World");
        long deck = addDeck("Test Deck");
        getCol().getDecks().select(deck);
        Card c2 = addNoteUsingBasicModel("Front", "Back").firstCard();
        c2.setDid(deck);
        c2.flush();

        CardBrowser cardBrowser = mDelegate.getBrowserWithNoNewCards();
        cardBrowser.searchCards("Hello");
        advanceRobolectricLooperWithSleep();
        assertThat("Card browser should have Test Deck as the selected deck", cardBrowser.getSelectedDeckNameForUi(), is("Test Deck"));
        assertThat("Result should be empty", cardBrowser.getCardCount(), is(0));

        cardBrowser.searchAllDecks();
        advanceRobolectricLooperWithSleep();
        assertThat("Result should contain one card", cardBrowser.getCardCount(), is(1));
    }


    protected void assertUndoDoesNotContain(CardBrowser browser, @StringRes int resId) {
        ShadowActivity shadowActivity = shadowOf(browser);
        MenuItem item = shadowActivity.getOptionsMenu().findItem(R.id.action_undo);
        String expected = browser.getString(resId);
        assertThat(item.getTitle().toString(), not(containsString(expected.toLowerCase(Locale.getDefault()))));
    }

    protected void assertUndoContains(CardBrowser browser, @StringRes int resId) {
        ShadowActivity shadowActivity = shadowOf(browser);
        MenuItem item = shadowActivity.getOptionsMenu().findItem(R.id.action_undo);
        String expected = browser.getString(resId);
        assertThat(item.getTitle().toString(), containsString(expected.toLowerCase(Locale.getDefault())));
    }


    private CardBrowser.CardCache getCheckedCard(CardBrowser b) {
        List<Long> ids = b.getCheckedCardIds();
        assertThat("only one card expected to be checked", ids, hasSize(1));
        return b.getPropertiesForCardId(ids.get(0));
    }

    // Regression test for #8821
    @Test
    public void emptyScroll() {
        CardBrowser cardBrowser = mDelegate.getBrowserWithNotes(2);

        CardBrowser.RenderOnScroll renderOnScroll = cardBrowser.new RenderOnScroll();
        renderOnScroll.onScroll(cardBrowser.mCardsListView, 0, 0, 2);
    }

    @Test
    public void searchCardsNumberOfResultCount() {
        int cardsToRender = 1;


        CardBrowser cardBrowser = mDelegate.getBrowserWithNotes(2, CardBrowserTestDelegate.CardBrowserSizeOne.class);

        CollectionTask.SearchCards task = new CollectionTask.SearchCards("", false, cardsToRender, 0, 0);

        TaskManager.launchCollectionTask(task, cardBrowser.new SearchCardsHandler(cardBrowser));
        CardBrowser.CardCollection<CardBrowser.CardCache> cards = cardBrowser.getCards();
        assertThat(2, is(cards.size()));
        assertTrue(cards.get(0).isLoaded());
        assertFalse(cards.get(1).isLoaded());

    }
}
