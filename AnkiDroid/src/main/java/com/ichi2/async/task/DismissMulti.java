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

public class DismissMulti extends Task {
    private final long[] mCardIDs;
    private final Collection.DismissType mType;
    private final Object mArg;

    public DismissMulti(long[] cardsIDs, Collection.DismissType type) {
        this(cardsIDs, type, null);
    }

    public DismissMulti(long[] cardsIDs, Collection.DismissType type, Object arg) {
        mCardIDs = cardsIDs;
        mType = type;
        mArg = arg;
    }

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
                switch (mType) {
                    case SUSPEND_CARD_MULTI: {
                        // collect undo information
                        long[] cids = new long[cards.length];
                        boolean[] originalSuspended = new boolean[cards.length];
                        boolean hasUnsuspended = false;
                        for (int i = 0; i < cards.length; i++) {
                            Card card = cards[i];
                            cids[i] = card.getId();
                            if (card.getQueue() != Consts.QUEUE_TYPE_SUSPENDED) {
                                hasUnsuspended = true;
                                originalSuspended[i] = false;
                            } else {
                                originalSuspended[i] = true;
                            }
                        }

                        // if at least one card is unsuspended -> suspend all
                        // otherwise unsuspend all
                        if (hasUnsuspended) {
                            sched.suspendCards(cids);
                        } else {
                            sched.unsuspendCards(cids);
                        }

                        // mark undo for all at once
                        col.markUndo(new UndoableSuspendCardMulti(cards, originalSuspended));

                        // reload cards because they'll be passed back to caller
                        for (Card c : cards) {
                            c.load();
                        }

                        sched.deferReset();
                        break;
                    }

                    case FLAG: {
                        int flag = (Integer) mArg;
                        col.setUserFlag(flag, mCardIDs);
                        for (Card c : cards) {
                            c.load();
                        }
                        break;
                    }

                    case MARK_NOTE_MULTI: {
                        Set<Note> notes = CardUtils.getNotes(Arrays.asList(cards));
                        // collect undo information
                        List<Note> originalMarked = new ArrayList<>();
                        List<Note> originalUnmarked = new ArrayList<>();

                        for (Note n : notes) {
                            if (n.hasTag("marked"))
                                originalMarked.add(n);
                            else
                                originalUnmarked.add(n);
                        }

                        CardUtils.markAll(new ArrayList<>(notes), !originalUnmarked.isEmpty());

                        // mark undo for all at once
                        col.markUndo(new UndoableMarkNoteMulti(originalMarked, originalUnmarked));

                        // reload cards because they'll be passed back to caller
                        for (Card c : cards) {
                            c.load();
                        }

                        break;
                    }

                    case DELETE_NOTE_MULTI: {
                        // list of all ids to pass to remNotes method.
                        // Need Set (-> unique) so we don't pass duplicates to col.remNotes()
                        Set<Note> notes = CardUtils.getNotes(Arrays.asList(cards));
                        List<Card> allCards = CardUtils.getAllCards(notes);
                        // delete note
                        long[] uniqueNoteIds = new long[notes.size()];
                        Note[] notesArr = notes.toArray(new Note[notes.size()]);
                        int count = 0;
                        for (Note note : notes) {
                            uniqueNoteIds[count] = note.getId();
                            count++;
                        }

                        col.markUndo(new UndoableDeleteNoteMulti(notesArr, allCards));

                        col.remNotes(uniqueNoteIds);
                        sched.deferReset();
                        // pass back all cards because they can't be retrieved anymore by the caller (since the note is deleted)
                        task.doProgress(new TaskData(allCards.toArray(new Card[allCards.size()])));
                        break;
                    }

                    case CHANGE_DECK_MULTI: {
                        long newDid = (long) mArg;

                        Timber.i("Changing %d cards to deck: '%d'", cards.length, newDid);
                        Deck deckData = col.getDecks().get(newDid);

                        if (Decks.isDynamic(deckData)) {
                            //#5932 - can't change to a dynamic deck. Use "Rebuild"
                            Timber.w("Attempted to move to dynamic deck. Cancelling task.");
                            return new TaskData(false);
                        }

                        //Confirm that the deck exists (and is not the default)
                        try {
                            long actualId = deckData.getLong("id");
                            if (actualId != newDid) {
                                Timber.w("Attempted to move to deck %d, but got %d", newDid, actualId);
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
                            card.setDid(newDid);
                            Note note = card.note();
                            note.flush();
                            // flush card too, in case, did has been changed
                            card.flush();
                        }

                        // mark undo for all at once
                        col.markUndo(new UndoableChangeDeckMulti(cards, originalDids));

                        break;
                    }

                    case RESCHEDULE_CARDS:
                    case REPOSITION_CARDS:
                    case RESET_CARDS: {
                        // collect undo information, sensitive to memory pressure, same for all 3 cases
                        try {
                            Timber.d("Saving undo information of type %s on %d cards", mType, cards.length);
                            col.markUndo(new UndoableRepositionRescheduleResetCards(mType, deepCopyCardArray(task, cards)));
                        } catch (CancellationException ce) {
                            Timber.i(ce, "Cancelled while handling type %s, skipping undo", mType);
                        }
                        switch (mType) {
                            case RESCHEDULE_CARDS:
                                sched.reschedCards(mCardIDs, (Integer) mArg, (Integer) mArg);
                                break;
                            case REPOSITION_CARDS:
                                sched.sortCards(mCardIDs, (Integer) mArg, 1, false, true);
                                break;
                            case RESET_CARDS:
                                sched.forgetCards(mCardIDs);
                                break;
                        }
                        // In all cases schedule a new card so Reviewer doesn't sit on the old one
                        col.reset();
                        task.doProgress(new TaskData(sched.getCard(), 0));
                        break;
                    }
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
