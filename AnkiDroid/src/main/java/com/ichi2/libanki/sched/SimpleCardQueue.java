package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import androidx.annotation.VisibleForTesting;

@VisibleForTesting
class SimpleCardQueue extends CardQueue<Card.Cache> {
    public SimpleCardQueue(AbstractSched sched) {
        super(sched);
    }

    public void add(long id) {
        add(new Card.Cache(getCol(), id));
    }
}
