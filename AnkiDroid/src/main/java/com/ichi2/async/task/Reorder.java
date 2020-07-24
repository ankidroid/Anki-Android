package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DeckConfig;

import timber.log.Timber;

public class Reorder extends Task {
    private final DeckConfig mConf;

    public Reorder(DeckConfig conf) {
        mConf = conf;
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundReorder");
        Collection col = task.getCol();
        col.getSched().resortConf(mConf);
        return new TaskData(true);
    }
}
