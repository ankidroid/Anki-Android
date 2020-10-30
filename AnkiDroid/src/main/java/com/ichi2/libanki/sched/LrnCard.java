package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import androidx.annotation.CheckResult;

class LrnCard extends Card.Cache implements Comparable<LrnCard> {
    private final long mDue;
    public LrnCard(Collection col, long due, long cid) {
        super(col, cid);
        mDue = due;
    }

    @CheckResult
    public long getDue () {
        return mDue;
    }

    @Override
    @CheckResult
    public int compareTo(LrnCard o) {
        return Long.compare(mDue, o.mDue);
    }
}
