package com.ichi2.async.task;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONObject;

import timber.log.Timber;

public class DeleteField extends Task {
    private final Model mModel;
    private final JSONObject mField;

    public DeleteField(Model model, JSONObject field) {
        mModel = model;
        mField = field;
    }

    /**
     * Deletes thje given field in the given model
     */
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackGroundDeleteField");
        Collection col = task.getCol();
        try {
            col.getModels().remField(mModel, mField);
            col.save();
        } catch (ConfirmModSchemaException e) {
            //Should never be reached
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
