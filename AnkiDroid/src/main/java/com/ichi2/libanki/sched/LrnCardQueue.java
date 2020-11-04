package com.ichi2.libanki.sched;

import java.util.Collections;
import java.util.PriorityQueue;

class LrnCardQueue extends CardQueue<LrnCard> {
    public LrnCardQueue(AbstractSched sched) {
        super(sched, new PriorityQueue<LrnCard>());
    }

    public void add(long due, long cid) {
        getQueue().add(new LrnCard(getCol(), due, cid));
    }

    public void add(LrnCard card) {
        getQueue().add(card);
    }

    public long getFirstDue() {
        return getQueue().peek().getDue();
    }
}
