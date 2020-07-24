package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONObject;

import timber.log.Timber;

public class AddTemplate extends Task {
    private final Model mModel;
    private final JSONObject mTemplate;

    public AddTemplate(Model model, JSONObject template) {
        mModel = model;
        mTemplate = template;
    }

    /**
     * Add a new card template
     */
    public TaskData background(CollectionTask task) {
        // mod should have been changed by addNewTemplateWithCheck in
        // main/java/com/ichi2/anki/CardTemplateEditor
        Timber.d("doInBackgroundAddTemplate");
        Collection col = task.getCol();
        // add the new template
        col.getModels().addTemplateModChanged(mModel, mTemplate);
        col.save();
        return new TaskData(true);
    }
}
