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

package com.ichi2.anki.reviewer;

import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.ichi2.anki.cardviewer.ViewerCommand.*;

public class PeripheralCommand {
    @Nullable
    private final Integer mKeyCode;

    @Nullable
    private final Character mUnicodeCharacter;

    @NonNull
    private final CardSide mCardSide;

    private final @ViewerCommandDef int mCommand;

    private final ModifierKeys mModifierKeys;


    private PeripheralCommand(int keyCode, @ViewerCommandDef int command, @NonNull CardSide side, ModifierKeys modifierKeys) {
        this.mKeyCode = keyCode;
        this.mUnicodeCharacter = null;
        this.mCommand = command;
        this.mCardSide = side;
        this.mModifierKeys = modifierKeys;
    }

    private PeripheralCommand(@Nullable Character unicodeCharacter, @ViewerCommandDef int command, @NonNull CardSide side, ModifierKeys modifierKeys) {
        this.mModifierKeys = modifierKeys;
        this.mKeyCode = null;
        this.mUnicodeCharacter = unicodeCharacter;
        this.mCommand = command;
        this.mCardSide = side;
    }

    public int getCommand() {
        return mCommand;
    }

    public Character getUnicodeCharacter() {
        return mUnicodeCharacter;
    }

    public Integer getKeycode() {
        return mKeyCode;
    }

    public boolean isQuestion() {
        return mCardSide == CardSide.QUESTION || mCardSide == CardSide.BOTH;
    }

    public boolean isAnswer() {
        return mCardSide == CardSide.ANSWER || mCardSide == CardSide.BOTH;
    }

    public static PeripheralCommand unicode(char unicodeChar, @ViewerCommandDef int command, CardSide side) {
        return unicode(unicodeChar, command, side, ModifierKeys.allowShift());
    }

    private static PeripheralCommand unicode(char unicodeChar, @ViewerCommandDef int command, CardSide side, ModifierKeys modifierKeys) {
        // Note: cast is needed to select the correct constructor
        return new PeripheralCommand((Character) unicodeChar, command, side, modifierKeys);
    }

    public static PeripheralCommand keyCode(int keyCode, @ViewerCommandDef int command, CardSide side) {
        return keyCode(keyCode, command, side, ModifierKeys.none());
    }

    private static PeripheralCommand keyCode(int keyCode, @ViewerCommandDef int command, CardSide side, ModifierKeys modifiers) {
        return new PeripheralCommand(keyCode, command, side, modifiers);
    }

    public static List<PeripheralCommand> getDefaultCommands() {
        List<PeripheralCommand> ret = new ArrayList<>(28); // Number of elements below

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_1, COMMAND_ANSWER_FIRST_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_2, COMMAND_ANSWER_SECOND_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_3, COMMAND_ANSWER_THIRD_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_4, COMMAND_ANSWER_FOURTH_BUTTON, CardSide.ANSWER));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_1, COMMAND_ANSWER_FIRST_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_2, COMMAND_ANSWER_SECOND_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_3, COMMAND_ANSWER_THIRD_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_4, COMMAND_ANSWER_FOURTH_BUTTON, CardSide.ANSWER));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_Y, COMMAND_FLIP_OR_ANSWER_EASE1, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_X, COMMAND_FLIP_OR_ANSWER_EASE2, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_B, COMMAND_FLIP_OR_ANSWER_EASE3, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_A, COMMAND_FLIP_OR_ANSWER_EASE4, CardSide.BOTH));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_SPACE, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_ENTER, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        // See: 1643 - Unsure if this will work - nothing came through on the emulator.
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_DPAD_CENTER, COMMAND_FLIP_OR_ANSWER_RECOMMENDED, CardSide.BOTH));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_E, COMMAND_EDIT, CardSide.BOTH));

        // Using a char rather than "Ctrl + 1" is not ideal due to the potential need to handle Shift/Fn + character on
        // international layouts but is what Anki Desktop does
        ret.add(PeripheralCommand.unicode('*', COMMAND_MARK, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('-', COMMAND_BURY_CARD, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('=', COMMAND_BURY_NOTE, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('@', COMMAND_SUSPEND_CARD, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('!', COMMAND_SUSPEND_NOTE, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_R, COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_F5, COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_V, COMMAND_REPLAY_VOICE, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_V, COMMAND_RECORD_VOICE, CardSide.BOTH, ModifierKeys.shift()));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_Z, COMMAND_UNDO, CardSide.BOTH));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_1, COMMAND_TOGGLE_FLAG_RED, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_2, COMMAND_TOGGLE_FLAG_ORANGE, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_3, COMMAND_TOGGLE_FLAG_GREEN, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_4, COMMAND_TOGGLE_FLAG_BLUE, CardSide.BOTH, ModifierKeys.ctrl()));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_1, COMMAND_TOGGLE_FLAG_RED, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_2, COMMAND_TOGGLE_FLAG_ORANGE, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_3, COMMAND_TOGGLE_FLAG_GREEN, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_4, COMMAND_TOGGLE_FLAG_BLUE, CardSide.BOTH, ModifierKeys.ctrl()));

        return ret;
    }


    public boolean matchesModifier(KeyEvent event) {
        return mModifierKeys.matches(event);
    }


    private enum CardSide {
        NONE,
        QUESTION,
        ANSWER,
        BOTH
    }


    public static class ModifierKeys {
        // null == true/false works.
        @Nullable
        private final Boolean mShift;
        @Nullable
        private final Boolean mCtrl;
        @Nullable
        private final Boolean mAlt;


        private ModifierKeys(@Nullable Boolean shift, @Nullable Boolean ctrl, @Nullable Boolean alt) {
            this.mShift = shift;
            this.mCtrl = ctrl;
            this.mAlt = alt;
        }


        public static ModifierKeys none() {
            return new ModifierKeys(false, false, false);
        }

        public static ModifierKeys ctrl() {
            return new ModifierKeys(false, true, false);
        }

        public static ModifierKeys shift() {
            return new ModifierKeys(true, false, false);
        }

        /** Allows shift, but not Ctrl/Alt */
        public static ModifierKeys allowShift() {
            return new ModifierKeys(null, false, false);
        }


        public boolean matches(KeyEvent event) {
            // return false if Ctrl+1 is pressed and 1 is expected
            return (mShift == null || mShift == event.isShiftPressed()) &&
                    (mCtrl == null || mCtrl == event.isCtrlPressed()) &&
                    (mAlt == null || mAlt == event.isAltPressed());
        }
    }
}
