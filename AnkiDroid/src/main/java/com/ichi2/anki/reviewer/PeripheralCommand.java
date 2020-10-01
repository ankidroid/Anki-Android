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

import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_ANSWER_FIRST_BUTTON;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_ANSWER_FOURTH_BUTTON;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_ANSWER_RECOMMENDED;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_ANSWER_SECOND_BUTTON;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_ANSWER_THIRD_BUTTON;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_BURY_CARD;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_BURY_NOTE;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_EDIT;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE1;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE2;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE3;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE4;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_FLIP_OR_ANSWER_RECOMMENDED;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_MARK;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_PLAY_MEDIA;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_SUSPEND_CARD;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_SUSPEND_NOTE;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_TOGGLE_FLAG_GREEN;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_TOGGLE_FLAG_ORANGE;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_TOGGLE_FLAG_RED;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_UNDO;
import static com.ichi2.anki.cardviewer.ViewerCommand.ViewerCommandDef;

public class PeripheralCommand {
    @Nullable
    private final Integer mKeyCode;

    @Nullable
    private final Character mUnicodeCharacter;

    @NonNull
    private final CardSide mCardSide;

    private final @ViewerCommandDef int mCommand;

    private final ModifierKeys modifierKeys;


    private PeripheralCommand(int keyCode, @ViewerCommandDef int command, @NonNull CardSide side) {
        this.mKeyCode = keyCode;
        this.mUnicodeCharacter = null;
        this.mCommand = command;
        this.mCardSide = side;
        this.modifierKeys = ModifierKeys.none();
    }

    private PeripheralCommand(@Nullable Character unicodeCharacter, @ViewerCommandDef int command, @NonNull CardSide side, ModifierKeys modifierKeys) {
        this.modifierKeys = modifierKeys;
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
        return unicode(unicodeChar, command, side, ModifierKeys.none());
    }

    private static PeripheralCommand unicode(char unicodeChar, @ViewerCommandDef int command, CardSide side, ModifierKeys modifierKeys) {
        return new PeripheralCommand((Character) unicodeChar, command, side, modifierKeys);
    }

    public static PeripheralCommand keyCode(int keyCode, @ViewerCommandDef int command, CardSide side) {
        return new PeripheralCommand(keyCode, command, side);
    }

    public static List<PeripheralCommand> getDefaultCommands() {
        List<PeripheralCommand> ret = new ArrayList<>();

        ret.add(PeripheralCommand.unicode('1', COMMAND_ANSWER_FIRST_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.unicode('2', COMMAND_ANSWER_SECOND_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.unicode('3', COMMAND_ANSWER_THIRD_BUTTON, CardSide.ANSWER));
        ret.add(PeripheralCommand.unicode('4', COMMAND_ANSWER_FOURTH_BUTTON, CardSide.ANSWER));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_Y, COMMAND_FLIP_OR_ANSWER_EASE1, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_X, COMMAND_FLIP_OR_ANSWER_EASE2, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_B, COMMAND_FLIP_OR_ANSWER_EASE3, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_BUTTON_A, COMMAND_FLIP_OR_ANSWER_EASE4, CardSide.BOTH));

        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_SPACE, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_ENTER, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        // See: 1643 - Unsure if this will work - nothing came through on the emulator.
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_DPAD_CENTER, COMMAND_FLIP_OR_ANSWER_RECOMMENDED, CardSide.BOTH));

        ret.add(PeripheralCommand.unicode('e', COMMAND_EDIT, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('*', COMMAND_MARK, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('-', COMMAND_BURY_CARD, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('=', COMMAND_BURY_NOTE, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('@', COMMAND_SUSPEND_CARD, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('!', COMMAND_SUSPEND_NOTE, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('r', COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_F5, COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('z', COMMAND_UNDO, CardSide.BOTH));

        ret.add(PeripheralCommand.unicode('1', COMMAND_TOGGLE_FLAG_RED, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.unicode('2', COMMAND_TOGGLE_FLAG_ORANGE, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.unicode('3', COMMAND_TOGGLE_FLAG_GREEN, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(PeripheralCommand.unicode('4', COMMAND_TOGGLE_FLAG_BLUE, CardSide.BOTH, ModifierKeys.ctrl()));

        return ret;
    }


    public boolean matchesModifier(KeyEvent event) {
        return modifierKeys.matches(event);
    }


    private enum CardSide {
        NONE,
        QUESTION,
        ANSWER,
        BOTH
    }


    public static class ModifierKeys {
        private final boolean mShift;
        private final boolean mCtrl;
        private final boolean mAlt;


        public ModifierKeys(boolean shift, boolean ctrl, boolean alt) {
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


        public boolean matches(KeyEvent event) {
            // return false if Ctrl+1 is pressed and 1 is expected
            return mShift == event.isShiftPressed() && mCtrl == event.isCtrlPressed() && mAlt == event.isAltPressed();
        }
    }
}
