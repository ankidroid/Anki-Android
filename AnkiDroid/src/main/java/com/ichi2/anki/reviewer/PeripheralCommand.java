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

import com.ichi2.anki.cardviewer.ViewerCommand;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.ichi2.anki.cardviewer.ViewerCommand.*;
import static com.ichi2.anki.reviewer.Binding.*;

public class PeripheralCommand {

    @NonNull
    private final CardSide mCardSide;

    @NonNull
    private final ViewerCommand mCommand;

    @NonNull
    private final Binding mBinding;


    public PeripheralCommand(@NonNull Binding binding, @NonNull ViewerCommand command, @NonNull CardSide side) {
        this.mBinding = binding;
        this.mCommand = command;
        this.mCardSide = side;
    }

    @NonNull
    public ViewerCommand getCommand() {
        return mCommand;
    }

    @Nullable
    public Character getUnicodeCharacter() {
        return mBinding.getUnicodeCharacter();
    }

    @Nullable
    public Integer getKeycode() {
        return mBinding.getKeycode();
    }

    @NonNull
    public Binding getBinding() {
        return mBinding;
    }

    public boolean isQuestion() {
        return mCardSide == CardSide.QUESTION || mCardSide == CardSide.BOTH;
    }

    public boolean isAnswer() {
        return mCardSide == CardSide.ANSWER || mCardSide == CardSide.BOTH;
    }


    private static PeripheralCommand keyCode(int keycode, @NonNull ViewerCommand command, CardSide side, ModifierKeys modifiers) {
        return new PeripheralCommand(Binding.keyCode(modifiers, keycode), command, side);
    }


    private static PeripheralCommand unicode(char c, @NonNull ViewerCommand command, CardSide side) {
        return new PeripheralCommand(Binding.unicode(c), command, side);
    }


    private static PeripheralCommand keyCode(int keycode, @NonNull ViewerCommand command, CardSide side) {
        return new PeripheralCommand(Binding.keyCode(keycode), command, side);
    }

    public static List<PeripheralCommand> getDefaultCommands() {
        List<PeripheralCommand> ret = new ArrayList<>(28); // Number of elements below

        ret.add(keyCode(KeyEvent.KEYCODE_1, COMMAND_ANSWER_FIRST_BUTTON, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_2, COMMAND_ANSWER_SECOND_BUTTON, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_3, COMMAND_ANSWER_THIRD_BUTTON, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_4, COMMAND_ANSWER_FOURTH_BUTTON, CardSide.ANSWER));

        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_1, COMMAND_ANSWER_FIRST_BUTTON, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_2, COMMAND_ANSWER_SECOND_BUTTON, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_3, COMMAND_ANSWER_THIRD_BUTTON, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_4, COMMAND_ANSWER_FOURTH_BUTTON, CardSide.ANSWER));

        ret.add(keyCode(KeyEvent.KEYCODE_BUTTON_Y, COMMAND_FLIP_OR_ANSWER_EASE1, CardSide.BOTH));
        ret.add(keyCode(KeyEvent.KEYCODE_BUTTON_X, COMMAND_FLIP_OR_ANSWER_EASE2, CardSide.BOTH));
        ret.add(keyCode(KeyEvent.KEYCODE_BUTTON_B, COMMAND_FLIP_OR_ANSWER_EASE3, CardSide.BOTH));
        ret.add(keyCode(KeyEvent.KEYCODE_BUTTON_A, COMMAND_FLIP_OR_ANSWER_EASE4, CardSide.BOTH));

        ret.add(keyCode(KeyEvent.KEYCODE_SPACE, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_ENTER, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, COMMAND_ANSWER_RECOMMENDED, CardSide.ANSWER));
        // See: 1643 - Unsure if this will work - nothing came through on the emulator.
        ret.add(keyCode(KeyEvent.KEYCODE_DPAD_CENTER, COMMAND_FLIP_OR_ANSWER_RECOMMENDED, CardSide.BOTH));

        ret.add(keyCode(KeyEvent.KEYCODE_E, COMMAND_EDIT, CardSide.BOTH));

        // Using a char rather than "Ctrl + 1" is not ideal due to the potential need to handle Shift/Fn + character on
        // international layouts but is what Anki Desktop does
        ret.add(unicode('*', COMMAND_MARK, CardSide.BOTH));
        ret.add(unicode('-', COMMAND_BURY_CARD, CardSide.BOTH));
        ret.add(unicode('=', COMMAND_BURY_NOTE, CardSide.BOTH));
        ret.add(unicode('@', COMMAND_SUSPEND_CARD, CardSide.BOTH));
        ret.add(unicode('!', COMMAND_SUSPEND_NOTE, CardSide.BOTH));
        ret.add(keyCode(KeyEvent.KEYCODE_R, COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(keyCode(KeyEvent.KEYCODE_F5, COMMAND_PLAY_MEDIA, CardSide.BOTH));
        ret.add(keyCode(KeyEvent.KEYCODE_V, COMMAND_REPLAY_VOICE, CardSide.BOTH));
        ret.add(keyCode(KeyEvent.KEYCODE_V, COMMAND_RECORD_VOICE, CardSide.BOTH, ModifierKeys.shift()));
        ret.add(keyCode(KeyEvent.KEYCODE_Z, COMMAND_UNDO, CardSide.BOTH));

        ret.add(keyCode(KeyEvent.KEYCODE_1, COMMAND_TOGGLE_FLAG_RED, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(keyCode(KeyEvent.KEYCODE_2, COMMAND_TOGGLE_FLAG_ORANGE, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(keyCode(KeyEvent.KEYCODE_3, COMMAND_TOGGLE_FLAG_GREEN, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(keyCode(KeyEvent.KEYCODE_4, COMMAND_TOGGLE_FLAG_BLUE, CardSide.BOTH, ModifierKeys.ctrl()));

        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_1, COMMAND_TOGGLE_FLAG_RED, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_2, COMMAND_TOGGLE_FLAG_ORANGE, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_3, COMMAND_TOGGLE_FLAG_GREEN, CardSide.BOTH, ModifierKeys.ctrl()));
        ret.add(keyCode(KeyEvent.KEYCODE_NUMPAD_4, COMMAND_TOGGLE_FLAG_BLUE, CardSide.BOTH, ModifierKeys.ctrl()));

        return ret;
    }


    public boolean matchesModifier(KeyEvent event) {
        return mBinding.matchesModifier(event);
    }
}
