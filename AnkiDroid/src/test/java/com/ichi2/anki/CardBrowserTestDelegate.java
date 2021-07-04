package com.ichi2.anki;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.errorprone.annotations.CheckReturnValue;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Note;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import timber.log.Timber;

import static com.ichi2.anki.RobolectricBackgroundTest.advanceRobolectricLooperWithSleep;
import static org.robolectric.Shadows.shadowOf;

public class CardBrowserTestDelegate {
    private final @NonNull
    RobolectricAbstractTest mRobolectric;


    public CardBrowserTestDelegate(@NonNull RobolectricAbstractTest robolectric) {
        mRobolectric = robolectric;
    }


    protected void flagCardForNote(Note n, int flag) {
        Card c = n.firstCard();
        c.setUserFlag(flag);
        c.flush();
    }


    protected void selectDefaultDeck() {
        mRobolectric.getCol().getDecks().select(1);
    }

    protected void deleteCardAtPosition(CardBrowser browser, int positionToCorrupt) {
        removeCardFromCollection(browser.getCardIds()[positionToCorrupt]);
        browser.clearCardData(positionToCorrupt);
    }

    protected void selectOneOfManyCards(CardBrowser cardBrowser) {
        selectOneOfManyCards(cardBrowser, 0);
    }

    protected void selectOneOfManyCards(CardBrowser browser, int position) {
        Timber.d("Selecting single card");
        ShadowActivity shadowActivity = shadowOf(browser);
        ListView toSelect = shadowActivity.getContentView().findViewById(R.id.card_browser_list);

        // Robolectric doesn't easily seem to allow us to fire an onItemLongClick
        AdapterView.OnItemLongClickListener listener = toSelect.getOnItemLongClickListener();
        if (listener == null) {
            throw new IllegalStateException("no listener found");
        }

        View childAt = toSelect.getChildAt(position);
        if (childAt == null) {
            Timber.w("Can't use childAt on position " + position + " for a single click as it is not visible");
        }
        listener.onItemLongClick(null, childAt,
                position, toSelect.getItemIdAtPosition(position));
    }


    protected void selectMenuItem(CardBrowser browser, int action_select_all) {
        Timber.d("Selecting menu item");
        //select seems to run an infinite loop :/
        ShadowActivity shadowActivity = shadowOf(browser);
        shadowActivity.clickMenuItem(action_select_all);
    }

    //There has to be a better way :(
    protected long[] toLongArray(List<Long> list){
        long[] ret = new long[list.size()];
        for(int i = 0; i < ret.length; i++) {
            ret[i] = list.get(i);
        }
        return ret;
    }


    protected CardBrowser getBrowserWithMultipleNotes() {
        return getBrowserWithNotes(3);
    }

    protected static class CardBrowserSizeOne extends CardBrowser {
        @Override
        protected int numCardsToRender() {
            return 1;
        }
    }

    protected CardBrowser getBrowserWithNotes(int count) {
        return getBrowserWithNotes(count, CardBrowser.class);
    }

    protected CardBrowser getBrowserWithNotes(int count, Class<? extends CardBrowser> cardBrowserClass) {
        mRobolectric.ensureCollectionLoadIsSynchronous();
        for(int i = 0; i < count; i ++) {
            mRobolectric.addNoteUsingBasicModel(Integer.toString(i), "back");
        }
        ActivityController<? extends CardBrowser> multimediaController = Robolectric.buildActivity(cardBrowserClass, new Intent())
                .create().start();
        multimediaController.resume().visible();
        mRobolectric.saveControllerForCleanup(multimediaController);
        advanceRobolectricLooperWithSleep();
        return multimediaController.get();
    }

    protected void removeCardFromCollection(Long cardId) {
        mRobolectric.getCol().remCards(Collections.singletonList(cardId));
    }

    @CheckReturnValue
    CardBrowser getBrowserWithNoNewCards() {
        mRobolectric.ensureCollectionLoadIsSynchronous();
        ActivityController<CardBrowser> multimediaController = Robolectric.buildActivity(CardBrowser.class, new Intent())
                .create().start();
        multimediaController.resume().visible();
        mRobolectric.saveControllerForCleanup(multimediaController);
        advanceRobolectricLooperWithSleep();
        return multimediaController.get();
    }


}
