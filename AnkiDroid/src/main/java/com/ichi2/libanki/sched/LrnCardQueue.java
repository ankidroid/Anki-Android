package com.ichi2.libanki.sched;

import java.util.Collections;

class LrnCardQueue extends CardQueue<LrnCard> {
    public LrnCardQueue(AbstractSched sched) {
        super(sched);
    }

    public void add(long due, long cid) {
        add(new LrnCard(getCol(), due, cid));
    }

    public void sort() {
        Collections.sort(getQueue());
    }

    public long getFirstDue() {
        return getQueue().getFirst().getDue();
    }
}
