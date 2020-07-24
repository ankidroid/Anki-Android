package com.ichi2.async.task;

import com.ichi2.anki.CardBrowser;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.WrongId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class RenderBrowserQA extends Task {
    private final List<Map<String, String>> mCards;
    private final int mStartPos;
    private final int mN;

    public RenderBrowserQA(List<Map<String, String>> cards, int startPos, int n) {
        mCards = cards;
        mStartPos = startPos;
        mN = n;
    }

    public TaskData background(CollectionTask task) {
        //TODO: Convert this to accept the following to make thread-safe:
        //(Range<Position>, Function<Position, BrowserCard>)
        Timber.d("doInBackgroundRenderBrowserQA");
        Collection col = task.getCol();

        List<Long> invalidCardIds = new ArrayList<>();
        // for each specified card in the browser list
        for (int i = mStartPos; i < mStartPos + mN; i++) {
            // Stop if cancelled
            if (task.isCancelled()) {
                Timber.d("doInBackgroundRenderBrowserQA was aborted");
                return null;
            }
            if (i < 0 || i >= mCards.size()) {
                continue;
            }
            Map<String, String> card;
            try {
                card = mCards.get(i);
            }
            catch (IndexOutOfBoundsException e) {
                //even though we test against card.size() above, there's still a race condition
                //We might be able to optimise this to return here. Logically if we're past the end of the collection,
                //we won't reach any more cards.
                continue;
            }
            if (card.get("answer") != null) {
                //We've already rendered the answer, we don't need to do it again.
                continue;
            }
            // Extract card item
            Card c;
            String maybeCardId = card.get("id");
            if (maybeCardId == null) {
                Timber.w("CardId was null, skipping");
                continue;
            }
            Long cardId;
            try {
                cardId = Long.parseLong(maybeCardId);
            } catch (Exception e) {
                Timber.e("Unable to parse CardId: %s. Unable to remove card", maybeCardId);
                continue;
            }
            try {
                c = col.getCard(cardId);
            } catch (WrongId e) {
                //#5891 - card can be inconsistent between the deck browser screen and the collection.
                //Realistically, we can skip any exception as it's a rendering task which should not kill the
                //process
                Timber.e(e, "Could not process card '%s' - skipping and removing from sight", maybeCardId);
                invalidCardIds.add(cardId);
                continue;
            }
            // Update item
            CardBrowser.updateSearchItemQA(task.getContext(), card, c, col);
            float progress = (float) i / mN * 100;
            task.doProgress(new TaskData((int) progress));
        }
        return new TaskData(new Object[] { mCards, invalidCardIds });
    }
}
