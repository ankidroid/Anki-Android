package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;

import androidx.annotation.VisibleForTesting;

class SimpleCardQueue extends CardQueue<Card.Cache> {
    public SimpleCardQueue(AbstractSched sched) {
        super(sched);
    }

    public void add(long id) {
        add(new Card.Cache(getCol(), id));
    }
}
