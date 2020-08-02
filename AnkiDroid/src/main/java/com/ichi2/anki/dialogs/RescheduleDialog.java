package com.ichi2.anki.dialogs;

import android.content.res.Resources;

import com.ichi2.anki.R;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.sched.SchedV2;
import com.ichi2.utils.FunctionalInterfaces.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import androidx.annotation.CheckResult;

public class RescheduleDialog extends IntegerDialog {
    private RescheduleDialog() {
        super();
    }

    @NotNull
    @CheckResult
    public static RescheduleDialog rescheduleSingleCard(Resources resources, Card currentCard,
                                                        Consumer<Integer> consumer) {
        RescheduleDialog rescheduleDialog = new RescheduleDialog();

        String content = getContentString(resources, currentCard);

        rescheduleDialog.setArgs(
                resources.getQuantityString(R.plurals.reschedule_cards_dialog_title, 1),
                resources.getString(R.string.reschedule_card_dialog_message),
                4,
                content);

        if (consumer != null) {
            rescheduleDialog.setCallbackRunnable(consumer);
        }

        return rescheduleDialog;
    }

    @NotNull
    @CheckResult
    public static RescheduleDialog rescheduleMultipleCards(Resources resources, Consumer<Integer> consumer, int cardCount) {
        RescheduleDialog rescheduleDialog = new RescheduleDialog();

        rescheduleDialog.setArgs(
                resources.getQuantityString(R.plurals.reschedule_cards_dialog_title, cardCount),
                resources.getString(R.string.reschedule_card_dialog_message),
                4);

        if (consumer != null) {
            rescheduleDialog.setCallbackRunnable(consumer);
        }

        return rescheduleDialog;
    }


    @Nullable
    private static String getContentString(Resources resources, Card currentCard) {
        if (currentCard.isNew()) {
            return resources.getString(R.string.reschedule_card_dialog_new_card_warning);
        }

        // #5595 - Help a user reschedule cards by showing them the current interval.
        // DEFECT: We should be able to calculate this for all card types - not yet performed for non-review or dynamic cards
        if (!currentCard.isReview()) {
            return null;
        }

        String message = resources.getString(R.string.reschedule_card_dialog_warning_ease_reset, SchedV2.RESCHEDULE_FACTOR / 10);
        if (currentCard.isDynamic()) {
            return message;
        }

        return message + "\n\n" + resources.getQuantityString(R.plurals.reschedule_card_dialog_interval, currentCard.getIvl(), currentCard.getIvl());
    }

}
