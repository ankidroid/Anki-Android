package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import androidx.annotation.VisibleForTesting;

@VisibleForTesting
class SimpleCardQueue extends CardQueue<Card.Cache> {

    /**
     * @param sched Constructor for empty queue. Nothing should be added to it
     */
    public SimpleCardQueue(AbstractSched sched) {
        super(sched);
    }

    public SimpleCardQueue(AbstractSched sched, List<Long> cards, boolean shuffle) {
        super(sched, toQueue(sched, cards, shuffle));
    }

    public static java.util.Queue<Card.Cache> toQueue(AbstractSched sched, List<Long> cids, boolean shuffle) {
        if (shuffle) {
            Random r = new Random();
            r.setSeed(sched.getToday());
            Collections.shuffle(cids, r);
        }
        java.util.Queue<Card.Cache> deque = new ArrayDeque<>(cids.size());
        for (long cid: cids) {
            deque.add(new Card.Cache(sched.mCol, cid));
        }
        return deque;
    }
}
