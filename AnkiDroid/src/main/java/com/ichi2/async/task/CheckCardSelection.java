package com.ichi2.async.task;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CheckCardSelection extends Task {
    private final Set<Integer> mCheckedCardPositions;
    private final List<Map<String, String>> mCards;

    public CheckCardSelection(Set<Integer> checkedCardPosition, List<Map<String, String>> cards) {
        mCheckedCardPositions = checkedCardPosition;
        mCards = cards;
    }

    /**
     * Goes through selected cards and checks selected and marked attribute
     * @return If there are unselected cards, if there are unmarked cards
     */
    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();

        boolean hasUnsuspended = false;
        boolean hasUnmarked = false;
        for (int cardPosition : mCheckedCardPositions) {
            Card card = col.getCard(Long.parseLong(mCards.get(cardPosition).get("id")));
            hasUnsuspended = hasUnsuspended || card.getQueue() != Consts.QUEUE_TYPE_SUSPENDED;
            hasUnmarked = hasUnmarked || !card.note().hasTag("marked");
            if (hasUnsuspended && hasUnmarked)
                break;
        }

        return new TaskData(new Object[] { hasUnsuspended, hasUnmarked});
    }
}
