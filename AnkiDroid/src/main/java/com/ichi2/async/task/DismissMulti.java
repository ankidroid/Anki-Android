package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Note;
import static com.ichi2.libanki.Undoable.*;
import com.ichi2.libanki.sched.AbstractSched;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import timber.log.Timber;

public abstract class DismissMulti extends Task {
    private final long[] mCardIDs;
    private final Collection.DismissType mType;

    protected DismissMulti(long[] cardsIDs, Collection.DismissType type) {
        mCardIDs = cardsIDs;
        mType = type;
    }

    protected long[] getCardIDs() {
        return mCardIDs;
    }

    protected Collection.DismissType getType() {
        return mType;
    }

    public abstract TaskData actualBackground(CollectionTask task, Card[] cards);

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        // query cards
        Card[] cards = new Card[mCardIDs.length];
        for (int i = 0; i < mCardIDs.length; i++) {
            cards[i] = col.getCard(mCardIDs[i]);
        }

        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                TaskData data = actualBackground(task, cards);
                if (data != null) {
                    return data;
                }
                col.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                col.getDb().getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSuspendCard");
            return new TaskData(false);
        }
        // pass cards back so more actions can be performed by the caller
        // (querying the cards again is unnecessarily expensive)
        return new TaskData(true, cards);
    }



    public Card[] deepCopyCardArray(CollectionTask task, Card[] originals) throws CancellationException {
        Collection col = CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance());
        Card[] copies = new Card[originals.length];
        for (int i = 0; i < originals.length; i++) {
            if (task.isCancelled()) {
                Timber.i("Cancelled during deep copy, probably memory pressure?");
                throw new CancellationException("Cancelled during deep copy");
            }

            // TODO: the performance-naive implementation loads from database instead of working in memory
            // the high performance version would implement .clone() on Card and test it well
            copies[i] = new Card(col, originals[i].getId());
        }
        return copies;
    }
}
