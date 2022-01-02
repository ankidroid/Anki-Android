/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs;

import android.content.res.Resources;

import com.ichi2.anki.R;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.sched.SchedV2;

import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.annotation.CheckResult;

public class RescheduleDialog extends IntegerDialog {
    private RescheduleDialog() {
        super();
    }

    @NonNull
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

    @NonNull
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
        if (currentCard.isInDynamicDeck()) {
            return message;
        }

        return message + "\n\n" + resources.getQuantityString(R.plurals.reschedule_card_dialog_interval, currentCard.getIvl(), currentCard.getIvl());
    }

}
