package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.sched.AbstractSched;

import timber.log.Timber;

public class AnswerCard extends Task {
    private final Card mOldCard;
    private final int mCurrentEase;
    public AnswerCard() {
        this(null, 0);
    }
    public AnswerCard(Card oldCard, int currentEase) {
        mOldCard = oldCard;
        mCurrentEase = currentEase;
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        Card newCard = null;
        Timber.i(mOldCard != null ? "Answering card" : "Obtaining card");
        try {
            DB db = col.getDb();
            db.getDatabase().beginTransaction();
            try {
                if (mOldCard != null) {
                    Timber.i("Answering card %d", mOldCard.getId());
                    sched.answerCard(mOldCard, mCurrentEase);
                }
                newCard = sched.getCard();
                if (newCard != null) {
                    // render cards before locking database
                    newCard._getQA(true);
                }
                task.doProgress(new TaskData(newCard));
                db.getDatabase().setTransactionSuccessful();
            } finally {
                db.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundAnswerCard - RuntimeException on answering card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAnswerCard");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
