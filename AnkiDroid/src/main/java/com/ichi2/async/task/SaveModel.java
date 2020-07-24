package com.ichi2.async.task;

import com.ichi2.anki.TemporaryModel;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONArray;

import java.lang.reflect.Array;
import java.util.ArrayList;

import timber.log.Timber;

public class SaveModel extends Task {
    private final Model mModel;
    private final ArrayList<Object[]> mTemplateChanges;

    public SaveModel(Model model, ArrayList<Object[]> templateChanges) {
        mModel = model;
        mTemplateChanges = templateChanges;
    }

    /**
     * Handles everything for a model change at once - template add / deletes as well as content updates
     */
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundSaveModel");
        Collection col = task.getCol();
        Model oldModel = col.getModels().get(mModel.getLong("id"));

        // TODO need to save all the cards that will go away, for undo
        //  (do I need to remove them from graves during undo also?)
        //    - undo (except for cards) could just be Models.update(model) / Models.flush() / Collection.reset() (that was prior "undo")
        JSONArray newTemplates = mModel.getJSONArray("tmpls");

        col.getDb().getDatabase().beginTransaction();

        try {
            for (Object[] change : mTemplateChanges) {
                JSONArray oldTemplates = oldModel.getJSONArray("tmpls");
                switch ((TemporaryModel.ChangeType) change[1]) {
                    case ADD:
                        Timber.d("doInBackgroundSaveModel() adding template %s", change[0]);
                        try {
                            col.getModels().addTemplate(oldModel, newTemplates.getJSONObject((int) change[0]));
                        } catch (Exception e) {
                            Timber.e(e, "Unable to add template %s to model %s", change[0], mModel.getLong("id"));
                            return new TaskData(e.getLocalizedMessage(), false);
                        }
                        break;
                    case DELETE:
                        Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0]);
                        try {
                            col.getModels().remTemplate(oldModel, oldTemplates.getJSONObject((int) change[0]));
                        } catch (Exception e) {
                            Timber.e(e, "Unable to delete template %s from model %s", change[0], mModel.getLong("id"));
                            return new TaskData(e.getLocalizedMessage(), false);
                        }
                        break;
                    default:
                        Timber.w("Unknown change type? %s", change[1]);
                        break;
                }
            }

            col.getModels().save(mModel, true);
            col.getModels().update(mModel);
            col.reset();
            col.save();
            if (col.getDb().getDatabase().inTransaction()) {
                col.getDb().getDatabase().setTransactionSuccessful();
            } else {
                Timber.i("CollectionTask::SaveModel was not in a transaction? Cannot mark transaction successful.");
            }
        } finally {
            if (col.getDb().getDatabase().inTransaction()) {
                col.getDb().getDatabase().endTransaction();
            } else {
                Timber.i("CollectionTask::SaveModel was not in a transaction? Cannot end transaction.");
            }
        }
        return new TaskData(true);
    }
}
