package com.ichi2.async.task;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.AbstractSched;

public class RepositionCards extends RescheduleRepositionReset {
    private final int mStart;
    public RepositionCards(long[] cardsIDs,  int start) {
        super(cardsIDs, Collection.DismissType.RESET_CARDS);
        mStart = start;
    }
    protected void actualActualBackground(AbstractSched sched) {
        sched.sortCards(getCardIDs(), mStart, 1, false, true);
    }
}
