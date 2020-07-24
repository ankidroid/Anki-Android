package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class SaveCollection extends Task {
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundSaveCollection");
        Collection col = task.getCol();
        if (col != null) {
            try {
                col.save();
            } catch (RuntimeException e) {
                Timber.e(e, "Error on saving deck in background");
            }
        }
        return null;
    }
}
