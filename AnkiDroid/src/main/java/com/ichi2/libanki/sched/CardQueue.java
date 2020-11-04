package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @param <T>
 */
abstract class CardQueue<T extends Card.Cache> {
    // We need to store mSched and not queue, because during initialization of sched, when CardQueues are initialized
    // sched.getCol is null.
    private final @NonNull AbstractSched mSched;
    private final @Nullable Queue<T> mQueue;

    /** Constructor for an empty queue. Nothing should be added to it.*/
    public CardQueue(AbstractSched sched) {
        mSched = sched;
        mQueue = new ArrayDeque<>(0);
    }

    public CardQueue(AbstractSched sched, Queue queue) {
        mSched = sched;
        mQueue = queue;
    }


    public void loadFirstCard() {
        if (!mQueue.isEmpty()) {
            // No nead to reload. If the card was changed, reset would have been called and emptied the queue
            mQueue.peek().loadQA(false, false);
        }
    }

    public Card removeFirstCard() throws NoSuchElementException {
        return mQueue.remove().getCard();
    }

    public boolean remove(long cid) {
        // CardCache and LrnCache with the same id will be considered as equal so it's a valid implementation.
        return mQueue.remove(new Card.Cache(getCol(), cid));
    }

    public void clear() {
        mQueue.clear();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isEmpty() {
        return mQueue.isEmpty();
    }

    public int size() {
        return mQueue.size();
    }

    protected java.util.Queue<T> getQueue() {
        return mQueue;
    }

    protected Collection getCol() {
        return mSched.getCol();
    }
}
