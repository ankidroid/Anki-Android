package com.ichi2.async.task;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONObject;

import timber.log.Timber;

public class RemoveTemplate extends Task {
    private final Model mModel;
    private final JSONObject mTemplate;

    public RemoveTemplate(Model model, JSONObject template) {
        mModel = model;
        mTemplate = template;
    }

    /**
     * Remove a card template. Note: it's necessary to call save model after this to re-generate the cards
     */
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundRemoveTemplate");
        Collection col = task.getCol();
        try {
            boolean success = col.getModels().remTemplate(mModel, mTemplate);
            if (! success) {
                return new TaskData("removeTemplateFailed", false);
            }
            col.save();
        } catch (ConfirmModSchemaException e) {
            Timber.e("doInBackgroundRemoveTemplate :: ConfirmModSchemaException");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
