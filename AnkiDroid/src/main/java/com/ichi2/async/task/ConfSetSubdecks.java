package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.utils.JSONException;

import java.util.Map;
import java.util.TreeMap;

import timber.log.Timber;

public class ConfSetSubdecks extends Task {
    private final Deck mDeck;
    private final DeckConfig mConf;

    public ConfSetSubdecks(Deck deck, DeckConfig conf) {
        mDeck = deck;
        mConf = conf;
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundConfSetSubdecks");
        try {
            TreeMap<String, Long> children = col.getDecks().children(mDeck.getLong("id"));
            for (Map.Entry<String, Long> entry : children.entrySet()) {
                Deck child = col.getDecks().get(entry.getValue());
                if (child.getInt("dyn") == 1) {
                    continue;
                }
                boolean changed = new ConfChange(child, mConf).background(task).getBoolean();
                if (!changed) {
                    return new TaskData(false);
                }
            }
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }
}
