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
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.Binding.Companion.unicode
import com.ichi2.anki.reviewer.Binding.ModifierKeys
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.shift
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.fromPreference
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import java.util.*
import java.util.function.BiFunction
import java.util.stream.Collectors

/** Abstraction: Discuss moving many of these to 'Reviewer'  */
enum class ViewerCommand(val resourceId: Int, private val preferenceValue: Int) {
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
    COMMAND_DELETE(R.string.menu_delete_note, 14), // 15 is unused.
    COMMAND_PLAY_MEDIA(R.string.gesture_play, 16),
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

    companion object {
        fun fromString(value: String): ViewerCommand? {
            return fromInt(value.toInt())
        }

        fun fromInt(valueAsInt: Int): ViewerCommand? =
            // PERF: this is slow, but won't be used for long
            values().firstOrNull { it.preferenceValue == valueAsInt }

        val allDefaultBindings: List<MappableBinding>
            get() = Arrays.stream(values())
                .flatMap { x: ViewerCommand -> x.defaultValue.stream() }
                .collect(Collectors.toList())
    }

    fun toPreferenceString(): String {
        return preferenceValue.toString()
    }

    val preferenceKey: String
        get() = "binding_" + name.replaceFirst("COMMAND_".toRegex(), "")

    fun addBinding(preferences: SharedPreferences, binding: MappableBinding) {
        val addAtStart = BiFunction { collection: MutableList<MappableBinding>, element: MappableBinding ->
            // reorder the elements, moving the added binding to the first position
            collection.remove(element)
            collection.add(0, element)
            true
        }
        addBindingInternal(preferences, binding, addAtStart)
    }

    fun addBindingAtEnd(preferences: SharedPreferences, binding: MappableBinding) {
        val addAtEnd = BiFunction { collection: MutableList<MappableBinding>, element: MappableBinding ->
            // do not reorder the elements
            if (collection.contains(element)) {
                return@BiFunction false
            }
            collection.add(element)
            return@BiFunction true
        }
        addBindingInternal(preferences, binding, addAtEnd)
    }

    private fun addBindingInternal(preferences: SharedPreferences, binding: MappableBinding, performAdd: BiFunction<MutableList<MappableBinding>, MappableBinding, Boolean>) {
        if (this == COMMAND_NOTHING) {
            return
        }
        val bindings: MutableList<MappableBinding> = fromPreference(preferences, this)
        performAdd.apply(bindings, binding)
        val newValue: String = bindings.toPreferenceString()
        preferences.edit().putString(preferenceKey, newValue).apply()
    }

    // If we use the serialised format, then this adds additional coupling to the properties.
    val defaultValue: List<MappableBinding>
        get() = // If we use the serialised format, then this adds additional coupling to the properties.
            when (this) {
                COMMAND_FLIP_OR_ANSWER_EASE1 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_Y, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_1, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.ANSWER)
                )
                COMMAND_FLIP_OR_ANSWER_EASE2 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_X, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_2, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.ANSWER)
                )
                COMMAND_FLIP_OR_ANSWER_EASE3 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_B, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_3, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.ANSWER)
                )
                COMMAND_FLIP_OR_ANSWER_EASE4 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_A, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_4, CardSide.ANSWER), keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.ANSWER)
                )
                COMMAND_FLIP_OR_ANSWER_RECOMMENDED -> from(
                    keyCode(KeyEvent.KEYCODE_DPAD_CENTER, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_SPACE, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_ENTER, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, CardSide.ANSWER)
                )
                COMMAND_EDIT -> from(keyCode(KeyEvent.KEYCODE_E, CardSide.BOTH))
                COMMAND_MARK -> from(unicode('*', CardSide.BOTH))
                COMMAND_BURY_CARD -> from(unicode('-', CardSide.BOTH))
                COMMAND_BURY_NOTE -> from(unicode('=', CardSide.BOTH))
                COMMAND_SUSPEND_CARD -> from(unicode('@', CardSide.BOTH))
                COMMAND_SUSPEND_NOTE -> from(unicode('!', CardSide.BOTH))
                COMMAND_PLAY_MEDIA -> from(keyCode(KeyEvent.KEYCODE_R, CardSide.BOTH), keyCode(KeyEvent.KEYCODE_F5, CardSide.BOTH))
                COMMAND_REPLAY_VOICE -> from(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH))
                COMMAND_RECORD_VOICE -> from(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH, shift()))
                COMMAND_UNDO -> from(keyCode(KeyEvent.KEYCODE_Z, CardSide.BOTH))
                COMMAND_TOGGLE_FLAG_RED -> from(keyCode(KeyEvent.KEYCODE_1, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.BOTH, ctrl()))
                COMMAND_TOGGLE_FLAG_ORANGE -> from(keyCode(KeyEvent.KEYCODE_2, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.BOTH, ctrl()))
                COMMAND_TOGGLE_FLAG_GREEN -> from(keyCode(KeyEvent.KEYCODE_3, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.BOTH, ctrl()))
                COMMAND_TOGGLE_FLAG_BLUE -> from(keyCode(KeyEvent.KEYCODE_4, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.BOTH, ctrl()))
                COMMAND_TOGGLE_FLAG_PINK -> from(keyCode(KeyEvent.KEYCODE_5, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_5, CardSide.BOTH, ctrl()))
                COMMAND_TOGGLE_FLAG_TURQUOISE -> from(keyCode(KeyEvent.KEYCODE_6, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_6, CardSide.BOTH, ctrl()))
                COMMAND_TOGGLE_FLAG_PURPLE -> from(keyCode(KeyEvent.KEYCODE_7, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_7, CardSide.BOTH, ctrl()))
                else -> ArrayList()
            }

    private fun keyCode(keycode: Int, side: CardSide, keys: ModifierKeys): MappableBinding {
        return MappableBinding(keyCode(keys, keycode), MappableBinding.Screen.Reviewer(side))
    }

    private fun unicode(c: Char, side: CardSide): MappableBinding {
        return MappableBinding(unicode(c), MappableBinding.Screen.Reviewer(side))
    }

    private fun from(vararg bindings: MappableBinding): List<MappableBinding> {
        return ArrayList(listOf(*bindings))
    }

    private fun keyCode(keyCode: Int, side: CardSide): MappableBinding {
        return MappableBinding(keyCode(keyCode), MappableBinding.Screen.Reviewer(side))
    }

    interface CommandProcessor {
        /**
         *
         * example failure: answering an ease on the front of the card
         */
        fun executeCommand(which: ViewerCommand): Boolean
    }
}
