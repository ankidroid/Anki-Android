package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Undoable;

import timber.log.Timber;

public class ChangeDeckMulti extends DismissMulti {
    private final long mNewDid;


    public ChangeDeckMulti(long[] cardsIDs, long newDid) {
        super(cardsIDs, Collection.DismissType.CHANGE_DECK_MULTI);
        mNewDid = newDid;
    }

    @Override
    public TaskData actualBackground(CollectionTask task, Card[] cards) {
        Collection col = task.getCol();

        Timber.i("Changing %d cards to deck: '%d'", cards.length, mNewDid);
        Deck deckData = col.getDecks().get(mNewDid);

        if (Decks.isDynamic(deckData)) {
            //#5932 - can't change to a dynamic deck. Use "Rebuild"
            Timber.w("Attempted to move to dynamic deck. Cancelling task.");
            return new TaskData(false);
        }

        //Confirm that the deck exists (and is not the default)
        try {
            long actualId = deckData.getLong("id");
            if (actualId != mNewDid) {
                Timber.w("Attempted to move to deck %d, but got %d", mNewDid, actualId);
                return new TaskData(false);
            }
        } catch (Exception e) {
            Timber.e(e, "failed to check deck");
            return new TaskData(false);
        }

        long[] changedCardIds = new long[cards.length];
        for (int i = 0; i < cards.length; i++) {
            changedCardIds[i] = cards[i].getId();
        }
        col.getSched().remFromDyn(changedCardIds);

        long[] originalDids = new long[cards.length];

        for (int i = 0; i < cards.length; i++) {
            Card card = cards[i];
            card.load();
            // save original did for undo
            originalDids[i] = card.getDid();
            // then set the card ID to the new deck
            card.setDid(mNewDid);
            Note note = card.note();
            note.flush();
            // flush card too, in case, did has been changed
            card.flush();
        }

        // mark undo for all at once
        col.markUndo(new Undoable.UndoableChangeDeckMulti(cards, originalDids));

        return null;
    }
}
