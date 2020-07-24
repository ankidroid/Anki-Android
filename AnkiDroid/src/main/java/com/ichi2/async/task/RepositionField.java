package com.ichi2.async.task;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONObject;

import timber.log.Timber;

public class RepositionField extends Task {
    private final Model mModel;
    private final JSONObject mField;
    private final int mIndex;

    public RepositionField(Model model, JSONObject field, int index) {
        mModel = model;
        mField = field;
        mIndex = index;
    }

    /**
     * Repositions the given field in the given model
     */
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundRepositionField");
        Collection col = task.getCol();
        try {
            col.getModels().moveField(mModel, mField, mIndex);
            col.save();
        } catch (ConfirmModSchemaException e) {
            //Should never be reached
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
