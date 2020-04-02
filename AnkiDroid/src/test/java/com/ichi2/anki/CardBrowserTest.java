package com.ichi2.anki;

import android.content.Intent;
import android.widget.AdapterView;
import android.widget.ListView;

import com.ichi2.libanki.Note;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import javax.annotation.CheckReturnValue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import timber.log.Timber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
public class CardBrowserTest extends RobolectricTest {

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithNoCards() {
        CardBrowser browser = getBrowserWithNoCards();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void browserIsNotInitiallyInMultiSelectModeWithCards() {
        CardBrowser browser = getBrowserWithMultipleNotes();
        assertThat(browser.isInMultiSelectMode(), is(false));
    }

    @Test
    public void selectAllIsNotVisibleWhenNoCardsInDeck() {
        CardBrowser browser = getBrowserWithNoCards();
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


    private void addNote(String value) {
        Note n = getCol().newNote();
        n.setField(0, value);
        if (getCol().addNote(n) != 1) {
            throw new IllegalStateException("card was not added");
        }
    }

    @CheckReturnValue
    private CardBrowser getBrowserWithNoCards() {
        ActivityController multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start().resume().visible();
        return (CardBrowser) multimediaController.get();
    }
}
