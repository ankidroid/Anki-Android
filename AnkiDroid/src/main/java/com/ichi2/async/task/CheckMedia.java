package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import java.util.List;

import timber.log.Timber;

public class CheckMedia extends Task {
    /**
     * @return The results list from the check, or false if any errors.
     */
    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundCheckMedia");
        // A media check on AnkiDroid will also update the media db
        col.getMedia().findChanges(true);
        // Then do the actual check
        List<List<String>> result = col.getMedia().check();
        return new TaskData(0, new Object[]{result}, true);
    }
}
