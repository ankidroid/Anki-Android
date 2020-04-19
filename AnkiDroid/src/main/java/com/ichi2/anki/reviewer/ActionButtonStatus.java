package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;

import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

import java.util.HashMap;
import java.util.Map;

public class ActionButtonStatus {
    /**
     * Custom button allocation
     */
    protected Map<Integer, Integer> mCustomButtons = new HashMap<>();
    private final ReviewerUi mReviewerUi;

    protected static final int MENU_DISABLED = 3;

    public ActionButtonStatus(ReviewerUi reviewerUi) {
        this.mReviewerUi = reviewerUi;
    }

    public void setup(SharedPreferences preferences) {
        mCustomButtons.put(R.id.action_undo, Integer.parseInt(preferences.getString("customButtonUndo", Integer.toString(MenuItem.SHOW_AS_ACTION_ALWAYS))));
        mCustomButtons.put(R.id.action_schedule, Integer.parseInt(preferences.getString("customButtonScheduleCard", Integer.toString(MenuItem.SHOW_AS_ACTION_NEVER))));
        mCustomButtons.put(R.id.action_flag, Integer.parseInt(preferences.getString("customButtonFlag", Integer.toString(MenuItem.SHOW_AS_ACTION_IF_ROOM))));
        mCustomButtons.put(R.id.action_edit, Integer.parseInt(preferences.getString("customButtonEditCard", Integer.toString(MenuItem.SHOW_AS_ACTION_IF_ROOM))));
        mCustomButtons.put(R.id.action_add_note_reviewer, Integer.parseInt(preferences.getString("customButtonAddCard", Integer.toString(MENU_DISABLED))));
        mCustomButtons.put(R.id.action_replay, Integer.parseInt(preferences.getString("customButtonReplay", Integer.toString(MenuItem.SHOW_AS_ACTION_IF_ROOM))));
        mCustomButtons.put(R.id.action_clear_whiteboard, Integer.parseInt(preferences.getString("customButtonClearWhiteboard", Integer.toString(MenuItem.SHOW_AS_ACTION_IF_ROOM))));
        mCustomButtons.put(R.id.action_hide_whiteboard, Integer.parseInt(preferences.getString("customButtonShowHideWhiteboard", Integer.toString(MenuItem.SHOW_AS_ACTION_ALWAYS))));
        mCustomButtons.put(R.id.action_select_tts, Integer.parseInt(preferences.getString("customButtonSelectTts", Integer.toString(MenuItem.SHOW_AS_ACTION_NEVER))));
        mCustomButtons.put(R.id.action_open_deck_options, Integer.parseInt(preferences.getString("customButtonDeckOptions", Integer.toString(MenuItem.SHOW_AS_ACTION_NEVER))));
        mCustomButtons.put(R.id.action_bury, Integer.parseInt(preferences.getString("customButtonBury", Integer.toString(MenuItem.SHOW_AS_ACTION_NEVER))));
        mCustomButtons.put(R.id.action_suspend, Integer.parseInt(preferences.getString("customButtonSuspend", Integer.toString(MenuItem.SHOW_AS_ACTION_NEVER))));
        mCustomButtons.put(R.id.action_mark_card, Integer.parseInt(preferences.getString("customButtonMarkCard", Integer.toString(MenuItem.SHOW_AS_ACTION_ALWAYS))));
        mCustomButtons.put(R.id.action_delete, Integer.parseInt(preferences.getString("customButtonDelete", Integer.toString(MenuItem.SHOW_AS_ACTION_NEVER))));
    }

    public void setCustomButtons(Menu menu) {
        for(int itemId : mCustomButtons.keySet()) {
            if (mCustomButtons.get(itemId) != MENU_DISABLED) {
                MenuItem item = menu.findItem(itemId);
                item.setShowAsAction(mCustomButtons.get(itemId));
                Drawable icon = item.getIcon();
                if (mReviewerUi.getControlBlocked()) {
                    item.setEnabled(false);
                    if (icon != null) {
                        icon.setAlpha(Themes.ALPHA_ICON_DISABLED_LIGHT);
                    }
                } else {
                    item.setEnabled(true);
                    if (icon != null) {
                        icon.setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
                    }
                }
            } else {
                menu.findItem(itemId).setVisible(false);
            }
        }
    }


    public boolean hideWhiteboardIsDisabled() {
        return mCustomButtons.get(R.id.action_hide_whiteboard) == MENU_DISABLED;
    }

    public boolean clearWhiteboardIsDisabled() {
        return mCustomButtons.get(R.id.action_clear_whiteboard) == MENU_DISABLED;
    }

    public boolean selectTtsIsDisabled() {
        return mCustomButtons.get(R.id.action_select_tts) == MENU_DISABLED;
    }
}
