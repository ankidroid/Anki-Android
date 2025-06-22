/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.IdRes
import com.ichi2.anki.R
import com.ichi2.utils.HashUtil.hashMapInit

// loads of unboxing issues, which are safe
class ActionButtonStatus {
    /**
     * Custom button allocation
     */
    private val customButtons: MutableMap<Int, ShowAsAction> = hashMapInit(25) // setup's size

    fun setup(preferences: SharedPreferences) {
        // NOTE: the default values below should be in sync with preferences_custom_buttons.xml and reviewer.xml
        setupButton(preferences, R.id.action_undo, "customButtonUndo", ShowAsAction.Always)
        setupButton(preferences, R.id.action_redo, "customButtonRedo", ShowAsAction.IfRoom)
        setupButton(preferences, R.id.action_schedule, "customButtonScheduleCard", ShowAsAction.Never)
        setupButton(preferences, R.id.action_flag, "customButtonFlag", ShowAsAction.Always)
        setupButton(preferences, R.id.action_tag, "customButtonTags", ShowAsAction.Never)
        setupButton(preferences, R.id.action_edit, "customButtonEditCard", ShowAsAction.IfRoom)
        setupButton(preferences, R.id.action_add_note_reviewer, "customButtonAddCard", ShowAsAction.Disabled)
        setupButton(preferences, R.id.action_replay, "customButtonReplay", ShowAsAction.IfRoom)
        setupButton(preferences, R.id.action_card_info, "customButtonCardInfo", ShowAsAction.Disabled)
        setupButton(preferences, R.id.action_clear_whiteboard, "customButtonClearWhiteboard", ShowAsAction.IfRoom)
        setupButton(preferences, R.id.action_hide_whiteboard, "customButtonShowHideWhiteboard", ShowAsAction.Always)
        setupButton(preferences, R.id.action_select_tts, "customButtonSelectTts", ShowAsAction.Never)
        setupButton(preferences, R.id.action_open_deck_options, "customButtonDeckOptions", ShowAsAction.Never)
        setupButton(preferences, R.id.action_bury, "customButtonBury", ShowAsAction.Never)
        setupButton(preferences, R.id.action_bury_card, "customButtonBury", ShowAsAction.Never)
        setupButton(preferences, R.id.action_suspend, "customButtonSuspend", ShowAsAction.Never)
        setupButton(preferences, R.id.action_suspend_card, "customButtonSuspend", ShowAsAction.Never)
        setupButton(preferences, R.id.action_mark_card, "customButtonMarkCard", ShowAsAction.IfRoom)
        setupButton(preferences, R.id.action_delete, "customButtonDelete", ShowAsAction.Never)
        setupButton(preferences, R.id.action_toggle_mic_tool_bar, "customButtonToggleMicToolBar", ShowAsAction.Never)
        setupButton(preferences, R.id.action_toggle_whiteboard, "customButtonEnableWhiteboard", ShowAsAction.Never)
        setupButton(preferences, R.id.action_toggle_stylus, "customButtonToggleStylus", ShowAsAction.IfRoom)
        setupButton(preferences, R.id.action_save_whiteboard, "customButtonSaveWhiteboard", ShowAsAction.Never)
        setupButton(preferences, R.id.action_change_whiteboard_pen_color, "customButtonWhiteboardPenColor", ShowAsAction.IfRoom)
        setupButton(preferences, R.id.user_action_1, "customButtonUserAction1", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_2, "customButtonUserAction2", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_3, "customButtonUserAction3", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_4, "customButtonUserAction4", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_5, "customButtonUserAction5", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_6, "customButtonUserAction6", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_7, "customButtonUserAction7", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_8, "customButtonUserAction8", ShowAsAction.Disabled)
        setupButton(preferences, R.id.user_action_9, "customButtonUserAction9", ShowAsAction.Disabled)
    }

    private fun setupButton(
        preferences: SharedPreferences,
        @IdRes resourceId: Int,
        preferenceName: String,
        showAsActionType: ShowAsAction,
    ) {
        customButtons[resourceId] =
            ShowAsAction.fromCode(
                preferences
                    .getString(
                        preferenceName,
                        showAsActionType.actionEnum.toString(),
                    )!!
                    .toInt(),
            )
    }

    fun setCustomButtons(menu: Menu) {
        for ((itemId, value) in customButtons) {
            if (value != ShowAsAction.Disabled) {
                val item = menu.findItem(itemId)
                item.setShowAsAction(value.actionEnum)
            } else {
                menu.findItem(itemId).isVisible = false
            }
        }
    }

    fun hideWhiteboardIsDisabled(): Boolean = customButtons[R.id.action_hide_whiteboard] == ShowAsAction.Disabled

    fun toggleStylusIsDisabled(): Boolean = customButtons[R.id.action_toggle_stylus] == ShowAsAction.Disabled

    fun clearWhiteboardIsDisabled(): Boolean = customButtons[R.id.action_clear_whiteboard] == ShowAsAction.Disabled

    fun selectTtsIsDisabled(): Boolean = customButtons[R.id.action_select_tts] == ShowAsAction.Disabled

    fun saveWhiteboardIsDisabled(): Boolean = customButtons[R.id.action_save_whiteboard] == ShowAsAction.Disabled

    fun whiteboardPenColorIsDisabled(): Boolean = customButtons[R.id.action_change_whiteboard_pen_color] == ShowAsAction.Disabled

    fun suspendIsDisabled(): Boolean = customButtons[R.id.action_suspend] == ShowAsAction.Disabled

    fun buryIsDisabled(): Boolean = customButtons[R.id.action_bury] == ShowAsAction.Disabled

    fun flagsIsOverflown(): Boolean = customButtons[R.id.action_flag] == ShowAsAction.Never

    /**
     * @param actionEnum - How the item should display.
     */
    enum class ShowAsAction(
        val actionEnum: Int,
    ) {
        Never(MenuItem.SHOW_AS_ACTION_NEVER),
        IfRoom(MenuItem.SHOW_AS_ACTION_IF_ROOM),
        Always(MenuItem.SHOW_AS_ACTION_ALWAYS),
        Disabled(3),
        ;

        companion object {
            fun fromCode(c: Int) = ShowAsAction.entries.first { it.actionEnum == c }
        }
    }
}
