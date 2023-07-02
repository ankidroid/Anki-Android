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
import com.ichi2.themes.Themes
import com.ichi2.utils.HashUtil.HashMapInit
import timber.log.Timber

// loads of unboxing issues, which are safe
class ActionButtonStatus(private val reviewerUi: ReviewerUi) {
    /**
     * Custom button allocation
     */
    protected val mCustomButtons: MutableMap<Int, Int> = HashMapInit(25) // setup's size
    fun getByMenuResourceId(resourceId: Int): Int? {
        if (!mCustomButtons.containsKey(resourceId)) {
            Timber.w("Invalid resource lookup: %d", resourceId)
            return SHOW_AS_ACTION_NEVER
        }
        return mCustomButtons[resourceId]
    }

    fun setup(preferences: SharedPreferences) {
        // NOTE: the default values below should be in sync with preferences_custom_buttons.xml and reviewer.xml
        setupButton(preferences, R.id.action_undo, "customButtonUndo", SHOW_AS_ACTION_ALWAYS)
        setupButton(preferences, R.id.action_schedule, "customButtonScheduleCard", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_flag, "customButtonFlag", SHOW_AS_ACTION_ALWAYS)
        setupButton(preferences, R.id.action_tag, "customButtonTags", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_edit, "customButtonEditCard", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_add_note_reviewer, "customButtonAddCard", MENU_DISABLED)
        setupButton(preferences, R.id.action_replay, "customButtonReplay", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_card_info, "customButtonCardInfo", MENU_DISABLED)
        setupButton(preferences, R.id.action_clear_whiteboard, "customButtonClearWhiteboard", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_hide_whiteboard, "customButtonShowHideWhiteboard", SHOW_AS_ACTION_ALWAYS)
        setupButton(preferences, R.id.action_select_tts, "customButtonSelectTts", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_open_deck_options, "customButtonDeckOptions", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_bury, "customButtonBury", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_suspend, "customButtonSuspend", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_mark_card, "customButtonMarkCard", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_delete, "customButtonDelete", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_toggle_mic_tool_bar, "customButtonToggleMicToolBar", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_toggle_whiteboard, "customButtonEnableWhiteboard", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_toggle_stylus, "customButtonToggleStylus", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_save_whiteboard, "customButtonSaveWhiteboard", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_change_whiteboard_pen_color, "customButtonWhiteboardPenColor", SHOW_AS_ACTION_IF_ROOM)
    }

    private fun setupButton(preferences: SharedPreferences, @IdRes resourceId: Int, preferenceName: String, showAsActionType: Int) {
        mCustomButtons[resourceId] = preferences.getString(preferenceName, Integer.toString(showAsActionType))!!.toInt()
    }

    fun setCustomButtons(menu: Menu) {
        for ((itemId, value) in mCustomButtons) {
            if (value != MENU_DISABLED) {
                val item = menu.findItem(itemId)
                item.setShowAsAction(value)
                val icon = item.icon
                item.isEnabled = !reviewerUi.isControlBlocked
                if (icon != null) {
                    /* Ideally, we want to give feedback to users that
                    buttons are disabled.  However, some actions are
                    expected to be so quick that the visual feedback
                    is useless and is only seen as a flickering.

                    We use a heuristic to decide whether the next card
                    will appear quickly or slowly.  We change the
                    color only if the buttons are blocked and we
                    expect the next card to take time to arrive.
                    */
                    val mutableIcon = icon.mutate()
                    if (reviewerUi.controlBlocked == ReviewerUi.ControlBlock.SLOW) {
                        mutableIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                    } else {
                        mutableIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                    }
                }
            } else {
                menu.findItem(itemId).isVisible = false
            }
        }
    }

    fun hideWhiteboardIsDisabled(): Boolean {
        return mCustomButtons[R.id.action_hide_whiteboard] == MENU_DISABLED
    }

    fun toggleStylusIsDisabled(): Boolean {
        return mCustomButtons[R.id.action_toggle_stylus] == MENU_DISABLED
    }

    fun clearWhiteboardIsDisabled(): Boolean {
        return mCustomButtons[R.id.action_clear_whiteboard] == MENU_DISABLED
    }

    fun selectTtsIsDisabled(): Boolean {
        return mCustomButtons[R.id.action_select_tts] == MENU_DISABLED
    }

    fun saveWhiteboardIsDisabled(): Boolean {
        return mCustomButtons[R.id.action_save_whiteboard] == MENU_DISABLED
    }

    fun whiteboardPenColorIsDisabled(): Boolean {
        return mCustomButtons[R.id.action_change_whiteboard_pen_color] == MENU_DISABLED
    }

    companion object {
        const val SHOW_AS_ACTION_NEVER = MenuItem.SHOW_AS_ACTION_NEVER
        const val SHOW_AS_ACTION_IF_ROOM = MenuItem.SHOW_AS_ACTION_IF_ROOM
        const val SHOW_AS_ACTION_ALWAYS = MenuItem.SHOW_AS_ACTION_ALWAYS
        const val MENU_DISABLED = 3
    }
}
