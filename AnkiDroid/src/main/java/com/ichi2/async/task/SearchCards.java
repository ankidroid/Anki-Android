package com.ichi2.async.task;

import com.ichi2.anki.CardBrowser;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class SearchCards extends Task {
    private final String mQuery;
    private final boolean mOrder;
    private final int mNumCardsToRender;

    public SearchCards(String query, boolean order, int numCardToRender) {
        mQuery = query;
        mOrder = order;
        mNumCardsToRender = numCardToRender;
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundSearchCards");
        Collection col = task.getCol();
        List<Long> searchResult_ = col.findCards(mQuery, mOrder, task);
        if (task.isCancelled()) {
            Timber.d("doInBackgroundSearchCards was cancelled so return null");
            return null;
        }
        int resultSize = searchResult_.size();
        List<Map<String,String>> searchResult = new ArrayList<>(resultSize);
        Timber.d("The search found %d cards", resultSize);
        for (Long cid: searchResult_) {
            Map<String, String> card = new HashMap<>();
            card.put(CardBrowser.ID, cid.toString());
            searchResult.add(card);
        }
        // Render the first few items
        for (int i = 0; i < Math.min(mNumCardsToRender, searchResult.size()); i++) {
            if (task.isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null");
                return null;
            }
            Card c = col.getCard(Long.parseLong(searchResult.get(i).get("id")));
            CardBrowser.updateSearchItemQA(task.getContext(), searchResult.get(i), c, col);
        }
        // Finish off the task
        if (task.isCancelled()) {
            Timber.d("doInBackgroundSearchCards was cancelled so return null");
            return null;
        } else {
            return new TaskData(searchResult);
        }
    }

}
