/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import java.util.Collections;
import java.util.LinkedList;
import java.util.ListIterator;
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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

    public ListIterator<T> listIterator() {
        return mQueue.listIterator();
    }

    protected Collection getCol() {
        return mSched.getCol();
    }
}
