package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;

import timber.log.Timber;

public class AddField extends Task {
    private final Model mModel;
    private final String mFieldName;

    public AddField(Model model, String fieldName) {
        mModel = model;
        mFieldName = fieldName;
    }

    /**
     * Adds a field with name in given model
     */
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundRepositionField");
        Collection col = task.getCol();
        col.getModels().addFieldModChanged(mModel, col.getModels().newField(mFieldName));
        col.save();
        return new TaskData(true);
    }
}
