package com.ichi2.async.task;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.AbstractSched;

public class RescheduleCards extends RescheduleRepositionReset {
    private final int mStart;
    public RescheduleCards(long[] cardsIDs,  int start) {
        super(cardsIDs, Collection.DismissType.RESET_CARDS);
        mStart = start;
    }
    protected void actualActualBackground(AbstractSched sched) {
        sched.reschedCards(getCardIDs(), mStart, mStart);
    }
}
