package com.ichi2.async.task;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

public class LoadCollectionComplete extends Task {
    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        if (col != null) {
            CollectionHelper.loadCollectionComplete(col);
        }
        return null;
    }
}
