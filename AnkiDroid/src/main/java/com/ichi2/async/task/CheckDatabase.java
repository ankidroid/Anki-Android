package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class CheckDatabase extends Task {
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundCheckDatabase");
        Collection col = task.getCol();
        // Don't proceed if collection closed
        if (col == null) {
            Timber.e("doInBackgroundCheckDatabase :: supplied collection was null");
            return new TaskData(false);
        }

        Collection.CheckDatabaseResult result = col.fixIntegrity(new CollectionTask.ProgressCallback(task, AnkiDroidApp.getAppResources()));
        if (result.getFailed()) {
            //we can fail due to a locked database, which requires knowledge of the failure.
            return new TaskData(false, new Object[] { result });
        } else {
            // Close the collection and we restart the app to reload
            CollectionHelper.getInstance().closeCollection(true, "Check Database Completed");
            return new TaskData(true, new Object[] { result });
        }
    }
}
