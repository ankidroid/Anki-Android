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

import android.content.SharedPreferences;
import android.view.KeyEvent;

import com.ichi2.anki.R;
import com.ichi2.anki.reviewer.Binding;
import com.ichi2.anki.reviewer.Binding.ModifierKeys;
import com.ichi2.anki.reviewer.CardSide;
import com.ichi2.anki.reviewer.MappableBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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
    // 15 is unused.
    COMMAND_PLAY_MEDIA(R.string.gesture_play,16),
    COMMAND_EXIT(R.string.gesture_abort_learning, 17),
    COMMAND_BURY_NOTE(R.string.menu_bury_note, 18),
    COMMAND_SUSPEND_NOTE(R.string.menu_suspend_note, 19),
    COMMAND_TOGGLE_FLAG_RED(R.string.gesture_flag_red, 20),
    COMMAND_TOGGLE_FLAG_ORANGE(R.string.gesture_flag_orange, 21),
    COMMAND_TOGGLE_FLAG_GREEN(R.string.gesture_flag_green, 22),
    COMMAND_TOGGLE_FLAG_BLUE(R.string.gesture_flag_blue, 23),
    COMMAND_TOGGLE_FLAG_PINK(R.string.gesture_flag_pink, 38),
    COMMAND_TOGGLE_FLAG_TURQUOISE(R.string.gesture_flag_turquoise, 39),
    COMMAND_TOGGLE_FLAG_PURPLE(R.string.gesture_flag_purple, 40),
    COMMAND_UNSET_FLAG(R.string.gesture_flag_remove, 24),
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

    public static List<MappableBinding> getAllDefaultBindings() {
        return Arrays.stream(ViewerCommand.values())
                .flatMap(x -> x.getDefaultValue().stream())
                .collect(Collectors.toList());
    }

    public void addBinding(SharedPreferences preferences, MappableBinding binding) {
        BiFunction<List<MappableBinding>, MappableBinding, Boolean> addAtStart = (collection, element) -> {
            // reorder the elements, moving the added binding to the first position
            collection.remove(element);
            collection.add(0, element);
            return true;
        };
        addBindingInternal(preferences, binding, addAtStart);
    }

    public void addBindingAtEnd(SharedPreferences preferences, MappableBinding binding) {
        BiFunction<List<MappableBinding>, MappableBinding, Boolean> addAtEnd = (collection, element) -> {
            // do not reorder the elements
            if (collection.contains(element)) {
                return false;
            }
            collection.add(element);
            return true;
        };
        addBindingInternal(preferences, binding, addAtEnd);
    }

    private void addBindingInternal(SharedPreferences preferences, MappableBinding binding, BiFunction<List<MappableBinding>, MappableBinding, Boolean> performAdd) {
        if (this == COMMAND_NOTHING) {
            return;
        }
        List<MappableBinding> bindings = MappableBinding.fromPreference(preferences, this);
        performAdd.apply(bindings, binding);
        String newValue = MappableBinding.Companion.toPreferenceString(bindings);
        preferences.edit().putString(this.getPreferenceKey(), newValue).apply();
    }

    @NonNull
    public List<MappableBinding> getDefaultValue() {
        // If we use the serialised format, then this adds additional coupling to the properties.
        switch (this) {
            case COMMAND_FLIP_OR_ANSWER_EASE1:
                return from(keyCode(KeyEvent.KEYCODE_BUTTON_Y, CardSide.BOTH),
                        keyCode(KeyEvent.KEYCODE_1, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.ANSWER));
            case COMMAND_FLIP_OR_ANSWER_EASE2:
                return from(keyCode(KeyEvent.KEYCODE_BUTTON_X, CardSide.BOTH),
                        keyCode(KeyEvent.KEYCODE_2, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.ANSWER));
            case COMMAND_FLIP_OR_ANSWER_EASE3:
                return from(keyCode(KeyEvent.KEYCODE_BUTTON_B, CardSide.BOTH),
                        keyCode(KeyEvent.KEYCODE_3, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.ANSWER));
            case COMMAND_FLIP_OR_ANSWER_EASE4:
                return from(keyCode(KeyEvent.KEYCODE_BUTTON_A, CardSide.BOTH),
                        keyCode(KeyEvent.KEYCODE_4, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.ANSWER));
            case COMMAND_FLIP_OR_ANSWER_RECOMMENDED:
                return from(keyCode(KeyEvent.KEYCODE_DPAD_CENTER, CardSide.BOTH),
                        keyCode(KeyEvent.KEYCODE_SPACE, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_ENTER, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, CardSide.ANSWER));
            case COMMAND_EDIT:
                return from(keyCode(KeyEvent.KEYCODE_E, CardSide.BOTH));
            case COMMAND_MARK:
                return from(unicode('*', CardSide.BOTH));
            case COMMAND_BURY_CARD:
                return from(unicode('-', CardSide.BOTH));
            case COMMAND_BURY_NOTE:
                return from(unicode('=', CardSide.BOTH));
            case COMMAND_SUSPEND_CARD:
                return from(unicode('@', CardSide.BOTH));
            case COMMAND_SUSPEND_NOTE:
                return from(unicode('!', CardSide.BOTH));
            case COMMAND_PLAY_MEDIA:
                return from(keyCode(KeyEvent.KEYCODE_R, CardSide.BOTH), keyCode(KeyEvent.KEYCODE_F5, CardSide.BOTH));
            case COMMAND_REPLAY_VOICE:
                return from(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH));
            case COMMAND_RECORD_VOICE:
                return from(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH, ModifierKeys.shift()));
            case COMMAND_UNDO:
                return from(keyCode(KeyEvent.KEYCODE_Z, CardSide.BOTH));
            case COMMAND_TOGGLE_FLAG_RED:
                return from(keyCode(KeyEvent.KEYCODE_1, CardSide.BOTH, ModifierKeys.ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.BOTH, ModifierKeys.ctrl()));
            case COMMAND_TOGGLE_FLAG_ORANGE:
                return from(keyCode(KeyEvent.KEYCODE_2, CardSide.BOTH, ModifierKeys.ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.BOTH, ModifierKeys.ctrl()));
            case COMMAND_TOGGLE_FLAG_GREEN:
                return from(keyCode(KeyEvent.KEYCODE_3, CardSide.BOTH, ModifierKeys.ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.BOTH, ModifierKeys.ctrl()));
            case COMMAND_TOGGLE_FLAG_BLUE:
                return from(keyCode(KeyEvent.KEYCODE_4, CardSide.BOTH, ModifierKeys.ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.BOTH, ModifierKeys.ctrl()));
            case COMMAND_TOGGLE_FLAG_PINK:
                return from(keyCode(KeyEvent.KEYCODE_5, CardSide.BOTH, ModifierKeys.ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_5, CardSide.BOTH, ModifierKeys.ctrl()));
            case COMMAND_TOGGLE_FLAG_TURQUOISE:
                return from(keyCode(KeyEvent.KEYCODE_6, CardSide.BOTH, ModifierKeys.ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_6, CardSide.BOTH, ModifierKeys.ctrl()));
            case COMMAND_TOGGLE_FLAG_PURPLE:
                return from(keyCode(KeyEvent.KEYCODE_7, CardSide.BOTH, ModifierKeys.ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_7, CardSide.BOTH, ModifierKeys.ctrl()));
            default: return new ArrayList<>();
        }
    }


    private MappableBinding keyCode(int keycode, @SuppressWarnings("SameParameterValue") CardSide side, ModifierKeys keys) {
        return new MappableBinding(Binding.keyCode(keys, keycode), new MappableBinding.Screen.Reviewer(side));
    }


    private MappableBinding unicode(char c, @SuppressWarnings("SameParameterValue") CardSide side) {
        return new MappableBinding(Binding.unicode(c), new MappableBinding.Screen.Reviewer(side));
    }


    private List<MappableBinding> from(MappableBinding... bindings) {
        return new ArrayList<>(Arrays.asList(bindings));
    }


    private MappableBinding keyCode(int keyCode, CardSide side) {
        return new MappableBinding(Binding.keyCode(keyCode), new MappableBinding.Screen.Reviewer(side));
    }


    public interface CommandProcessor {
        /**
          * <p>example failure: answering an ease on the front of the card</p>
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean executeCommand(@NonNull ViewerCommand which);
    }
}