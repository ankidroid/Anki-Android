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
import androidx.core.view.ViewCompat;

/** Abstraction: Discuss moving many of these to 'Reviewer' */
public enum ViewerCommand {
    
    NOTHING,
    SHOW_ANSWER,
    FLIP_OR_ANSWER_EASE1,
    FLIP_OR_ANSWER_EASE2,
    FLIP_OR_ANSWER_EASE3,
    FLIP_OR_ANSWER_EASE4,
    FLIP_OR_ANSWER_RECOMMENDED,
    FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED,
    UNDO,
    EDIT,
    MARK,
    LOOKUP,
    BURY_CARD,
    SUSPEND_CARD,
    DELETE,
    UNUSED_15,
    PLAY_MEDIA,
    EXIT,
    BURY_NOTE,
    SUSPEND_NOTE,
    TOGGLE_FLAG_RED,
    TOGGLE_FLAG_ORANGE,
    TOGGLE_FLAG_GREEN,
    TOGGLE_FLAG_BLUE,
    UNSET_FLAG,
    ANSWER_FIRST_BUTTON,
    ANSWER_SECOND_BUTTON,
    ANSWER_THIRD_BUTTON,
    ANSWER_FOURTH_BUTTON,
    ANSWER_RECOMMENDED,
    PAGE_UP,
    PAGE_DOWN,

    TAG,
    CARD_INFO,
    ABORT_AND_SYNC,
    RECORD_VOICE,
    REPLAY_VOICE,

    TOGGLE_WHITEBOARD;

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
