package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.utils.JSONException;

import timber.log.Timber;

public class ConfRemove extends Task {
    private final DeckConfig mConf;

    public ConfRemove(DeckConfig conf) {
        mConf = conf;
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundConfRemove");
        try {
            // Note: We do the actual removing of the options group in the main thread so that we
            // can ask the user to confirm if they're happy to do a full sync, and just do the resorting here

            // When a conf is deleted, all decks using it revert to the default conf.
            // Cards must be reordered according to the default conf.
            int order = mConf.getJSONObject("new").getInt("order");
            int defaultOrder = col.getDecks().getConf(1).getJSONObject("new").getInt("order");
            if (order != defaultOrder) {
                mConf.getJSONObject("new").put("order", defaultOrder);
                col.getSched().resortConf(mConf);
            }
            col.save();
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }
}
