package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.utils.JSONException;

import timber.log.Timber;

public class ConfChange extends Task {
    private final Deck mDeck;
    private final DeckConfig mConf;

    public ConfChange(Deck deck, DeckConfig conf) {
        mDeck = deck;
        mConf = conf;
    }

    public TaskData background(CollectionTask task) {
        return background(task, mDeck, mConf);
    }

    /* Allow this method to be called by other class with specific name without creating a task. */
    public TaskData background(CollectionTask task, Deck deck, DeckConfig conf) {
        Timber.d("doInBackgroundConfChange");
        Collection col = task.getCol();
        try {
            long newConfId = conf.getLong("id");
            // If new config has a different sorting order, reorder the cards
            int oldOrder = col.getDecks().getConf(deck.getLong("conf")).getJSONObject("new").getInt("order");
            int newOrder = col.getDecks().getConf(newConfId).getJSONObject("new").getInt("order");
            if (oldOrder != newOrder) {
                switch (newOrder) {
                    case 0:
                        col.getSched().randomizeCards(deck.getLong("id"));
                        break;
                    case 1:
                        col.getSched().orderCards(deck.getLong("id"));
                        break;
                }
            }
            col.getDecks().setConf(deck, newConfId);
            col.save();
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }
}
