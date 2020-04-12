package com.ichi2.anki;

import android.content.Intent;
import android.widget.AdapterView;
import android.widget.ListView;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Note;
import com.ichi2.testutils.AnkiAssert;
import com.ichi2.utils.JSONObject;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckReturnValue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import timber.log.Timber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
        assertThat(browser.isShowingSelectAll(), is(false));
    }

    @Test
    public void selectNoneIsVisibleOnceSelectAllCalled() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectMenuItem(browser, R.id.action_select_all);
        assertThat(browser.isShowingSelectNone(), is(true));
    }

    @Test
    public void selectNoneIsVisibleWhenSelectingOne() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        selectOneOfManyCards(browser);
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
        assertThat(browser.cardCount(), equalTo(5L));
    }


    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    public void selectionsAreCorrectWhenNonExistingCardIsRemoved() {
        CardBrowser browser = getBrowserWithNotes(7);
        browser.checkedCardsAtPositions(new int[] {1, 3, 5, 6});
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

        List<JSONObject> decks = b.getValidDecksForChangeDeck();

        for (JSONObject d : decks) {
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

        List<JSONObject> decks = b.getValidDecksForChangeDeck();

        for (JSONObject d : decks) {
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

        List<JSONObject> decks = b.getValidDecksForChangeDeck();
        for (JSONObject d : decks) {
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
        b.checkedCardsAtPositions(new int[] {0, 2});

        List<Long> cardIds = b.getCheckedCardIds();

        for (Long cardId : cardIds) {
            assertThat("Deck should have been changed yet", getCol().getCard(cardId).getDid(), not(deckIdToChangeTo));
        }

        final int deckPosition = b.getChangeDeckPositionFromId(deckIdToChangeTo);

        //act
        AnkiAssert.assertDoesNotThrow(() -> b.changeDeck(deckPosition));

        //assert
        for (Long cardId : cardIds) {
            assertThat("Deck should be changed", getCol().getCard(cardId).getDid(), is(deckIdToChangeTo));
        }
    }

    @Test
    public void changeDeckViaTaskIsHandledCorrectly() {
        long dynId = addDynamicDeck("World");
        selectDefaultDeck();
        CardBrowser b = getBrowserWithNotes(5);
        b.checkedCardsAtPositions(new int[] {0, 2});

        List<Long> cardIds = b.getCheckedCardIds();

        b.executeChangeCollectionTask(toLongArray(cardIds), dynId);

        for (Long cardId: cardIds) {
            assertThat("Deck should not be changed", getCol().getCard(cardId).getDid(), not(dynId));
        }
    }

    @Test
    public void flagValueIsShownOnCard() {
        Note n = addNote("1");
        flagCardForNote(n, 1);

        long cardId = n.cards().get(0).getId();

        CardBrowser b = getBrowserWithNoNewCards();
        Map<String, String> cardProperties = b.getPropertiesForCardId(cardId);

        int actualFlag = b.getFlagOrDefault(cardProperties, 0);

        assertThat("The card flag value should be reflected in the UI", actualFlag, is(1));
    }


    private void flagCardForNote(Note n, int flag) {
        Card c = n.cards().get(0);
        c.setUserFlag(flag);
        c.flush();
    }


    private void selectDefaultDeck() {
        getCol().getDecks().select(1);
    }


    private long addDynamicDeck(String name) {
        return getCol().getDecks().newDyn(name);
    }


    private long addDeck(String deckName) {
        return getCol().getDecks().id(deckName, true);
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
        for(int i = 0; i < count; i ++) {
            addNote(Integer.toString(i));
        }
        ActivityController multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start().resume().visible();
        return (CardBrowser) multimediaController.get();
    }

    private void removeCardFromCollection(Long cardId) {
        getCol().remCards(new long[] { cardId });
    }

    private Note addNote(String value) {
        Note n = getCol().newNote();
        n.setField(0, value);
        if (getCol().addNote(n) != 1) {
            throw new IllegalStateException("card was not added");
        }
        return n;
    }

    @CheckReturnValue
    private CardBrowser getBrowserWithNoNewCards() {
        ActivityController multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start().resume().visible();
        return (CardBrowser) multimediaController.get();
    }
}
