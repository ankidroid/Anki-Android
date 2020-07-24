package com.ichi2.async.task;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class DeleteModel extends Task {
    private final long mModID;

    public DeleteModel(long modID) {
        mModID = modID;
    }

    /**
     * Deletes the given model (stored in the long field of TaskData)
     * and all notes associated with it
     */
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackGroundDeleteModel");
        Collection col = task.getCol();
        try {
            col.getModels().rem(col.getModels().get(mModID));
            col.save();
        } catch (ConfirmModSchemaException e) {
            Timber.e("doInBackGroundDeleteModel :: ConfirmModSchemaException");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
