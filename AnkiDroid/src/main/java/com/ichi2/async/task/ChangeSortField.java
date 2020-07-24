package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;

import timber.log.Timber;

public class ChangeSortField extends Task {
    private final Model mModel;
    private final int mIdx;

    public ChangeSortField(Model model, int idx) {
        mModel = model;
        mIdx = idx;
    }

    /**
     * Adds a field of with name in given model
     */
    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        try {
            Timber.d("doInBackgroundChangeSortField");
            col.getModels().setSortIdx(mModel, mIdx);
            col.save();
        } catch(Exception e){
            Timber.e(e, "Error changing sort field");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
