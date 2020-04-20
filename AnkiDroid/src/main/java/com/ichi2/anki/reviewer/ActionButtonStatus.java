package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;

import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

@SuppressWarnings("ConstantConditions") //loads of unboxing issues, which are safe
public class ActionButtonStatus {
    /**
     * Custom button allocation
     */
    @NonNull
    protected Map<Integer, Integer> mCustomButtons = new HashMap<>();
    private final ReviewerUi mReviewerUi;

    public static final int SHOW_AS_ACTION_NEVER = MenuItem.SHOW_AS_ACTION_NEVER;
    public static final int SHOW_AS_ACTION_IF_ROOM = MenuItem.SHOW_AS_ACTION_IF_ROOM;
    public static final int SHOW_AS_ACTION_ALWAYS = MenuItem.SHOW_AS_ACTION_ALWAYS;
    public static final int MENU_DISABLED = 3;

    public @Nullable Integer getByMenuResourceId(int resourceId) {
        if (!mCustomButtons.containsKey(resourceId)) {
            Timber.w("Invalid resource lookup: %d", resourceId);
            return SHOW_AS_ACTION_NEVER;
        }
        return mCustomButtons.get(resourceId);
    }


    public ActionButtonStatus(ReviewerUi reviewerUi) {
        this.mReviewerUi = reviewerUi;
    }

    public void setup(SharedPreferences preferences) {
        // NOTE: the default values below should be in sync with preferences_custom_buttons.xml and reviewer.xml
        setupButton(preferences, R.id.action_undo, "customButtonUndo", SHOW_AS_ACTION_ALWAYS);
        setupButton(preferences, R.id.action_schedule, "customButtonScheduleCard", SHOW_AS_ACTION_NEVER);
        setupButton(preferences, R.id.action_flag, "customButtonFlag", SHOW_AS_ACTION_ALWAYS);
        setupButton(preferences, R.id.action_edit, "customButtonEditCard", SHOW_AS_ACTION_IF_ROOM);
        setupButton(preferences, R.id.action_add_note_reviewer, "customButtonAddCard", MENU_DISABLED);
        setupButton(preferences, R.id.action_replay, "customButtonReplay", SHOW_AS_ACTION_IF_ROOM);
        setupButton(preferences, R.id.action_clear_whiteboard, "customButtonClearWhiteboard", SHOW_AS_ACTION_IF_ROOM);
        setupButton(preferences, R.id.action_hide_whiteboard, "customButtonShowHideWhiteboard", SHOW_AS_ACTION_ALWAYS);
        setupButton(preferences, R.id.action_select_tts, "customButtonSelectTts", SHOW_AS_ACTION_NEVER);
        setupButton(preferences, R.id.action_open_deck_options, "customButtonDeckOptions", SHOW_AS_ACTION_NEVER);
        setupButton(preferences, R.id.action_bury, "customButtonBury", SHOW_AS_ACTION_NEVER);
        setupButton(preferences, R.id.action_suspend, "customButtonSuspend", SHOW_AS_ACTION_NEVER);
        setupButton(preferences, R.id.action_mark_card, "customButtonMarkCard", SHOW_AS_ACTION_IF_ROOM);
        setupButton(preferences, R.id.action_delete, "customButtonDelete", SHOW_AS_ACTION_NEVER);
    }


    private void setupButton(SharedPreferences preferences, @IdRes int resourceId, String preferenceName, int showAsActionType) {
        mCustomButtons.put(resourceId, Integer.parseInt(preferences.getString(preferenceName, Integer.toString(showAsActionType))));
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
