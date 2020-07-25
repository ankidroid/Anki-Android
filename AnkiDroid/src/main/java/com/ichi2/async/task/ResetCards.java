package com.ichi2.async.task;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.AbstractSched;

public class ResetCards extends RescheduleRepositionReset {
    public ResetCards(long[] cardsIDs) {
        super(cardsIDs, Collection.DismissType.RESET_CARDS);
    }

    protected void actualActualBackground(AbstractSched sched) {
        sched.forgetCards(getCardIDs());
    }
}
