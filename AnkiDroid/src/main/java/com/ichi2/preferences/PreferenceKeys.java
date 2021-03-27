/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.preferences;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Lookup;
import com.ichi2.anki.Preferences;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

import static com.ichi2.anki.AnkiDroidApp.FEEDBACK_REPORT_ASK;
import static com.ichi2.anki.NavigationDrawerActivity.NIGHT_MODE_PREFERENCE;
import static com.ichi2.anki.contextmenu.AnkiCardContextMenu.ANKI_CARD_CONTEXT_MENU_PREF_KEY;
import static com.ichi2.anki.contextmenu.CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY;
import static com.ichi2.anki.reviewer.ActionButtonStatus.MENU_DISABLED;
import static com.ichi2.anki.reviewer.ActionButtonStatus.SHOW_AS_ACTION_ALWAYS;
import static com.ichi2.anki.reviewer.ActionButtonStatus.SHOW_AS_ACTION_IF_ROOM;
import static com.ichi2.anki.reviewer.ActionButtonStatus.SHOW_AS_ACTION_NEVER;
import static com.ichi2.anki.web.CustomSyncServer.PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER;

/**
 * Keys to ensure consistency between XML and code defaults
 * Tested via PreferenceTest.preferenceDefaultsAreNotChangedWhenOpeningPreferences
 * */
public class PreferenceKeys {
    public static final PreferenceKey<Boolean> UseInputTag = new PreferenceKey<>("useInputTag", false);
    public static final PreferenceKey<Boolean> NoCodeFormatting = new PreferenceKey<>("noCodeFormatting", false);
    public static final StringAsIntKey Dictionary = new StringAsIntKey("dictionary", Integer.toString(Lookup.DICTIONARY_NONE));
    public static final StringAsIntKey FullscreenMode = new StringAsIntKey("fullscreenMode", "0");
    public static final PreferenceKey<Integer> AnswerButtonSide = new PreferenceKey<>("answerButtonSize", 100);
    public static final PreferenceKey<Boolean> Tts = new PreferenceKey<>("tts", false);
    public static final PreferenceKey<Boolean> TimeoutAnswer = new PreferenceKey<>("timeoutAnswer", false);
    public static final PreferenceKey<Integer> TimeoutAnswerSeconds = new PreferenceKey<>("timeoutAnswerSeconds", 20);
    public static final PreferenceKey<Integer> TimeoutQuestionSeconds = new PreferenceKey<>("timeoutQuestionSeconds", 60);
    public static final PreferenceKey<Boolean> ScrollingButtons = new PreferenceKey<>("scrolling_buttons", false);
    public static final PreferenceKey<Boolean> DoubleScrolling = new PreferenceKey<>("double_scrolling", false);
    public static final PreferenceKey<Boolean> ShowTopBar = new PreferenceKey<>("showTopbar", true);
    public static final PreferenceKey<Boolean> LinkOverridesTouchGesture = new PreferenceKey<>("linkOverridesTouchGesture", false);

    public static final PreferenceKey<Boolean> Gestures = new PreferenceKey<>("gestures", false);
    public static final PreferenceKey<Integer> SwipeSensitivity = new PreferenceKey<>( "swipeSensitivity", 100);
    public static final StringAsIntKey GestureSwipeUp = new StringAsIntKey("gestureSwipeUp", "9");
    public static final StringAsIntKey GestureSwipeDown = new StringAsIntKey("gestureSwipeDown", "0");
    public static final StringAsIntKey GestureSwipeLeft = new StringAsIntKey("gestureSwipeLeft", "8");
    public static final StringAsIntKey GestureSwipeRight = new StringAsIntKey("gestureSwipeRight", "17");
    public static final StringAsIntKey GestureDoubleTap = new StringAsIntKey("gestureDoubleTap", "7");
    public static final StringAsIntKey GestureLongClick = new StringAsIntKey("gestureLongclick", "11");  /* This appears to be unused */
    public static final StringAsIntKey GestureVolumeUp = new StringAsIntKey("gestureVolumeUp", "0");
    public static final StringAsIntKey GestureVolumeDown = new StringAsIntKey("gestureVolumeDown", "0");

    public static final PreferenceKey<Boolean> KeepScreenOn = new PreferenceKey<>("keepScreenOn", false);
    public static final PreferenceKey<Boolean> HtmlJavascriptDebugging = new PreferenceKey<>("html_javascript_debugging", false);
    public static final PreferenceKey<String> AnswerButtonPosition = new PreferenceKey<>("answerButtonPosition", "bottom"); // This exists in constants.xml - remove?

    // card appearance
    public static final PreferenceKey<Integer> CardZoom = new PreferenceKey<>("cardZoom", 100);
    public static final PreferenceKey<Integer> ImageZoom = new PreferenceKey<>("imageZoom", 100);
    public static final PreferenceKey<Boolean> CenterVertically = new PreferenceKey<>("centerVertically", false);
    public static final PreferenceKey<Boolean> InvertedColors = new PreferenceKey<>(NIGHT_MODE_PREFERENCE, false);

    // Whiteboard
    public static final PreferenceKey<Integer> WhiteBoardStrokeWidth = new PreferenceKey<>("whiteBoardStrokeWidth", 6);

    // MyAccount
    public static final PreferenceKey<String> Username = new PreferenceKey<>("username", "");
    public static final PreferenceKey<String> HKey = new PreferenceKey<>("hkey", "");

    // DialogHandler
    public static final PreferenceKey<Long> LastSyncTime = new PreferenceKey<>("lastSyncTime", 0L);

    // Note Editor
    public static final PreferenceKey<Boolean> NoteEditorCapitalize = new PreferenceKey<>("note_editor_capitalize", true);
    public static final PreferenceKey<Boolean> NoteEditorNewlineReplace = new PreferenceKey<>("noteEditorNewlineReplace", true);
    public static final PreferenceKey<Boolean> NoteEditorShowToolbar = new PreferenceKey<>("noteEditorShowToolbar", true);
    public static final PreferenceKey<String> BrowserEditorFont = new PreferenceKey<>("browserEditorFont", ""); // and card browser
    public static final PreferenceKey<Integer> NoteEditorFontSize = new PreferenceKey<>("note_editor_font_size", -1);

    public static final PreferenceKey<Boolean> DisableExtendedTextUi = new PreferenceKey<>("disableExtendedTextUi", false);

    // Reviewer
    public static final PreferenceKey<Boolean> HideDueCount = new PreferenceKey<>("hideDueCount", false);
    public static final PreferenceKey<Boolean> ShowEta = new PreferenceKey<>("showETA", true);

    // Widget
    public static final PreferenceKey<Boolean> WidgetSmallEnabled = new PreferenceKey<>("widgetSmallEnabled", false);
    // TODO: minimumCardsDueForNotification - currently ambiguous default

    // SyncStatus
    public static final PreferenceKey<Boolean> ShowSyncStatusBadge = new PreferenceKey<>("showSyncStatusBadge", true);
    public static final PreferenceKey<Boolean> ChangesSinceLastSync = new PreferenceKey<>("changesSinceLastSync", false);


    // Themes
    public static final StringAsIntKey NightTheme = new StringAsIntKey("nightTheme", "0");
    public static final StringAsIntKey DayTheme = new StringAsIntKey("dayTheme", "0");

    // ReviewerCustomFonts
    // TODO: null or "" in code
    // public static NullablePreferenceKey<String> DefaultFont = new NullablePreferenceKey<>("defaultFont", null);
    public static final PreferenceKey<String> OverrideFontBehavior = new PreferenceKey<>("overrideFontBehavior", "0");

    // GestureTapProcessor
    public static final StringAsIntKey GestureTapLeft = new StringAsIntKey("gestureTapLeft", "3");
    public static final StringAsIntKey GestureTapRight = new StringAsIntKey("gestureTapRight", "6");
    public static final StringAsIntKey GestureTapTop = new StringAsIntKey("gestureTapTop", "12");
    public static final StringAsIntKey GestureTapBottom = new StringAsIntKey("gestureTapBottom", "2");
    public static final PreferenceKey<Boolean> GestureCornerTouch = new PreferenceKey<>("gestureCornerTouch", false);
    public static final StringAsIntKey GestureTapTopLeft = new StringAsIntKey("gestureTapTopLeft", "0");
    public static final StringAsIntKey GestureTapTopRight = new StringAsIntKey("gestureTapTopRight", "0");
    public static final StringAsIntKey GestureTapCenter = new StringAsIntKey("gestureTapCenter", "0");
    public static final StringAsIntKey GestureTapBottomLeft = new StringAsIntKey("gestureTapBottomLeft", "0");
    public static final StringAsIntKey GestureTapBottomRight = new StringAsIntKey("gestureTapBottomRight", "0");

    // ActionButtonStatus
    public static final CustomButtonPreferenceKey CustomButtonUndo = new CustomButtonPreferenceKey("customButtonUndo", SHOW_AS_ACTION_ALWAYS);
    public static final CustomButtonPreferenceKey CustomButtonScheduleCard = new CustomButtonPreferenceKey("customButtonScheduleCard", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonFlag = new CustomButtonPreferenceKey("customButtonFlag", SHOW_AS_ACTION_ALWAYS);
    public static final CustomButtonPreferenceKey CustomButtonTags = new CustomButtonPreferenceKey("customButtonTags", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonEditCard = new CustomButtonPreferenceKey("customButtonEditCard", SHOW_AS_ACTION_IF_ROOM);
    public static final CustomButtonPreferenceKey CustomButtonAddCard = new CustomButtonPreferenceKey("customButtonAddCard", MENU_DISABLED);
    public static final CustomButtonPreferenceKey CustomButtonReplay = new CustomButtonPreferenceKey("customButtonReplay", SHOW_AS_ACTION_IF_ROOM);
    public static final CustomButtonPreferenceKey CustomButtonCardInfo = new CustomButtonPreferenceKey("customButtonCardInfo", MENU_DISABLED);
    public static final CustomButtonPreferenceKey CustomButtonClearWhiteboard = new CustomButtonPreferenceKey("customButtonClearWhiteboard", SHOW_AS_ACTION_IF_ROOM);
    public static final CustomButtonPreferenceKey CustomButtonShowHideWhiteboard = new CustomButtonPreferenceKey("customButtonShowHideWhiteboard", SHOW_AS_ACTION_ALWAYS);
    public static final CustomButtonPreferenceKey CustomButtonSelectTts = new CustomButtonPreferenceKey("customButtonSelectTts", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonDeckOptions = new CustomButtonPreferenceKey("customButtonDeckOptions", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonBury = new CustomButtonPreferenceKey("customButtonBury", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonSuspend = new CustomButtonPreferenceKey("customButtonSuspend", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonMarkCard = new CustomButtonPreferenceKey("customButtonMarkCard", SHOW_AS_ACTION_IF_ROOM);
    public static final CustomButtonPreferenceKey CustomButtonDelete = new CustomButtonPreferenceKey("customButtonDelete", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonToggleMicToolBar = new CustomButtonPreferenceKey("customButtonToggleMicToolBar", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonEnableWhiteboard = new CustomButtonPreferenceKey("customButtonEnableWhiteboard", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonSaveWhiteboard = new CustomButtonPreferenceKey("customButtonSaveWhiteboard", SHOW_AS_ACTION_NEVER);
    public static final CustomButtonPreferenceKey CustomButtonWhiteboardPenColor = new CustomButtonPreferenceKey("customButtonWhiteboardPenColor", SHOW_AS_ACTION_IF_ROOM);

    // Advanced Statistics
    public static final PreferenceKey<Boolean> AdvancedStatisticsEnabled = new PreferenceKey<>("advanced_statistics_enabled", false);
    public static final PreferenceKey<Integer> AdvancedForecastStatsMcNIterations = new PreferenceKey<>("advanced_forecast_stats_mc_n_iterations", 1);
    public static final PreferenceKey<Integer> AdvancedForecastStatsComputeNDays = new PreferenceKey<>("advanced_forecast_stats_compute_n_days", 0);
    public static final PreferenceKey<Integer> AdvancedForecastStatsComputePrecision = new PreferenceKey<>("advanced_forecast_stats_compute_precision", 90);

    // ACRA
    // Note: defaulted to "" in Preferences, but should never have been hit
    public static final PreferenceKey<String> FeedbackReportKey = new PreferenceKey<>(AnkiDroidApp.FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK);

    // DeckPicker - Sync
    public static final PreferenceKey<Boolean> AutomaticSyncMode = new PreferenceKey<>("automaticSyncMode", false);
    public static final PreferenceKey<Boolean> SyncFetchesMedia = new PreferenceKey<>("syncFetchesMedia", true);

    // DeckPicker
    public static final PreferenceKey<Boolean> NoSpaceLeft = new PreferenceKey<>("noSpaceLeft", false);
    public static final PreferenceKey<String> LastVersion = new PreferenceKey<>("lastVersion", "");
    public static final PreferenceKey<Boolean> DeckPickerBackground = new PreferenceKey<>("deckPickerBackground", false);

    // Card Browser
    public static final PreferenceKey<Boolean> CardBrowserNoSorting = new PreferenceKey<>("cardBrowserNoSorting", false);
    public static final PreferenceKey<Integer> CardBrowserColumn1 = new PreferenceKey<>("cardBrowserColumn1", 0);
    public static final PreferenceKey<Integer> CardBrowserColumn2 = new PreferenceKey<>("cardBrowserColumn2", 0);
    public static final PreferenceKey<Integer> RelativeCardBrowserFontSize = new PreferenceKey<>("relativeCardBrowserFontSize", 100);
    public static final PreferenceKey<Boolean> CardBrowserShowMediaFilenames = new PreferenceKey<>("card_browser_show_media_filenames", false);

    // Notifications
    public static final PreferenceKey<Boolean> WidgetVibrate = new PreferenceKey<>("widgetVibrate", false);
    public static final PreferenceKey<Boolean> WidgetBlink = new PreferenceKey<>("widgetBlink", false);

    // Animations
    public static final PreferenceKey<Boolean> SafeDisplay = new PreferenceKey<>("safeDisplay", false);

    // Chess
    public static final PreferenceKey<Boolean> ConvertFenText = new PreferenceKey<>("convertFenText", false);

    // Context Menus
    public static final PreferenceKey<Boolean> CardBrowserContextMenu = new PreferenceKey<>(CARD_BROWSER_CONTEXT_MENU_PREF_KEY, false);
    public static final PreferenceKey<Boolean> AnkiCardContextMenu = new PreferenceKey<>(ANKI_CARD_CONTEXT_MENU_PREF_KEY, true);

    // Custom Sync Server
    public static final PreferenceKey<Boolean> EnableCustomSyncServer = new PreferenceKey<>(PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER, false);
    // TODO: variable defaults (null/empty): syncBaseUrl, syncMediaUrl

    // BootService
    public static final PreferenceKey<Integer> DayOffset = new PreferenceKey<>("dayOffset", 0);
    public static final PreferenceKey<String> Language = new PreferenceKey<>(Preferences.LANGUAGE, "");

    // Backups
    public static final PreferenceKey<Integer> BackupMax = new PreferenceKey<>("backupMax", 8);



    public static class PreferenceKey<T> {
        public final String key;
        public final T defaultValue;

        public PreferenceKey(String key, T value) {
            this.key = key;
            this.defaultValue = value;
        }
    }

    /** A value stored as a string, but uses as an int */
    public static class StringAsIntKey extends PreferenceKey<String> {
        public StringAsIntKey(String key, String value) {
            super(key, value);
        }
    }

    public static class CustomButtonPreferenceKey extends StringAsIntKey {
        public CustomButtonPreferenceKey(String key, @CustomButtonDef int value) {
            super(key, Integer.toString(value));
        }


        @Retention(RetentionPolicy.SOURCE)
        @IntDef({SHOW_AS_ACTION_NEVER, SHOW_AS_ACTION_IF_ROOM, SHOW_AS_ACTION_ALWAYS, MENU_DISABLED })
        public @interface CustomButtonDef {}
    }
}
