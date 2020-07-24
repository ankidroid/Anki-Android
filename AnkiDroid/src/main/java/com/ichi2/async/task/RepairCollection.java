package com.ichi2.async.task;

import com.ichi2.anki.BackupManager;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class RepairCollection extends Task {
    public RepairCollection() {
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundRepairCollection");
        Collection col = task.getCol();
        if (col != null) {
            Timber.i("RepairCollection: Closing collection");
            col.close(false);
        }
        return new TaskData(BackupManager.repairCollection(col));
    }
}
