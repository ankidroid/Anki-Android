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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

/** Abstraction: Discuss moving many of these to 'Reviewer' */
public class ViewerCommand {
    public static final int COMMAND_NOTHING = 0;
    public static final int COMMAND_SHOW_ANSWER = 1;
    public static final int COMMAND_FLIP_OR_ANSWER_EASE1 = 2;
    public static final int COMMAND_FLIP_OR_ANSWER_EASE2 = 3;
    public static final int COMMAND_FLIP_OR_ANSWER_EASE3 = 4;
    public static final int COMMAND_FLIP_OR_ANSWER_EASE4 = 5;
    public static final int COMMAND_FLIP_OR_ANSWER_RECOMMENDED = 6;
    public static final int COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED = 7;
    public static final int COMMAND_UNDO = 8;
    public static final int COMMAND_EDIT = 9;
    public static final int COMMAND_MARK = 10;
    public static final int COMMAND_LOOKUP = 11;
    public static final int COMMAND_BURY_CARD = 12;
    public static final int COMMAND_SUSPEND_CARD = 13;
    public static final int COMMAND_DELETE = 14;
    public static final int COMMAND_PLAY_MEDIA = 16;
    public static final int COMMAND_EXIT = 17;
    public static final int COMMAND_BURY_NOTE = 18;
    public static final int COMMAND_SUSPEND_NOTE = 19;
    public static final int COMMAND_TOGGLE_FLAG_RED = 20;
    public static final int COMMAND_TOGGLE_FLAG_ORANGE = 21;
    public static final int COMMAND_TOGGLE_FLAG_GREEN = 22;
    public static final int COMMAND_TOGGLE_FLAG_BLUE = 23;
    public static final int COMMAND_UNSET_FLAG = 24;
    public static final int COMMAND_ANSWER_FIRST_BUTTON = 25;
    public static final int COMMAND_ANSWER_SECOND_BUTTON = 26;
    public static final int COMMAND_ANSWER_THIRD_BUTTON = 27;
    public static final int COMMAND_ANSWER_FOURTH_BUTTON = 28;
    /** Answer "Good" */
    public static final int COMMAND_ANSWER_RECOMMENDED = 29;
    public static final int COMMAND_PAGE_UP = 30;
    public static final int COMMAND_PAGE_DOWN = 31;

    public static final int COMMAND_TAG = 32;
    public static final int COMMAND_CARD_INFO = 33;
    public static final int COMMAND_ABORT_AND_SYNC = 34;
    public static final int COMMAND_RECORD_VOICE = 35;
    public static final int COMMAND_REPLAY_VOICE = 36;

    public static final int COMMAND_TOGGLE_WHITEBOARD = 37;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({COMMAND_NOTHING, COMMAND_SHOW_ANSWER, COMMAND_FLIP_OR_ANSWER_EASE1, COMMAND_FLIP_OR_ANSWER_EASE2,
            COMMAND_FLIP_OR_ANSWER_EASE3, COMMAND_FLIP_OR_ANSWER_EASE4, COMMAND_FLIP_OR_ANSWER_RECOMMENDED,
            COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED, COMMAND_UNDO, COMMAND_EDIT, COMMAND_MARK, COMMAND_LOOKUP,
            COMMAND_BURY_CARD, COMMAND_SUSPEND_CARD, COMMAND_DELETE, COMMAND_PLAY_MEDIA, COMMAND_EXIT,
            COMMAND_BURY_NOTE, COMMAND_SUSPEND_NOTE, COMMAND_TOGGLE_FLAG_RED, COMMAND_TOGGLE_FLAG_ORANGE,
            COMMAND_TOGGLE_FLAG_GREEN, COMMAND_TOGGLE_FLAG_BLUE, COMMAND_UNSET_FLAG, COMMAND_ANSWER_FIRST_BUTTON,
            COMMAND_ANSWER_SECOND_BUTTON, COMMAND_ANSWER_THIRD_BUTTON, COMMAND_ANSWER_FOURTH_BUTTON, COMMAND_ANSWER_RECOMMENDED,
            COMMAND_PAGE_UP, COMMAND_PAGE_DOWN, COMMAND_TAG, COMMAND_CARD_INFO, COMMAND_ABORT_AND_SYNC, COMMAND_RECORD_VOICE,
            COMMAND_REPLAY_VOICE, COMMAND_TOGGLE_WHITEBOARD
    })
    public @interface ViewerCommandDef {}

    public interface CommandProcessor {
        /**
         *
         * @param which The command (defined in {@code ViewerCommand}) to execute
         * @return Whether the action was successfully processed.
         * <p>example failure: answering an ease on the front of the card</p>
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean executeCommand(@ViewerCommandDef int which);
    }
}
