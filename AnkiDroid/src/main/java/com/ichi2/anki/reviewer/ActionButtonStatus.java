package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;

import com.ichi2.anki.R;
import com.ichi2.preferences.PreferenceKeys;
import com.ichi2.preferences.Prefs;
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
    protected final Map<Integer, Integer> mCustomButtons = new HashMap<>(25); // setup's size
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
        // TODO: Link this to test for #5346
        Prefs prefs = new Prefs(preferences);
        setupButton(prefs, R.id.action_undo, PreferenceKeys.CustomButtonUndo);
        setupButton(prefs, R.id.action_schedule, PreferenceKeys.CustomButtonScheduleCard);
        setupButton(prefs, R.id.action_flag, PreferenceKeys.CustomButtonFlag);
        setupButton(prefs, R.id.action_tag, PreferenceKeys.CustomButtonTags);
        setupButton(prefs, R.id.action_edit, PreferenceKeys.CustomButtonEditCard);
        setupButton(prefs, R.id.action_add_note_reviewer, PreferenceKeys.CustomButtonAddCard);
        setupButton(prefs, R.id.action_replay, PreferenceKeys.CustomButtonReplay);
        setupButton(prefs, R.id.action_card_info, PreferenceKeys.CustomButtonCardInfo);
        setupButton(prefs, R.id.action_clear_whiteboard, PreferenceKeys.CustomButtonClearWhiteboard);
        setupButton(prefs, R.id.action_hide_whiteboard, PreferenceKeys.CustomButtonShowHideWhiteboard);
        setupButton(prefs, R.id.action_select_tts, PreferenceKeys.CustomButtonSelectTts);
        setupButton(prefs, R.id.action_open_deck_options, PreferenceKeys.CustomButtonDeckOptions);
        setupButton(prefs, R.id.action_bury, PreferenceKeys.CustomButtonBury);
        setupButton(prefs, R.id.action_suspend, PreferenceKeys.CustomButtonSuspend);
        setupButton(prefs, R.id.action_mark_card, PreferenceKeys.CustomButtonMarkCard);
        setupButton(prefs, R.id.action_delete, PreferenceKeys.CustomButtonDelete);
        setupButton(prefs, R.id.action_toggle_mic_tool_bar, PreferenceKeys.CustomButtonToggleMicToolBar);
        setupButton(prefs, R.id.action_toggle_whiteboard, PreferenceKeys.CustomButtonEnableWhiteboard);
        setupButton(prefs, R.id.action_save_whiteboard, PreferenceKeys.CustomButtonSaveWhiteboard);
        setupButton(prefs, R.id.action_change_whiteboard_pen_color, PreferenceKeys.CustomButtonWhiteboardPenColor);
    }


    private void setupButton(Prefs preferences, @IdRes int resourceId, PreferenceKeys.StringAsIntKey key) {
        mCustomButtons.put(resourceId, preferences.getIntFromStr(key));
    }


    public void setCustomButtons(Menu menu) {
        for(Map.Entry<Integer, Integer> entry : mCustomButtons.entrySet()) {
            int itemId = entry.getKey();
            if (entry.getValue() != MENU_DISABLED) {
                MenuItem item = menu.findItem(itemId);
                if (item == null) {
                    // Happens with TV - removing flag icon
                    Timber.w("Could not find Menu Item %d", itemId);
                    continue;
                }

                item.setShowAsAction(entry.getValue());
                Drawable icon = item.getIcon();
                item.setEnabled(!mReviewerUi.isControlBlocked());
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
                    Drawable mutableIcon = icon.mutate();
                    if (mReviewerUi.getControlBlocked() == ReviewerUi.ControlBlock.SLOW) {
                        mutableIcon.setAlpha(Themes.ALPHA_ICON_DISABLED_LIGHT);
                    } else {
                        mutableIcon.setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
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

    public boolean saveWhiteboardIsDisabled() {
        return mCustomButtons.get(R.id.action_save_whiteboard) == MENU_DISABLED;
    }

    public boolean whiteboardPenColorIsDisabled() {
        return mCustomButtons.get(R.id.action_change_whiteboard_pen_color) == MENU_DISABLED;
    }
}
