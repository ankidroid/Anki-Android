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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

import static com.ichi2.anki.AnkiDroidApp.FEEDBACK_REPORT_ASK;
import static com.ichi2.anki.contextmenu.AnkiCardContextMenu.ANKI_CARD_CONTEXT_MENU_PREF_KEY;
import static com.ichi2.anki.contextmenu.CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY;
import static com.ichi2.anki.reviewer.ActionButtonStatus.MENU_DISABLED;
import static com.ichi2.anki.reviewer.ActionButtonStatus.SHOW_AS_ACTION_ALWAYS;
import static com.ichi2.anki.reviewer.ActionButtonStatus.SHOW_AS_ACTION_IF_ROOM;
import static com.ichi2.anki.reviewer.ActionButtonStatus.SHOW_AS_ACTION_NEVER;
import static com.ichi2.anki.web.CustomSyncServer.PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER;

/**
 * Keys to ensure consistency between XML and code defaults
 * TODO: No tests have been run on this yet
 * */
public class PreferenceKeys {
    public static PreferenceKey<Boolean> UseInputTag = new PreferenceKey<>("useInputTag", false);
    public static PreferenceKey<Boolean> NoCodeFormatting = new PreferenceKey<>("noCodeFormatting", false);
    public static PreferenceKey<String> Dictionary = new PreferenceKey<>("dictionary", Integer.toString(Lookup.DICTIONARY_NONE));
    public static PreferenceKey<String> FullscreenMode = new PreferenceKey<>("fullscreenMode", "0");
    public static PreferenceKey<Integer> AnswerButtonSide = new PreferenceKey<>("answerButtonSize", 100);
    public static PreferenceKey<Boolean> Tts = new PreferenceKey<>("tts", false);
    public static PreferenceKey<Boolean> TimeoutAnswer = new PreferenceKey<>("timeoutAnswer", false);
    public static PreferenceKey<Integer> TimeoutAnswerSeconds = new PreferenceKey<>("timeoutAnswerSeconds", 20);
    public static PreferenceKey<Integer> TimeoutQuestionSeconds = new PreferenceKey<>("timeoutQuestionSeconds", 60);
    public static PreferenceKey<Boolean> ScrollingButtons = new PreferenceKey<>("scrolling_buttons", false);
    public static PreferenceKey<Boolean> DoubleScrolling = new PreferenceKey<>("double_scrolling", false);
    public static PreferenceKey<Boolean> ShowTopBar = new PreferenceKey<>("showTopbar", true);
    public static PreferenceKey<Boolean> LinkOverridesTouchGesture = new PreferenceKey<>("linkOverridesTouchGesture", false);

    public static PreferenceKey<String> GestureSwipeUp = new PreferenceKey<>("gestureSwipeUp", "9");
    public static PreferenceKey<String> GestureSwipeDown = new PreferenceKey<>("gestureSwipeDown", "0");
    public static PreferenceKey<String> GestureSwipeLeft = new PreferenceKey<>("gestureSwipeLeft", "8");
    public static PreferenceKey<String> GestureSwipeRight = new PreferenceKey<>("gestureSwipeRight", "17");
    public static PreferenceKey<String> GestureDoubleTap = new PreferenceKey<>("gestureDoubleTap", "7");
    public static PreferenceKey<String> GestureLongClick = new PreferenceKey<>("gestureLongclick", "11");  /* This appears to be unused */
    public static PreferenceKey<String> GestureVolumeUp = new PreferenceKey<>("gestureVolumeUp", "0");
    public static PreferenceKey<String> GestureVolumeDown = new PreferenceKey<>("gestureVolumeDown", "0");

    public static PreferenceKey<Boolean> KeepScreenOn = new PreferenceKey<>("keepScreenOn", false);
    public static PreferenceKey<Boolean> HtmlJavascriptDebugging = new PreferenceKey<>("html_javascript_debugging", false);
    public static PreferenceKey<String> AnswerButtonPosition = new PreferenceKey<>("answerButtonPosition", "bottom"); // This exists in constants.xml - remove?

    // card appearance
    public static PreferenceKey<Integer> CardZoom = new PreferenceKey<>("cardZoom", 100);
    public static PreferenceKey<Integer> ImageZoom = new PreferenceKey<>("imageZoom", 100);
    public static PreferenceKey<Boolean> CenterVertically = new PreferenceKey<>("centerVertically", false);
    public static PreferenceKey<Boolean> InvertedColors = new PreferenceKey<>("invertedColors", false);

    // Whiteboard
    public static PreferenceKey<Integer> WhiteBoardStrokeWidth = new PreferenceKey<>("whiteBoardStrokeWidth", 6);

    // MyAccount
    public static PreferenceKey<String> Username = new PreferenceKey<>("username", "");
    public static PreferenceKey<String> HKey = new PreferenceKey<>("hkey", "");

    // DialogHandler
    public static PreferenceKey<Long> LastSyncTime = new PreferenceKey<>("lastSyncTime", 0L);

    // Note Editor
    public static PreferenceKey<Boolean> NoteEditorCapitalize = new PreferenceKey<>("note_editor_capitalize", true);
    public static PreferenceKey<Boolean> NoteEditorNewlineReplace = new PreferenceKey<>("noteEditorNewlineReplace", true);
    public static PreferenceKey<Boolean> NoteEditorShowToolbar = new PreferenceKey<>("noteEditorShowToolbar", true);
    public static PreferenceKey<String> BrowserEditorFont = new PreferenceKey<>("browserEditorFont", ""); // and card browser
    public static PreferenceKey<Integer> NoteEditorFontSize = new PreferenceKey<>("note_editor_font_size", -1);

    // Reviewer
    public static PreferenceKey<Boolean> HideDueCount = new PreferenceKey<>("hideDueCount", false);
    public static PreferenceKey<Boolean> ShowEta = new PreferenceKey<>("showETA", true);

    // Widget
    public static PreferenceKey<Boolean> WidgetSmallEnabled = new PreferenceKey<>("widgetSmallEnabled", false);
    // TODO: minimumCardsDueForNotification - currently ambiguous default

    // SyncStatus
    public static PreferenceKey<Boolean> ShowSyncStatusBadge = new PreferenceKey<>("showSyncStatusBadge", true);
    public static PreferenceKey<Boolean> ChangesSinceLastSync = new PreferenceKey<>("changesSinceLastSync", false);


    // Themes
    public static PreferenceKey<String> NightTheme = new PreferenceKey<>("nightTheme", "0");
    public static PreferenceKey<String> DayTheme = new PreferenceKey<>("dayTheme", "0");

    // ReviewerCustomFonts
    // TODO: null or "" in code
    // public static NullablePreferenceKey<String> DefaultFont = new NullablePreferenceKey<>("defaultFont", null);
    public static PreferenceKey<String> OverrideFontBehavior = new PreferenceKey<>("overrideFontBehavior", "0");

    // GestureTapProcessor
    public static PreferenceKey<String> GestureTapLeft = new PreferenceKey<>("gestureTapLeft", "3");
    public static PreferenceKey<String> GestureTapRight = new PreferenceKey<>("gestureTapRight", "6");
    public static PreferenceKey<String> GestureTapTop = new PreferenceKey<>("gestureTapTop", "12");
    public static PreferenceKey<String> GestureTapBottom = new PreferenceKey<>("gestureTapBottom", "2");
    public static PreferenceKey<Boolean> GestureCornerTouch = new PreferenceKey<>("gestureCornerTouch", false);
    public static PreferenceKey<String> GestureTapTopLeft = new PreferenceKey<>("gestureTapTopLeft", "0");
    public static PreferenceKey<String> GestureTapTopRight = new PreferenceKey<>("gestureTapTopRight", "0");
    public static PreferenceKey<String> GestureTapCenter = new PreferenceKey<>("gestureTapCenter", "0");
    public static PreferenceKey<String> GestureTapBottomLeft = new PreferenceKey<>("gestureTapBottomLeft", "0");
    public static PreferenceKey<String> GestureTapBottomRight = new PreferenceKey<>("gestureTapBottomRight", "0");

    // ActionButtonStatus
    public static CustomButtonPreferenceKey CustomButtonUndo = new CustomButtonPreferenceKey("customButtonUndo", SHOW_AS_ACTION_ALWAYS);
    public static CustomButtonPreferenceKey CustomButtonScheduleCard = new CustomButtonPreferenceKey("customButtonScheduleCard", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonFlag = new CustomButtonPreferenceKey("customButtonFlag", SHOW_AS_ACTION_ALWAYS);
    public static CustomButtonPreferenceKey CustomButtonTags = new CustomButtonPreferenceKey("customButtonTags", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonEditCard = new CustomButtonPreferenceKey("customButtonEditCard", SHOW_AS_ACTION_IF_ROOM);
    public static CustomButtonPreferenceKey CustomButtonAddCard = new CustomButtonPreferenceKey("customButtonAddCard", MENU_DISABLED);
    public static CustomButtonPreferenceKey CustomButtonReplay = new CustomButtonPreferenceKey("customButtonReplay", SHOW_AS_ACTION_IF_ROOM);
    public static CustomButtonPreferenceKey CustomButtonCardInfo = new CustomButtonPreferenceKey("customButtonCardInfo", MENU_DISABLED);
    public static CustomButtonPreferenceKey CustomButtonClearWhiteboard = new CustomButtonPreferenceKey("customButtonClearWhiteboard", SHOW_AS_ACTION_IF_ROOM);
    public static CustomButtonPreferenceKey CustomButtonShowHideWhiteboard = new CustomButtonPreferenceKey("customButtonShowHideWhiteboard", SHOW_AS_ACTION_ALWAYS);
    public static CustomButtonPreferenceKey CustomButtonSelectTts = new CustomButtonPreferenceKey("customButtonSelectTts", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonDeckOptions = new CustomButtonPreferenceKey("customButtonDeckOptions", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonBury = new CustomButtonPreferenceKey("customButtonBury", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonSuspend = new CustomButtonPreferenceKey("customButtonSuspend", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonMarkCard = new CustomButtonPreferenceKey("customButtonMarkCard", SHOW_AS_ACTION_IF_ROOM);
    public static CustomButtonPreferenceKey CustomButtonDelete = new CustomButtonPreferenceKey("customButtonDelete", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonToggleMicToolBar = new CustomButtonPreferenceKey("customButtonToggleMicToolBar", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonEnableWhiteboard = new CustomButtonPreferenceKey("customButtonEnableWhiteboard", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonSaveWhiteboard = new CustomButtonPreferenceKey("customButtonSaveWhiteboard", SHOW_AS_ACTION_NEVER);
    public static CustomButtonPreferenceKey CustomButtonWhiteboardPenColor = new CustomButtonPreferenceKey("customButtonWhiteboardPenColor", SHOW_AS_ACTION_IF_ROOM);

    // Advanced Statistics
    public static PreferenceKey<Boolean> AdvancedStatisticsEnabled = new PreferenceKey<>("advanced_statistics_enabled", false);
    public static PreferenceKey<Integer> AdvancedForecastStatsMcNIterations = new PreferenceKey<>("advanced_forecast_stats_mc_n_iterations", 1);
    public static PreferenceKey<Integer> AdvancedForecastStatsComputeNDays = new PreferenceKey<>("advanced_forecast_stats_compute_n_days", 0);
    public static PreferenceKey<Integer> AdvancedForecastStatsComputePrecision = new PreferenceKey<>("advanced_forecast_stats_compute_precision", 90);

    // ACRA
    // Note: defaulted to "" in Preferences, but should never have been hit
    public static PreferenceKey<String> FeedbackReportKey = new PreferenceKey<>(AnkiDroidApp.FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK);

    // DeckPicker - Sync
    public static PreferenceKey<Boolean> AutomaticSyncMode = new PreferenceKey<>("automaticSyncMode", false);
    public static PreferenceKey<Boolean> SyncFetchesMedia = new PreferenceKey<>("syncFetchesMedia", true);

    // DeckPicker
    public static PreferenceKey<Boolean> NoSpaceLeft = new PreferenceKey<>("noSpaceLeft", false);
    public static PreferenceKey<String> LastVersion = new PreferenceKey<>("lastVersion", "");
    public static PreferenceKey<Boolean> DeckPickerBackground = new PreferenceKey<>("deckPickerBackground", false);

    // Card Browser
    public static PreferenceKey<Boolean> CardBrowserNoSorting = new PreferenceKey<>("cardBrowserNoSorting", false);
    public static PreferenceKey<Integer> CardBrowserColumn1 = new PreferenceKey<>("cardBrowserColumn1", 0);
    public static PreferenceKey<Integer> CardBrowserColumn2 = new PreferenceKey<>("cardBrowserColumn2", 0);
    public static PreferenceKey<Integer> RelativeCardBrowserFontSize = new PreferenceKey<>("relativeCardBrowserFontSize", 100);
    public static PreferenceKey<Boolean> CardBrowserShowMediaFilenames = new PreferenceKey<>("card_browser_show_media_filenames", false);

    // Notifications
    public static PreferenceKey<Boolean> WidgetVibrate = new PreferenceKey<>("widgetVibrate", false);
    public static PreferenceKey<Boolean> WidgetBlink = new PreferenceKey<>("widgetBlink", false);

    // Animations
    public static PreferenceKey<Boolean> SafeDisplay = new PreferenceKey<>("safeDisplay", false);

    // Chess
    public static PreferenceKey<Boolean> ConvertFenText = new PreferenceKey<>("convertFenText", false);

    // Context Menus
    public static PreferenceKey<Boolean> CardBrowserContextMenu = new PreferenceKey<>(CARD_BROWSER_CONTEXT_MENU_PREF_KEY, false);
    public static PreferenceKey<Boolean> AnkiCardContextMenu = new PreferenceKey<>(ANKI_CARD_CONTEXT_MENU_PREF_KEY, true);

    // Custom Sync Server
    public static PreferenceKey<Boolean> EnableCustomSyncServer = new PreferenceKey<>(PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER, false);
    // TODO: variable defaults (null/empty): syncBaseUrl, syncMediaUrl


    public static class PreferenceKey<T> {
        public String key;
        public T defaultValue;

        public PreferenceKey(String key, T value) {
            this.key = key;
            this.defaultValue = value;
        }
    }


    public static class CustomButtonPreferenceKey extends PreferenceKey<String> {
        public CustomButtonPreferenceKey(String key, @CustomButtonDef int value) {
            super(key, Integer.toString(value));
        }


        @Retention(RetentionPolicy.SOURCE)
        @IntDef({SHOW_AS_ACTION_NEVER, SHOW_AS_ACTION_IF_ROOM, SHOW_AS_ACTION_ALWAYS, MENU_DISABLED })
        public @interface CustomButtonDef {}
    }
}
