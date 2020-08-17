package com.ichi2.libanki.sched;

import com.ichi2.libanki.Collection;

import java.util.Collections;

class LrnCardQueue extends CardQueue<LrnCard> {
    public LrnCardQueue(AbstractSched sched) {
        super(sched);
    }

    public void add(long due, long cid) {
        add(new LrnCard(getCol(), due, cid));
    }

    public void add(int pos, LrnCard card) {
        getQueue().add(pos, card);
    }

    public void sort() {
        Collections.sort(getQueue());
    }

    public long getFirstDue() {
        return getQueue().getFirst().getDue();
    }
}
