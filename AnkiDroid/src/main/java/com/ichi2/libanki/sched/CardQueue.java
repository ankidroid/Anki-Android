package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Random;

abstract class CardQueue<T extends Card.Cache> {
    // We need to store mSched and not queue, because during initialization of sched, when CardQueues are initialized
    // sched.getCol is null.
    private final AbstractSched mSched;
    private final LinkedList<T> mQueue = new LinkedList<>();


    public CardQueue(AbstractSched sched) {
        mSched = sched;
    }


    public void loadFirstCard() {
        if (!mQueue.isEmpty()) {
            // No nead to reload. If the card was changed, reset would have been called and emptied the queue
            mQueue.get(0).loadQA(false, false);
        }
    }

    public Card removeFirstCard() throws NoSuchElementException {
        return mQueue.remove().getCard();
    }

    public boolean remove(long cid) {
        // CardCache and LrnCache with the same id will be considered as equal so it's a valid implementation.
        return mQueue.remove(new Card.Cache(getCol(), cid));
    }

    public void add(T elt) {
        mQueue.add(elt);
    }

    public void clear() {
        mQueue.clear();
    }

    public boolean isEmpty() {
        return mQueue.isEmpty();
    }

    public int size() {
        return mQueue.size();
    }

    protected LinkedList<T> getQueue() {
        return mQueue;
    }

    public void shuffle(Random r) {
        Collections.shuffle(mQueue, r);
    }

    public Iterator<T> listIterator() {
        return mQueue.listIterator();
    }

    protected Collection getCol() {
        return mSched.getCol();
    }
}
