package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

public class Flag extends DismissMulti {
    private final int mFlag;


    public Flag(long[] cardsIDs, int flag) {
        super(cardsIDs, Collection.DismissType.FLAG);
        mFlag = flag;
    }

    @Override
    public TaskData actualBackground(CollectionTask task, Card[] cards) {
        Collection col = task.getCol();
        col.setUserFlag(mFlag, getCardIDs());
        for (Card c : cards) {
            c.load();
        }
        return null;
    }
}
