/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.cardviewer;

import com.ichi2.anki.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import androidx.core.view.ViewCompat;

/** Abstraction: Discuss moving many of these to 'Reviewer' */
public enum ViewerCommand {
    
    NOTHING(R.string.nothing),
    SHOW_ANSWER(R.string.show_answer),
    FLIP_OR_ANSWER_EASE1(R.string.gesture_answer_1),
    FLIP_OR_ANSWER_EASE2(R.string.gesture_answer_2),
    FLIP_OR_ANSWER_EASE3(R.string.gesture_answer_3),
    FLIP_OR_ANSWER_EASE4(R.string.gesture_answer_4),
    FLIP_OR_ANSWER_RECOMMENDED(R.string.gesture_answer_green),
    FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED(R.string.gesture_answer_better_recommended),
    UNDO(R.string.undo),
    EDIT(R.string.cardeditor_title_edit_card),
    MARK(R.string.menu_mark_note),
    LOOKUP(R.string.lookup_button_content),
    BURY_CARD(R.string.menu_bury),
    SUSPEND_CARD(R.string.menu_suspend_card),
    DELETE(R.string.menu_delete_note),
    UNUSED_15(R.string.nothing),
    PLAY_MEDIA(R.string.gesture_play),
    EXIT(R.string.nothing),
    BURY_NOTE(R.string.menu_bury_note),
    SUSPEND_NOTE(R.string.menu_suspend_note),
    TOGGLE_FLAG_RED(R.string.gesture_flag_red),
    TOGGLE_FLAG_ORANGE(R.string.gesture_flag_orange),
    TOGGLE_FLAG_GREEN(R.string.gesture_flag_green),
    TOGGLE_FLAG_BLUE(R.string.gesture_flag_blue),
    UNSET_FLAG(R.string.gesture_flag_remove),
    ANSWER_FIRST_BUTTON(R.string.gesture_answer_1),
    ANSWER_SECOND_BUTTON(R.string.gesture_answer_2),
    ANSWER_THIRD_BUTTON(R.string.gesture_answer_3),
    ANSWER_FOURTH_BUTTON(R.string.gesture_answer_4),
    ANSWER_RECOMMENDED(R.string.gesture_answer_green),
    PAGE_UP(R.string.gesture_page_up),
    PAGE_DOWN(R.string.gesture_page_down),

    TAG(R.string.add_tag),
    CARD_INFO(R.string.card_info_title),
    ABORT_AND_SYNC(R.string.gesture_abort_sync),
    RECORD_VOICE(R.string.record_voice),
    REPLAY_VOICE(R.string.replay_voice),

    TOGGLE_WHITEBOARD(R.string.gesture_toggle_whiteboard);

    private final int resourceId;

    ViewerCommand(int resourceId) {
        this.resourceId = resourceId;
    }

    public int getResourceId() {
        return resourceId;
    }

    /**
     * Get the key of this command, under which all {@link com.ichi2.anki.reviewer.Binding}s are stored.
     *
     * @return preference key
     */
    public String getPreferenceKey() {
        return "binding_" + name();
    }

    public interface CommandProcessor {
        /**
         *
         * @param which The command (defined in {@code ViewerCommand}) to execute
         * @return Whether the action was successfully processed.
         * <p>example failure: answering an ease on the front of the card</p>
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean executeCommand(ViewerCommand which);
    }
}
