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
import androidx.core.content.edit
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
enum class ViewerCommand(val resourceId: Int) {
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
    BURY_CARD(R.string.menu_bury_card),
    SUSPEND_CARD(R.string.menu_suspend_card),
    DELETE(R.string.menu_delete_note),
    PLAY_MEDIA(R.string.gesture_play),
    EXIT(R.string.gesture_abort_learning),
    BURY_NOTE(R.string.menu_bury_note),
    SUSPEND_NOTE(R.string.menu_suspend_note),
    TOGGLE_FLAG_RED(R.string.gesture_flag_red),
    TOGGLE_FLAG_ORANGE(R.string.gesture_flag_orange),
    TOGGLE_FLAG_GREEN(R.string.gesture_flag_green),
    TOGGLE_FLAG_BLUE(R.string.gesture_flag_blue),
    TOGGLE_FLAG_PINK(R.string.gesture_flag_pink),
    TOGGLE_FLAG_TURQUOISE(R.string.gesture_flag_turquoise),
    TOGGLE_FLAG_PURPLE(R.string.gesture_flag_purple),
    UNSET_FLAG(R.string.gesture_flag_remove),
    PAGE_UP(R.string.gesture_page_up),
    PAGE_DOWN(R.string.gesture_page_down),
    TAG(R.string.add_tag),
    CARD_INFO(R.string.card_info_title),
    ABORT_AND_SYNC(R.string.gesture_abort_sync),
    RECORD_VOICE(R.string.record_voice),
    REPLAY_VOICE(R.string.replay_voice),
    TOGGLE_WHITEBOARD(R.string.gesture_toggle_whiteboard),
    CLEAR_WHITEBOARD(R.string.clear_whiteboard),
    CHANGE_WHITEBOARD_PEN_COLOR(R.string.title_whiteboard_editor),
    SHOW_HINT(R.string.gesture_show_hint),
    SHOW_ALL_HINTS(R.string.gesture_show_all_hints),
    ADD_NOTE(R.string.menu_add_note),
    RESCHEDULE_NOTE(R.string.card_editor_reschedule_card);

    companion object {
        val allDefaultBindings: List<MappableBinding>
            get() = Arrays.stream(values())
                .flatMap { x: ViewerCommand -> x.defaultValue.stream() }
                .collect(Collectors.toList())
    }

    val preferenceKey: String
        get() = "binding_$name"

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
        val bindings: MutableList<MappableBinding> = fromPreference(preferences, this)
        performAdd.apply(bindings, binding)
        val newValue: String = bindings.toPreferenceString()
        preferences.edit { putString(preferenceKey, newValue) }
    }

    fun removeBinding(prefs: SharedPreferences, binding: MappableBinding) {
        val bindings: MutableList<MappableBinding> = MappableBinding.fromPreferenceString(preferenceKey)
        bindings.remove(binding)
        prefs.edit {
            putString(preferenceKey, bindings.toPreferenceString())
        }
    }

    // If we use the serialised format, then this adds additional coupling to the properties.
    val defaultValue: List<MappableBinding>
        get() = // If we use the serialised format, then this adds additional coupling to the properties.
            when (this) {
                FLIP_OR_ANSWER_EASE1 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_Y, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_1, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.ANSWER)
                )
                FLIP_OR_ANSWER_EASE2 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_X, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_2, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.ANSWER)
                )
                FLIP_OR_ANSWER_EASE3 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_B, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_3, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.ANSWER)
                )
                FLIP_OR_ANSWER_EASE4 -> from(
                    keyCode(KeyEvent.KEYCODE_BUTTON_A, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_4, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.ANSWER)
                )
                FLIP_OR_ANSWER_RECOMMENDED -> from(
                    keyCode(KeyEvent.KEYCODE_DPAD_CENTER, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_SPACE, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_ENTER, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, CardSide.ANSWER)
                )
                EDIT -> from(keyCode(KeyEvent.KEYCODE_E, CardSide.BOTH))
                MARK -> from(unicode('*', CardSide.BOTH))
                BURY_CARD -> from(unicode('-', CardSide.BOTH))
                BURY_NOTE -> from(unicode('=', CardSide.BOTH))
                SUSPEND_CARD -> from(unicode('@', CardSide.BOTH))
                SUSPEND_NOTE -> from(unicode('!', CardSide.BOTH))
                PLAY_MEDIA -> from(keyCode(KeyEvent.KEYCODE_R, CardSide.BOTH), keyCode(KeyEvent.KEYCODE_F5, CardSide.BOTH))
                REPLAY_VOICE -> from(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH))
                RECORD_VOICE -> from(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH, shift()))
                UNDO -> from(keyCode(KeyEvent.KEYCODE_Z, CardSide.BOTH))
                TOGGLE_FLAG_RED -> from(keyCode(KeyEvent.KEYCODE_1, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_ORANGE -> from(keyCode(KeyEvent.KEYCODE_2, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_GREEN -> from(keyCode(KeyEvent.KEYCODE_3, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_BLUE -> from(keyCode(KeyEvent.KEYCODE_4, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_PINK -> from(keyCode(KeyEvent.KEYCODE_5, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_5, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_TURQUOISE -> from(keyCode(KeyEvent.KEYCODE_6, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_6, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_PURPLE -> from(keyCode(KeyEvent.KEYCODE_7, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_7, CardSide.BOTH, ctrl()))
                SHOW_HINT -> from(keyCode(KeyEvent.KEYCODE_H, CardSide.BOTH))
                SHOW_ALL_HINTS -> from(keyCode(KeyEvent.KEYCODE_G, CardSide.BOTH))
                ADD_NOTE -> from(keyCode(KeyEvent.KEYCODE_A, CardSide.BOTH))
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

    fun interface CommandProcessor {
        /**
         *
         * example failure: answering an ease on the front of the card
         */
        fun executeCommand(which: ViewerCommand, fromGesture: Gesture?): Boolean
    }
}
