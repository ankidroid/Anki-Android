package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Note;

import timber.log.Timber;

public class AddNote extends Task {
    private final Note mNote;

    public AddNote(Note note) {
        mNote = note;
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundAddNote");
        Collection col = task.getCol();
        try {
            DB db = col.getDb();
            db.getDatabase().beginTransaction();
            try {
                task.doProgress(new TaskData(col.addNote(mNote)));
                db.getDatabase().setTransactionSuccessful();
            } finally {
                db.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundAddNote - RuntimeException on adding note");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAddNote");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
