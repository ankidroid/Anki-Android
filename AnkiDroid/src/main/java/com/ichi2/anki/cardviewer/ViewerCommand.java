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

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Abstraction: Discuss moving many of these to 'Reviewer' */
public enum ViewerCommand {

    COMMAND_NOTHING(R.string.nothing, 0),
    COMMAND_SHOW_ANSWER(R.string.show_answer, 1),
    COMMAND_FLIP_OR_ANSWER_EASE1(R.string.gesture_answer_1, 2),
    COMMAND_FLIP_OR_ANSWER_EASE2(R.string.gesture_answer_2, 3),
    COMMAND_FLIP_OR_ANSWER_EASE3(R.string.gesture_answer_3, 4),
    COMMAND_FLIP_OR_ANSWER_EASE4(R.string.gesture_answer_4, 5),
    COMMAND_FLIP_OR_ANSWER_RECOMMENDED(R.string.gesture_answer_green, 6),
    COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED(R.string.gesture_answer_better_recommended, 7),
    COMMAND_UNDO(R.string.undo, 8),
    COMMAND_EDIT(R.string.cardeditor_title_edit_card, 9),
    COMMAND_MARK(R.string.menu_mark_note, 10),
    COMMAND_LOOKUP(R.string.lookup_button_content, 11),
    COMMAND_BURY_CARD(R.string.menu_bury, 12),
    COMMAND_SUSPEND_CARD(R.string.menu_suspend_card, 13),
    COMMAND_DELETE(R.string.menu_delete_note, 14),
    COMMAND_UNUSED_15(R.string.nothing, 15),
    COMMAND_PLAY_MEDIA(R.string.gesture_play,16),
    COMMAND_EXIT(R.string.nothing, 17),
    COMMAND_BURY_NOTE(R.string.menu_bury_note, 18),
    COMMAND_SUSPEND_NOTE(R.string.menu_suspend_note, 19),
    COMMAND_TOGGLE_FLAG_RED(R.string.gesture_flag_red, 20),
    COMMAND_TOGGLE_FLAG_ORANGE(R.string.gesture_flag_orange, 21),
    COMMAND_TOGGLE_FLAG_GREEN(R.string.gesture_flag_green, 22),
    COMMAND_TOGGLE_FLAG_BLUE(R.string.gesture_flag_blue, 23),
    COMMAND_UNSET_FLAG(R.string.gesture_flag_remove, 24),
    COMMAND_ANSWER_FIRST_BUTTON(R.string.gesture_answer_1, 25),
    COMMAND_ANSWER_SECOND_BUTTON(R.string.gesture_answer_2, 26),
    COMMAND_ANSWER_THIRD_BUTTON(R.string.gesture_answer_3, 27),
    COMMAND_ANSWER_FOURTH_BUTTON(R.string.gesture_answer_4, 28),
    COMMAND_ANSWER_RECOMMENDED(R.string.gesture_answer_green, 29),
    COMMAND_PAGE_UP(R.string.gesture_page_up, 30),
    COMMAND_PAGE_DOWN(R.string.gesture_page_down, 31),
    COMMAND_TAG(R.string.add_tag, 32),
    COMMAND_CARD_INFO(R.string.card_info_title, 33),
    COMMAND_ABORT_AND_SYNC(R.string.gesture_abort_sync, 34),
    COMMAND_RECORD_VOICE(R.string.record_voice, 35),
    COMMAND_REPLAY_VOICE(R.string.replay_voice, 36),
    COMMAND_TOGGLE_WHITEBOARD(R.string.gesture_toggle_whiteboard, 37);

    private final int mResourceId;
    private final int mPreferenceValue;


    ViewerCommand(int resourceId, int preferenceValue) {
        this.mResourceId = resourceId;
        this.mPreferenceValue = preferenceValue;
    }

    public int getResourceId() {
        return mResourceId;
    }

    @Nullable
    public static ViewerCommand fromString(String value) {
        return fromInt(Integer.parseInt(value));
    }

    @Nullable
    public static ViewerCommand fromInt(int valueAsInt) {
        // PERF: this is slow, but won't be used for long
        return Arrays.stream(ViewerCommand.values()).filter(x -> x.mPreferenceValue == valueAsInt).findFirst().orElse(null);
    }


    public String toPreferenceString() {
        return Integer.toString(mPreferenceValue);
    }


    public String getPreferenceKey() {
        return "binding_" + name().replaceFirst("COMMAND_", "");
    }


    public interface CommandProcessor {
        /**
          * <p>example failure: answering an ease on the front of the card</p>
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean executeCommand(@NonNull ViewerCommand which);
    }
}