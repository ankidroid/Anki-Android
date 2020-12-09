package com.ichi2.anki;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ListView;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Deck;
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

import javax.annotation.CheckReturnValue;

import androidx.annotation.StringRes;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import timber.log.Timber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
public class CardBrowserTest extends RobolectricTest {

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithNoCards() {
        CardBrowser browser = getBrowserWithNoNewCards();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithCards() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void selectAllIsNotVisibleWhenNoCardsInDeck() {
        CardBrowser browser = getBrowserWithNoNewCards();
        assertThat(browser.isShowingSelectAll(), is(false));
    }

    @Test
    public void selectAllIsVisibleWhenCardsInDeck() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        assertThat(browser.cardCount(), greaterThan(0L));
        assertThat(browser.isShowingSelectAll(), is(true));
    }

    @Test
    public void selectAllIsNotVisibleOnceCalled() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectMenuItem(browser, R.id.action_select_all);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.isShowingSelectAll(), is(false));
    }

    @Test
    public void selectNoneIsVisibleOnceSelectAllCalled() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectMenuItem(browser, R.id.action_select_all);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.isShowingSelectNone(), is(true));
    }

    @Test
    public void selectNoneIsVisibleWhenSelectingOne() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        advanceRobolectricLooperWithSleep();
        selectOneOfManyCards(browser);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.isShowingSelectNone(), is(true));
    }

    @Test
    public void selectAllIsVisibleWhenSelectingOne() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectOneOfManyCards(browser);
        assertThat(browser.isShowingSelectAll(), is(true));
    }

    @Test
    public void browserIsInMultiSelectModeWhenSelectingOne() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectOneOfManyCards(browser);
        assertThat(browser.isInMultiSelectMode(), is(true));
    }

    @Test
    public void browserIsInMultiSelectModeWhenSelectingAll() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectMenuItem(browser, R.id.action_select_all);
        assertThat(browser.isInMultiSelectMode(), is(true));
    }

    @Test
    public void browserIsNotInMultiSelectModeWhenSelectingNone() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectMenuItem(browser, R.id.action_select_all);
        selectMenuItem(browser, R.id.action_select_none);
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void browserDoesNotFailWhenSelectingANonExistingCard() {
        //#5900
        CardBrowser browser = getBrowserWithNotes(6);
        //Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
        deleteCardAtPosition(browser, 0);
        AnkiAssert.assertDoesNotThrow(browser::rerenderAllCards);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.cardCount(), equalTo(5L));
    }


    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    public void selectionsAreCorrectWhenNonExistingCardIsRemoved() {
        CardBrowser browser = getBrowserWithNotes(7);
        browser.checkCardsAtPositions(1, 3, 5, 6);
        deleteCardAtPosition(browser, 2); //delete non-selected
        deleteCardAtPosition(browser, 3); //delete selected, ensure it's not still selected

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
        CardBrowser b = getBrowserWithNotes(5);

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
        CardBrowser b = getBrowserWithNotes(5);

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

        CardBrowser b = getBrowserWithNotes(5);

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
        selectDefaultDeck();
        CardBrowser b = getBrowserWithNotes(5);
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
        selectDefaultDeck();
        CardBrowser b = getBrowserWithNotes(5);
        b.checkCardsAtPositions(0, 2);

        List<Long> cardIds = b.getCheckedCardIds();

        b.executeChangeCollectionTask(cardIds, dynId);

        for (Long cardId: cardIds) {
            assertThat("Deck should not be changed", getCol().getCard(cardId).getDid(), not(dynId));
        }
    }

    @Test
    public void flagValueIsShownOnCard() {
        Note n = addNoteUsingBasicModel("1", "back");
        flagCardForNote(n, 1);

        long cardId = n.cids().get(0);

        CardBrowser b = getBrowserWithNoNewCards();
        CardBrowser.CardCache cardProperties = b.getPropertiesForCardId(cardId);


        int actualFlag = cardProperties.getCard().userFlag();

        assertThat("The card flag value should be reflected in the UI", actualFlag, is(1));
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

        CardBrowser b = getBrowserWithNoNewCards();
        b.filterByTag("sketchy::(1)");
        advanceRobolectricLooperWithSleep();

        assertThat("tagged card should be returned", b.getCardCount(), is(1));
    }

    @Test
    public void filterByFlagDisplaysProperly() {
        Note cardWithRedFlag = addNoteUsingBasicModel("Card with red flag", "Reverse");
        flagCardForNote(cardWithRedFlag, 1);

        Note cardWithGreenFlag = addNoteUsingBasicModel("Card with green flag", "Reverse");
        flagCardForNote(cardWithGreenFlag, 3);

        Note anotherCardWithRedFlag = addNoteUsingBasicModel("Second card with red flag", "Reverse");
        flagCardForNote(anotherCardWithRedFlag, 1);

        CardBrowser b = getBrowserWithNoNewCards();
        b.filterByFlag(1);
        advanceRobolectricLooperWithSleep();

        assertThat("Flagged cards should be returned", b.getCardCount(), is(2));
    }

    @Test
    public void previewWorksAfterSort() {
        // #7286
        long cid1 = addNoteUsingBasicModel("Hello", "World").cards().get(0).getId();
        long cid2 = addNoteUsingBasicModel("Hello2", "World2").cards().get(0).getId();

        CardBrowser b = getBrowserWithNoNewCards();

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

        CardBrowser b = getBrowserWithNoNewCards();

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

        CardBrowser b = getBrowserWithNoNewCards();

        assertThat("The target deck should not yet be selected", b.getLastDeckId(), not(is(targetDid)));

        b.selectDeckId(targetDid);

        assertThat("The target deck should be selected", b.getLastDeckId(), is(targetDid));

        Intent addIntent = b.getAddNoteIntent();

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, targetDid);
    }

    /** 7420 */
    @Test
    public void addCardDeckISetIfDeckIsSelectedOnOpen() {
        long initialDid = addDeck("NotDefault");

        getCol().getDecks().select(initialDid);

        CardBrowser b = getBrowserWithNoNewCards();

        assertThat("The initial deck should be selected", b.getLastDeckId(), is(initialDid));

        Intent addIntent = b.getAddNoteIntent();

        IntentAssert.hasExtra(addIntent, NoteEditor.EXTRA_DID, initialDid);
    }


    @Test
    public void repositionDataTest() {
        CardBrowser b = getBrowserWithNotes(1);

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

        CardBrowser b = getBrowserWithNoNewCards();

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
        CardBrowser b = getBrowserWithNotes(1);

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
        CardBrowser b = getBrowserWithNotes(1);

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
        CardBrowser b = getBrowserWithNotes(1);

        assertUndoDoesNotContain(b, R.string.deck_conf_cram_reschedule);

        b.checkCardsAtPositions(0);

        b.rescheduleWithoutValidation(Collections.singletonList(getCheckedCard(b).getId()), 2);

        advanceRobolectricLooperWithSleep();

        assertUndoContains(b, R.string.deck_conf_cram_reschedule);
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


    private void flagCardForNote(Note n, int flag) {
        Card c = n.firstCard();
        c.setUserFlag(flag);
        c.flush();
    }


    private void selectDefaultDeck() {
        getCol().getDecks().select(1);
    }

    private void deleteCardAtPosition(CardBrowser browser, int positionToCorrupt) {
        removeCardFromCollection(browser.getCardIds()[positionToCorrupt]);
        browser.clearCardData(positionToCorrupt);
    }

    private void selectOneOfManyCards(CardBrowser browser) {
        Timber.d("Selecting single card");
        ShadowActivity shadowActivity = shadowOf(browser);
        ListView toSelect = shadowActivity.getContentView().findViewById(R.id.card_browser_list);
        int position = 0;

        //roboelectric doesn't easily seem to allow us to fire an onItemLongClick
        AdapterView.OnItemLongClickListener listener = toSelect.getOnItemLongClickListener();
        if (listener == null)
            throw new IllegalStateException("no listener found");
        listener.onItemLongClick(null, toSelect.getChildAt(position),
                position, toSelect.getItemIdAtPosition(position));
    }


    private void selectMenuItem(CardBrowser browser, int action_select_all) {
        Timber.d("Selecting menu item");
        //select seems to run an infinite loop :/
        ShadowActivity shadowActivity = shadowOf(browser);
        shadowActivity.clickMenuItem(action_select_all);
    }

    //There has to be a better way :(
    private long[] toLongArray(List<Long> list){
        long[] ret = new long[list.size()];
        for(int i = 0; i < ret.length; i++) {
            ret[i] = list.get(i);
        }
        return ret;
    }


    private CardBrowser getBrowserWithMultipleNotes() {
        return getBrowserWithNotes(3);
    }


    private CardBrowser getBrowserWithNotes(int count) {
        ensureCollectionLoadIsSynchronous();
        for(int i = 0; i < count; i ++) {
            addNoteUsingBasicModel(Integer.toString(i), "back");
        }
        ActivityController<CardBrowser> multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start();
        multimediaController.resume().visible();
        saveControllerForCleanup(multimediaController);
        advanceRobolectricLooperWithSleep();
        return multimediaController.get();
    }

    private void removeCardFromCollection(Long cardId) {
        getCol().remCards(Collections.singletonList(cardId));
    }

    @CheckReturnValue
    private CardBrowser getBrowserWithNoNewCards() {
        ensureCollectionLoadIsSynchronous();
        ActivityController<CardBrowser> multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start();
        multimediaController.resume().visible();
        saveControllerForCleanup(multimediaController);
        advanceRobolectricLooperWithSleep();
        return multimediaController.get();
    }
}
