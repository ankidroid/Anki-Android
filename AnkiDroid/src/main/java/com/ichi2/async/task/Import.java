package com.ichi2.async.task;

import android.content.res.Resources;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.importer.AnkiPackageImporter;

import timber.log.Timber;

public class Import extends Task {
    private final String mPath;

    public Import(String path) {
        mPath = path;
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundImportAdd");
        Collection col = task.getCol();
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        AnkiPackageImporter imp = new AnkiPackageImporter(col, mPath);
        imp.setProgressCallback(new CollectionTask.ProgressCallback(task, res));
        try {
            imp.run();
        } catch (ImportExportException e) {
            return new TaskData(e.getMessage(), true);
        }
        return new TaskData(new Object[] {imp});
    }
}
