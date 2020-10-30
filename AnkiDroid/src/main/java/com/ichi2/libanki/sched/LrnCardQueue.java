package com.ichi2.libanki.sched;

import java.util.Collections;

import androidx.annotation.CheckResult;

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

    @CheckResult
    public long getFirstDue() {
        return getQueue().getFirst().getDue();
    }
}
