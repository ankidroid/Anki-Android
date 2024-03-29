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
import com.ichi2.anki.reviewer.MappableBinding.*
import com.ichi2.anki.reviewer.MappableBinding.Companion.fromPreference
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import java.util.*
import java.util.function.BiFunction
import java.util.stream.Collectors

/** Abstraction: Discuss moving many of these to 'Reviewer'  */
enum class ViewerCommand(val resourceId: Int) {
    SHOW_ANSWER(R.string.show_answer),
    FLIP_OR_ANSWER_EASE1(R.string.answer_again),
    FLIP_OR_ANSWER_EASE2(R.string.answer_hard),
    FLIP_OR_ANSWER_EASE3(R.string.answer_good),
    FLIP_OR_ANSWER_EASE4(R.string.answer_easy),
    UNDO(R.string.undo),
    REDO(R.string.redo),
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
    SAVE_VOICE(R.string.save_voice),
    REPLAY_VOICE(R.string.replay_voice),
    TOGGLE_WHITEBOARD(R.string.gesture_toggle_whiteboard),
    CLEAR_WHITEBOARD(R.string.clear_whiteboard),
    CHANGE_WHITEBOARD_PEN_COLOR(R.string.title_whiteboard_editor),
    SHOW_HINT(R.string.gesture_show_hint),
    SHOW_ALL_HINTS(R.string.gesture_show_all_hints),
    ADD_NOTE(R.string.menu_add_note),
    RESCHEDULE_NOTE(R.string.card_editor_reschedule_card),
    USER_ACTION_1(R.string.user_action_1),
    USER_ACTION_2(R.string.user_action_2),
    USER_ACTION_3(R.string.user_action_3),
    USER_ACTION_4(R.string.user_action_4),
    USER_ACTION_5(R.string.user_action_5),
    USER_ACTION_6(R.string.user_action_6),
    USER_ACTION_7(R.string.user_action_7),
    USER_ACTION_8(R.string.user_action_8),
    USER_ACTION_9(R.string.user_action_9)
    ;

    companion object {
        val allDefaultBindings: List<MappableBinding>
            get() = Arrays.stream(entries.toTypedArray())
                .flatMap { x: ViewerCommand -> x.defaultValue.stream() }
                .collect(Collectors.toList())

        fun fromPreferenceKey(key: String) = entries.first { it.preferenceKey == key }
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
        get() {
            // all of the default commands are currently for the Reviewer
            fun keyCode(keycode: Int, side: CardSide, modifierKeys: ModifierKeys = ModifierKeys.none()) =
                keyCode(keycode, Screen.Reviewer(side), modifierKeys)
            fun unicode(c: Char, side: CardSide) = unicode(c, Screen.Reviewer(side))
            return when (this) {
                FLIP_OR_ANSWER_EASE1 -> listOf(
                    keyCode(KeyEvent.KEYCODE_BUTTON_Y, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_1, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.ANSWER)
                )
                FLIP_OR_ANSWER_EASE2 -> listOf(
                    keyCode(KeyEvent.KEYCODE_BUTTON_X, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_2, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.ANSWER)
                )
                FLIP_OR_ANSWER_EASE3 -> listOf(
                    keyCode(KeyEvent.KEYCODE_BUTTON_B, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_3, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_DPAD_CENTER, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_SPACE, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_ENTER, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, CardSide.ANSWER)
                )
                FLIP_OR_ANSWER_EASE4 -> listOf(
                    keyCode(KeyEvent.KEYCODE_BUTTON_A, CardSide.BOTH),
                    keyCode(KeyEvent.KEYCODE_4, CardSide.ANSWER),
                    keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.ANSWER)
                )
                EDIT -> listOf(keyCode(KeyEvent.KEYCODE_E, CardSide.BOTH))
                MARK -> listOf(unicode('*', CardSide.BOTH))
                BURY_CARD -> listOf(unicode('-', CardSide.BOTH))
                BURY_NOTE -> listOf(unicode('=', CardSide.BOTH))
                SUSPEND_CARD -> listOf(unicode('@', CardSide.BOTH))
                SUSPEND_NOTE -> listOf(unicode('!', CardSide.BOTH))
                PLAY_MEDIA -> listOf(keyCode(KeyEvent.KEYCODE_R, CardSide.BOTH), keyCode(KeyEvent.KEYCODE_F5, CardSide.BOTH))
                REPLAY_VOICE -> listOf(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH))
                RECORD_VOICE -> listOf(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH, shift()))
                SAVE_VOICE -> listOf(keyCode(KeyEvent.KEYCODE_S, CardSide.BOTH, shift()))
                UNDO -> listOf(keyCode(KeyEvent.KEYCODE_Z, CardSide.BOTH))
                REDO -> listOf(keyCode(KeyEvent.KEYCODE_Z, CardSide.BOTH, ModifierKeys(shift = true, ctrl = true, alt = false)))
                TOGGLE_FLAG_RED -> listOf(keyCode(KeyEvent.KEYCODE_1, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_ORANGE -> listOf(keyCode(KeyEvent.KEYCODE_2, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_GREEN -> listOf(keyCode(KeyEvent.KEYCODE_3, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_BLUE -> listOf(keyCode(KeyEvent.KEYCODE_4, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_PINK -> listOf(keyCode(KeyEvent.KEYCODE_5, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_5, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_TURQUOISE -> listOf(keyCode(KeyEvent.KEYCODE_6, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_6, CardSide.BOTH, ctrl()))
                TOGGLE_FLAG_PURPLE -> listOf(keyCode(KeyEvent.KEYCODE_7, CardSide.BOTH, ctrl()), keyCode(KeyEvent.KEYCODE_NUMPAD_7, CardSide.BOTH, ctrl()))
                SHOW_HINT -> listOf(keyCode(KeyEvent.KEYCODE_H, CardSide.BOTH))
                SHOW_ALL_HINTS -> listOf(keyCode(KeyEvent.KEYCODE_G, CardSide.BOTH))
                ADD_NOTE -> listOf(keyCode(KeyEvent.KEYCODE_A, CardSide.BOTH))
                else -> emptyList()
            }
        }

    private fun keyCode(keycode: Int, screen: Screen, keys: ModifierKeys = ModifierKeys.none()): MappableBinding {
        return MappableBinding(keyCode(keys, keycode), screen)
    }

    private fun unicode(c: Char, screen: Screen): MappableBinding {
        return MappableBinding(unicode(c), screen)
    }

    fun interface CommandProcessor {
        /**
         * @return whether the command was executed
         */
        fun executeCommand(which: ViewerCommand, fromGesture: Gesture?): Boolean
    }
}
