package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DeckConfig;

import timber.log.Timber;

public class ConfReset extends Task {
    private final DeckConfig mConf;

    public ConfReset(DeckConfig conf) {
        mConf = conf;
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundConfReset");
        col.getDecks().restoreToDefault(mConf);
        col.save();
        return new TaskData(true);
    }
}
