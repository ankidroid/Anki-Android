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

import com.ichi2.anki.cardviewer.ViewerCommand.ViewerCommandDef;

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

    private PeripheralCommand(int keyCode, @ViewerCommandDef int command, @NonNull CardSide side) {
        this.mKeyCode = keyCode;
        this.mUnicodeCharacter = null;
        this.mCommand = command;
        this.mCardSide = side;
    }

    private PeripheralCommand(@Nullable Character unicodeCharacter, @ViewerCommandDef int command, @NonNull CardSide side) {
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
        return new PeripheralCommand((Character) unicodeChar, command, side);
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

        ret.add(PeripheralCommand.unicode('e', COMMAND_EDIT, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('*', COMMAND_MARK, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('-', COMMAND_BURY_CARD, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('=', COMMAND_BURY_NOTE, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('@', COMMAND_SUSPEND_CARD, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('!', COMMAND_SUSPEND_NOTE, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('r', COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(PeripheralCommand.keyCode(KeyEvent.KEYCODE_F5, COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(PeripheralCommand.unicode('z', COMMAND_UNDO, CardSide.BOTH));

        return ret;
    }

    private enum CardSide {
        NONE,
        QUESTION,
        ANSWER,
        BOTH
    }
}
