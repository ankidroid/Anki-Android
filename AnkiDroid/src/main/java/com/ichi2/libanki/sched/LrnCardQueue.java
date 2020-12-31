package com.ichi2.libanki.sched;

import java.util.Collections;

class LrnCardQueue extends CardQueue<LrnCard> {
    /**
     * Whether the queue already contains its current expected value.
     * If it's not the case, then we won't add cards reviewed immediately and wait for a filling to occur.
     */
    private boolean mIsFilled = false;

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


    @Override
    public void clear() {
        super.clear();
        mIsFilled = false;
    }

    public void setFilled() {
        mIsFilled = true;
    }

    public boolean isFilled() {
        return mIsFilled;
    }
}
