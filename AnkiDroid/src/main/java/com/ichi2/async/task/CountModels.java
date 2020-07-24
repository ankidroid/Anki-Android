package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import timber.log.Timber;

public class CountModels extends Task {
     /*
     * Async task for the ModelBrowser Class
     * Returns an ArrayList of all models alphabetically ordered and the number of notes
     * associated with each model.
     *
     * @return {ArrayList<JSONObject> models, ArrayList<Integer> cardCount}
     */
    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundLoadModels");

        ArrayList<Model> models = col.getModels().all();
        ArrayList<Integer> cardCount = new ArrayList<>();
        Collections.sort(models, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return a.getString("name").compareTo(b.getString("name"));
            }
        });

        for (Model n : models) {
            if (task.isCancelled()) {
                Timber.e("doInBackgroundLoadModels :: Cancelled");
                // onPostExecute not executed if cancelled. Return value not used.
                return new TaskData(false);
            }
            cardCount.add(col.getModels().useCount(n));
        }

        Object[] data = new Object[2];
        data[0] = models;
        data[1] = cardCount;
        return (new TaskData(0, data, true));
    }
}
