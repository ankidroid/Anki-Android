/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.themes;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Themes {

    public final static int THEME_ANDROID_DARK = 0;
    public final static int THEME_ANDROID_LIGHT = 1;
    public final static int THEME_BLUE = 2;
    public final static int THEME_WHITE = 3;
    public final static int THEME_FLAT = 4;
    public final static int THEME_NO_THEME = 100;

    public final static int CALLER_STUDYOPTIONS = 1;
    public final static int CALLER_DECKPICKER_DECK = 3;
    public final static int CALLER_REVIEWER = 4;
    public final static int CALLER_FEEDBACK = 5;
    public final static int CALLER_DOWNLOAD_DECK = 6;
    public final static int CALLER_DECKPICKER = 7;
    public final static int CALLER_CARDBROWSER = 8;
    public final static int CALLER_CARDEDITOR_INTENTDIALOG = 9;
    public final static int CALLER_CARD_EDITOR = 10;

    private static int mCurrentTheme = -1;
    private static int mProgressbarsBackgroundColor;
    private static int mProgressbarsFrameColor;
    private static int mProgressbarsMatureColor;
    private static int mProgressbarsYoungColor;
    private static int mProgressbarsDeckpickerYoungColor;
    private static int mReviewerBackground = 0;
    private static int mReviewerProgressbar = 0;
    private static int mFlashcardBorder = 0;
    private static int mDeckpickerItemBorder = 0;
    private static int mTitleStyle = 0;
    private static int mTitleTextColor;
    private static int mTextViewStyle = 0;
    private static int mWallpaper = 0;
    private static int mBackgroundColor;
    private static int mBackgroundDarkColor = 0;
    private static int mDialogBackgroundColor = 0;
    private static int mToastBackground = 0;
    private static int[] mCardbrowserItemBorder;
    private static int[] mChartColors;
    private static int mPopupTopDark;
    private static int mPopupTopMedium;
    private static int mPopupTopBright;
    private static int mPopupCenterDark;
    private static int mPopupCenterBright;
    private static int mPopupCenterMedium;
    private static int mPopupBottomDark;
    private static int mPopupBottomBright;
    private static int mPopupBottomMedium;
    private static int mPopupFullDark;
    private static int mPopupFullMedium;
    private static int mPopupFullBright;
    private static int mDividerHorizontalBright;
    private static Typeface mLightFont;
    private static Typeface mRegularFont;
    private static Typeface mBoldFont;
    private static int mProgressDialogFontColor;
    private static int mNightModeButton;

    private static Context mContext;


    public static void applyTheme(Context context) {
        applyTheme(context, -1);
    }


    public static void applyTheme(Context context, int theme) {
        mContext = context;
        if (mCurrentTheme == -1) {
            loadTheme();
        }
        switch (theme == -1 ? mCurrentTheme : theme) {
            case THEME_ANDROID_DARK:
                context.setTheme(android.R.style.Theme_Black);
                Log.i(AnkiDroidApp.TAG, "Set theme: dark");
                break;
            case THEME_ANDROID_LIGHT:
                context.setTheme(android.R.style.Theme_Light);
                Log.i(AnkiDroidApp.TAG, "Set theme: light");
                break;
            case THEME_BLUE:
                context.setTheme(R.style.Theme_Blue);
                Log.i(AnkiDroidApp.TAG, "Set theme: blue");
                break;
            case THEME_FLAT:
                context.setTheme(R.style.Theme_Flat);
                Log.i(AnkiDroidApp.TAG, "Set theme: flat");
                break;
            case THEME_WHITE:
                context.setTheme(R.style.Theme_White);
                Log.i(AnkiDroidApp.TAG, "Set theme: white");
                break;
            case -1:
                break;
        }
    }


    public static void setContentStyle(View view, int caller) {
        setFont(view);
        switch (caller) {
            case CALLER_STUDYOPTIONS:
                // ((View)
                // view.findViewById(R.id.studyoptions_progressbar1_border)).setBackgroundResource(mProgressbarsFrameColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_progressbar2_border)).setBackgroundResource(mProgressbarsFrameColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_global_limit_bars)).setBackgroundResource(mProgressbarsFrameColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_progressbar4_border)).setBackgroundResource(mProgressbarsFrameColor);
                //
                // ((View)
                // view.findViewById(R.id.studyoptions_bars_max)).setBackgroundResource(mProgressbarsBackgroundColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_progressbar2_content)).setBackgroundResource(mProgressbarsBackgroundColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_global_limit_bars_content)).setBackgroundResource(mProgressbarsBackgroundColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_progressbar4_content)).setBackgroundResource(mProgressbarsBackgroundColor);
                //
                // ((View)
                // view.findViewById(R.id.studyoptions_global_mat_limit_bar)).setBackgroundResource(mProgressbarsMatureColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_global_mat_bar)).setBackgroundResource(mProgressbarsMatureColor);
                //
                // ((View)
                // view.findViewById(R.id.studyoptions_global_limit_bar)).setBackgroundResource(mProgressbarsYoungColor);
                // ((View)
                // view.findViewById(R.id.studyoptions_global_bar)).setBackgroundResource(mProgressbarsYoungColor);

                if (mCurrentTheme == THEME_WHITE) {
                    setMargins(view.findViewById(R.id.studyoptions_deck_name), LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT, 0, 6f, 0, 2f);
                    // setMargins(view.findViewById(R.id.studyoptions_statistic_field), LayoutParams.WRAP_CONTENT,
                    // LayoutParams.WRAP_CONTENT, 0, 2f, 0, 12f);
                    ((View) view.findViewById(R.id.studyoptions_deckinformation))
                            .setBackgroundResource(R.drawable.white_textview);
                    // ((View)
                    // view.findViewById(R.id.studyoptions_statistic_field)).setBackgroundResource(R.color.transparent);
                    ((View) view.findViewById(R.id.studyoptions_deck_name)).setVisibility(View.VISIBLE);
                    // ((View)
                    // view.findViewById(R.id.studyoptions_deckinformation)).setBackgroundResource(mTextViewStyle);
                    ((View) view.findViewById(R.id.studyoptions_main))
                            .setBackgroundResource(R.drawable.white_wallpaper);
                } else {
                    // ((View)
                    // view.findViewById(R.id.studyoptions_statistic_field)).setBackgroundResource(mTextViewStyle);
                    ((View) view.findViewById(R.id.studyoptions_main)).setBackgroundResource(mWallpaper);
                }
                break;

            case CALLER_DECKPICKER:
                ListView lv = (ListView) view.findViewById(R.id.files);
                switch (mCurrentTheme) {
                    case THEME_BLUE:
                        lv.setSelector(R.drawable.blue_deckpicker_list_selector);
                        lv.setDividerHeight(0);
                        break;
                    case THEME_FLAT:
                        lv.setSelector(R.drawable.blue_deckpicker_list_selector);
                        lv.setDividerHeight(0);
                        break;
                    case THEME_WHITE:
                        lv.setSelector(R.drawable.white_deckpicker_list_selector);
                        AnkiDroidApp.getCompat().setOverScrollModeNever(lv);
                        lv.setVerticalScrollBarEnabled(false);
                        lv.setFadingEdgeLength(15);
                        lv.setDividerHeight(0);
                        lv.setBackgroundResource(R.drawable.white_deckpicker_lv_background);
                        view.setBackgroundResource(mWallpaper);
                        // lv.setDivider(mContext.getResources().getDrawable(R.drawable.white_listdivider));
                        // setMargins(view, LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 4f, 4f, 4f, 4f);
                        break;
                    default:
                        break;
                }
                break;

            case CALLER_CARDBROWSER:
                ListView lv2 = (ListView) view.findViewById(R.id.card_browser_list);
                switch (mCurrentTheme) {
                    case THEME_BLUE:
                        lv2.setSelector(R.drawable.blue_cardbrowser_list_selector);
                        lv2.setDividerHeight(0);
                        break;
                    case THEME_FLAT:
                        lv2.setSelector(R.drawable.blue_cardbrowser_list_selector);
                        lv2.setDividerHeight(0);
                        break;
                    case THEME_WHITE:
                        lv2.setBackgroundResource(R.drawable.white_textview);
                        lv2.setSelector(R.drawable.white_deckpicker_list_selector);
                        AnkiDroidApp.getCompat().setOverScrollModeNever(lv2);
                        lv2.setFadingEdgeLength(15);
                        lv2.setDividerHeight(0);
                        lv2.setSelector(R.drawable.white_deckpicker_list_selector);
                        lv2.setDivider(mContext.getResources().getDrawable(R.drawable.white_listdivider));
                        setFont(view);
                        setWallpaper(view);
                        break;
                    default:
                        break;
                }
                break;

            case CALLER_CARDEDITOR_INTENTDIALOG:
                ListView lv3 = (ListView) view;
                switch (mCurrentTheme) {
                    case THEME_BLUE:
                        lv3.setSelector(R.drawable.blue_cardbrowser_list_selector);
                        lv3.setDividerHeight(0);
                        break;
                    case THEME_FLAT:
                        lv3.setSelector(R.drawable.blue_cardbrowser_list_selector);
                        lv3.setDividerHeight(0);
                        break;
                    case THEME_WHITE:
                        lv3.setSelector(R.drawable.blue_cardbrowser_list_selector);
                        lv3.setDividerHeight(0);
                        break;
                    default:
                        break;
                }
                break;

            case CALLER_DECKPICKER_DECK:
                // if (view.getId() == R.id.deckpicker_bar_mat) {
                // // view.setBackgroundResource(mProgressbarsFrameColor);
                // } else if (view.getId() == R.id.deckpicker_bar_all) {
                // // view.setBackgroundResource(mProgressbarsDeckpickerYoungColor);
                // } else
                if (view.getId() == R.id.deckpicker_deck) {
                    view.setBackgroundResource(mDeckpickerItemBorder);
                }
                break;

            case CALLER_REVIEWER:
                ((View) view.findViewById(R.id.main_layout)).setBackgroundResource(mReviewerBackground);
                ((View) view.findViewById(R.id.flashcard_border)).setBackgroundResource(mFlashcardBorder);
                switch (mCurrentTheme) {
                    case THEME_ANDROID_DARK:
                    case THEME_ANDROID_LIGHT:
                        ((View) view.findViewById(R.id.flashcard_frame)).setBackgroundResource(AnkiDroidApp
                                .getSharedPrefs(mContext).getBoolean("invertedColors", false) ? R.color.black
                                : R.color.white);
                        break;
                    case THEME_BLUE:
                        ((View) view.findViewById(R.id.flashcard_frame))
                                .setBackgroundResource(AnkiDroidApp.getSharedPrefs(mContext).getBoolean(
                                        "invertedColors", false) ? R.color.reviewer_night_card_background
                                        : R.color.white);
                        break;
                    case THEME_FLAT:
                        ((View) view.findViewById(R.id.flashcard_frame))
                                .setBackgroundResource(AnkiDroidApp.getSharedPrefs(mContext).getBoolean(
                                        "invertedColors", false) ? R.color.reviewer_night_card_background
                                        : R.color.white);
                        break;
                    case THEME_WHITE:
                        ((View) view.findViewById(R.id.flashcard_frame)).setBackgroundResource(AnkiDroidApp
                                .getSharedPrefs(mContext).getBoolean("invertedColors", false) ? R.color.black
                                : R.color.white);

                        setMargins(view.findViewById(R.id.main_layout), LayoutParams.FILL_PARENT,
                                LayoutParams.FILL_PARENT, 4f, 0, 4f, 4f);

                        // ((View)view.findViewById(R.id.nextTime1)).setBackgroundResource(R.drawable.white_next_time_separator);
                        // ((View)view.findViewById(R.id.nextTime2)).setBackgroundResource(R.drawable.white_next_time_separator);
                        // ((View)view.findViewById(R.id.nextTime3)).setBackgroundResource(R.drawable.white_next_time_separator);
                        // ((View)view.findViewById(R.id.nextTime4)).setBackgroundResource(R.drawable.white_next_time_separator);
                        break;
                }
                ((View) view.findViewById(R.id.session_progress)).setBackgroundResource(mReviewerProgressbar);
                ((View) view.findViewById(R.id.daily_bar)).setBackgroundResource(mReviewerProgressbar);
                break;

            case CALLER_FEEDBACK:
                ((TextView) view).setTextColor(mProgressbarsFrameColor);
                break;

            case CALLER_CARD_EDITOR:
                view.findViewById(R.id.CardEditorEditFieldsLayout).setBackgroundResource(mTextViewStyle);
                // int padding = (int) (4 * mContext.getResources().getDisplayMetrics().density);
                // view.findViewById(R.id.CardEditorScroll).setPadding(padding, padding, padding, padding);
                break;

            case CALLER_DOWNLOAD_DECK:
                view.setBackgroundResource(mDeckpickerItemBorder);
                break;
        }
    }


    public static void loadTheme() {
        // SharedPreferences preferences = PrefSettings.getSharedPrefs(mContext);
        // mCurrentTheme = Integer.parseInt(preferences.getString("theme", "3"));

        // set theme always to "white" until theming is properly reimplemented
        mCurrentTheme = 3;

        switch (mCurrentTheme) {
            case THEME_ANDROID_DARK:
                mDialogBackgroundColor = R.color.card_browser_background;
                mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_default;
                mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_default;
                mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_default;
                mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_default;
                mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_dark;
                mReviewerBackground = 0;
                mFlashcardBorder = 0;
                mDeckpickerItemBorder = 0;
                mTitleStyle = 0;
                mTitleTextColor = mContext.getResources().getColor(R.color.white);
                mTextViewStyle = 0;
                mWallpaper = 0;
                mToastBackground = 0;
                mBackgroundDarkColor = 0;
                mReviewerProgressbar = 0;
                mCardbrowserItemBorder = new int[] { 0, R.color.card_browser_marked, R.color.card_browser_suspended,
                        R.color.card_browser_marked };
                mChartColors = new int[] { Color.WHITE, Color.BLACK };
                mPopupTopBright = R.drawable.popup_top_bright;
                mPopupTopMedium = R.drawable.popup_top_bright;
                mPopupTopDark = R.drawable.popup_top_dark;
                mPopupCenterDark = R.drawable.popup_center_dark;
                mPopupCenterBright = R.drawable.popup_center_bright;
                mPopupCenterMedium = R.drawable.popup_center_medium;
                mPopupBottomDark = R.drawable.popup_bottom_dark;
                mPopupBottomBright = R.drawable.popup_bottom_bright;
                mPopupBottomMedium = R.drawable.popup_bottom_medium;
                mPopupFullBright = R.drawable.popup_full_bright;
                mPopupFullDark = R.drawable.popup_full_dark;
                mPopupFullMedium = R.drawable.popup_full_bright;
                mDividerHorizontalBright = R.drawable.blue_divider_horizontal_bright;
                mBackgroundColor = R.color.white;
                mProgressDialogFontColor = mContext.getResources().getColor(R.color.white);
                mNightModeButton = R.drawable.btn_keyboard_key_fulltrans_normal;
                break;

            case THEME_ANDROID_LIGHT:
                mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_light;
                mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
                mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
                mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_light;
                mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_light;
                mReviewerBackground = 0;
                mFlashcardBorder = 0;
                mDeckpickerItemBorder = 0;
                mTitleStyle = 0;
                mTitleTextColor = mContext.getResources().getColor(R.color.black);
                mTextViewStyle = 0;
                mWallpaper = 0;
                mToastBackground = 0;
                mBackgroundDarkColor = 0;
                mDialogBackgroundColor = R.color.card_browser_background;
                mCardbrowserItemBorder = new int[] { 0, R.color.card_browser_marked, R.color.card_browser_suspended,
                        R.color.card_browser_marked };
                mReviewerProgressbar = mProgressbarsYoungColor;
                mChartColors = new int[] { Color.BLACK, Color.WHITE };
                mPopupTopDark = R.drawable.popup_top_dark;
                mPopupTopBright = R.drawable.popup_top_bright;
                mPopupTopMedium = R.drawable.popup_top_bright;
                mPopupCenterDark = R.drawable.popup_center_dark;
                mPopupCenterBright = R.drawable.popup_center_bright;
                mPopupCenterMedium = R.drawable.popup_center_medium;
                mPopupBottomDark = R.drawable.popup_bottom_dark;
                mPopupBottomBright = R.drawable.popup_bottom_bright;
                mPopupBottomMedium = R.drawable.popup_bottom_medium;
                mPopupFullBright = R.drawable.popup_full_bright;
                mPopupFullMedium = R.drawable.popup_full_bright;
                mPopupFullDark = R.drawable.popup_full_dark;
                mDividerHorizontalBright = R.drawable.blue_divider_horizontal_bright;
                mBackgroundColor = R.color.white;
                mProgressDialogFontColor = mContext.getResources().getColor(R.color.white);
                mNightModeButton = R.drawable.btn_keyboard_key_fulltrans_normal;
                break;

            case THEME_BLUE:
                mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_blue;
                mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
                mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
                mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_blue;
                mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_light;
                mReviewerBackground = R.color.reviewer_background;
                mFlashcardBorder = R.drawable.blue_bg_webview;
                mDeckpickerItemBorder = R.drawable.blue_bg_deckpicker;
                mTitleStyle = R.drawable.blue_title;
                mTitleTextColor = mContext.getResources().getColor(R.color.black);
                mTextViewStyle = R.drawable.blue_textview;
                mWallpaper = R.drawable.blue_background;
                mBackgroundColor = R.color.background_blue;
                mToastBackground = R.drawable.blue_toast_frame;
                mDialogBackgroundColor = R.color.background_dialog_blue;
                mBackgroundDarkColor = R.color.background_dark_blue;
                mReviewerProgressbar = R.color.reviewer_progressbar_session_blue;
                mCardbrowserItemBorder = new int[] { R.drawable.blue_bg_cardbrowser,
                        R.drawable.blue_bg_cardbrowser_marked, R.drawable.blue_bg_cardbrowser_suspended,
                        R.drawable.blue_bg_cardbrowser_marked_suspended };
                mChartColors = new int[] { Color.BLACK, Color.WHITE };
                mPopupTopDark = R.drawable.blue_popup_top_dark;
                mPopupTopBright = R.drawable.blue_popup_top_bright;
                mPopupTopMedium = R.drawable.blue_popup_top_medium;
                mPopupCenterDark = R.drawable.blue_popup_center_dark;
                mPopupCenterBright = R.drawable.blue_popup_center_bright;
                mPopupCenterMedium = R.drawable.blue_popup_center_medium;
                mPopupBottomDark = R.drawable.blue_popup_bottom_dark;
                mPopupBottomBright = R.drawable.blue_popup_bottom_bright;
                mPopupBottomMedium = R.drawable.blue_popup_bottom_medium;
                mPopupFullBright = R.drawable.blue_popup_full_bright;
                mPopupFullMedium = R.drawable.blue_popup_full_medium;
                mPopupFullDark = R.drawable.blue_popup_full_dark;
                mDividerHorizontalBright = R.drawable.blue_divider_horizontal_bright;
                mProgressDialogFontColor = mContext.getResources().getColor(R.color.white);
                mNightModeButton = R.drawable.blue_btn_night;
                break;

            case THEME_FLAT:
                mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_blue;
                mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
                mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
                mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_blue;
                mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_light;
                mReviewerBackground = R.color.reviewer_background;
                mFlashcardBorder = R.drawable.blue_bg_webview;
                mDeckpickerItemBorder = R.drawable.blue_bg_deckpicker;
                mTitleStyle = R.drawable.flat_title;
                mTitleTextColor = mContext.getResources().getColor(R.color.flat_title_color);
                mTextViewStyle = R.drawable.flat_textview;
                mWallpaper = R.drawable.flat_background;
                mBackgroundColor = R.color.background_blue;
                mToastBackground = R.drawable.blue_toast_frame;
                mDialogBackgroundColor = R.color.background_dialog_blue;
                mBackgroundDarkColor = R.color.background_dark_blue;
                mReviewerProgressbar = R.color.reviewer_progressbar_session_blue;
                mCardbrowserItemBorder = new int[] { R.drawable.blue_bg_cardbrowser,
                        R.drawable.blue_bg_cardbrowser_marked, R.drawable.blue_bg_cardbrowser_suspended,
                        R.drawable.blue_bg_cardbrowser_marked_suspended };
                mChartColors = new int[] { Color.BLACK, Color.WHITE };
                mPopupTopDark = R.drawable.blue_popup_top_dark;
                mPopupTopBright = R.drawable.blue_popup_top_bright;
                mPopupTopMedium = R.drawable.blue_popup_top_medium;
                mPopupCenterDark = R.drawable.blue_popup_center_dark;
                mPopupCenterBright = R.drawable.blue_popup_center_bright;
                mPopupCenterMedium = R.drawable.blue_popup_center_medium;
                mPopupBottomDark = R.drawable.blue_popup_bottom_dark;
                mPopupBottomBright = R.drawable.blue_popup_bottom_bright;
                mPopupBottomMedium = R.drawable.blue_popup_bottom_medium;
                mPopupFullBright = R.drawable.blue_popup_full_bright;
                mPopupFullMedium = R.drawable.blue_popup_full_medium;
                mPopupFullDark = R.drawable.blue_popup_full_dark;
                mDividerHorizontalBright = R.drawable.blue_divider_horizontal_bright;
                mLightFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Light.ttf");
                mRegularFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Regular.ttf");
                mBoldFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Bold.ttf");
                mProgressDialogFontColor = mContext.getResources().getColor(R.color.white);
                mNightModeButton = R.drawable.blue_btn_night;
                break;

            case THEME_WHITE:
                mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_blue;
                mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
                mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
                mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_blue;
                mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_light;
                mReviewerBackground = R.color.white_background;
                mFlashcardBorder = R.drawable.white_bg_webview;
                mTitleStyle = R.drawable.white_btn_default_normal;
                mTitleTextColor = mContext.getResources().getColor(R.color.black);
                mTextViewStyle = R.drawable.white_textview_padding;
                mWallpaper = R.drawable.white_wallpaper;
                mBackgroundColor = R.color.white_background;
                mToastBackground = R.drawable.white_toast_frame;
                mDialogBackgroundColor = R.color.white;
                mBackgroundDarkColor = R.color.background_dark_blue;
                mReviewerProgressbar = R.color.reviewer_progressbar_session_blue;
                mCardbrowserItemBorder = new int[] { R.drawable.white_bg_cardbrowser,
                        R.drawable.white_bg_cardbrowser_marked, R.drawable.white_bg_cardbrowser_suspended,
                        R.drawable.white_bg_cardbrowser_marked_suspended };
                mChartColors = new int[] { Color.BLACK, Color.WHITE };
                mPopupTopBright = R.drawable.white_popup_top_bright;
                mPopupTopMedium = R.drawable.white_popup_top_medium;
                mPopupTopDark = mPopupTopMedium;
                mPopupCenterDark = R.drawable.white_popup_center_bright;
                mPopupCenterBright = R.drawable.white_popup_center_bright;
                mPopupCenterMedium = R.drawable.white_popup_center_medium;
                mPopupBottomBright = R.drawable.white_popup_bottom_bright;
                mPopupBottomDark = mPopupBottomBright;
                mPopupBottomMedium = R.drawable.white_popup_bottom_medium;
                mPopupFullBright = R.drawable.white_popup_full_bright;
                mPopupFullMedium = R.drawable.white_popup_full_medium;
                mPopupFullDark = mPopupFullBright;
                mDividerHorizontalBright = R.drawable.white_dialog_divider;
                mLightFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Light.ttf");
                mRegularFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Regular.ttf");
                mBoldFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Bold.ttf");
                mProgressDialogFontColor = mContext.getResources().getColor(R.color.black);
                mNightModeButton = R.drawable.white_btn_night;
                break;
        }
    }


    public static void setLightFont(TextView view) {
        if (mLightFont != null) {
            view.setTypeface(mLightFont);
        }
    }


    public static void setRegularFont(TextView view) {
        if (mRegularFont != null) {
            view.setTypeface(mRegularFont);
        }
    }


    public static void setBoldFont(TextView view) {
        if (mBoldFont != null) {
            view.setTypeface(mBoldFont);
        }
    }


    public static void setFont(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if (tv.getTypeface() != null && tv.getTypeface().getStyle() == Typeface.BOLD) {
                        setBoldFont((TextView) child);
                    } else {
                        setRegularFont((TextView) child);
                    }
                }
                setFont(child);
            }
        }
    }


    public static String getReviewerFontName() {
        switch (mCurrentTheme) {
            case THEME_WHITE:
                return "OpenSans";
            default:
                return null;
        }
    }


    public static void setTextColor(View view, int color) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    tv.setTextColor(color);
                }
                setTextColor(child, color);
            }
        }
    }


    public static void setWallpaper(View view) {
        setWallpaper(view, false);
    }


    public static void setWallpaper(View view, boolean solid) {
        if (solid) {
            view.setBackgroundResource(mBackgroundDarkColor);
        } else {
            try {
                view.setBackgroundResource(mWallpaper);
            } catch (OutOfMemoryError e) {
                mWallpaper = mBackgroundColor;
                view.setBackgroundResource(mWallpaper);
                Log.e(AnkiDroidApp.TAG, "Themes: setWallpaper: OutOfMemoryError = " + e);
            }
        }
    }


    public static void setTextViewStyle(View view) {
        view.setBackgroundResource(mTextViewStyle);
    }


    public static void setTitleStyle(View view) {
        view.setBackgroundResource(mTitleStyle);
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextColor(mTitleTextColor);
            if (mCurrentTheme == THEME_FLAT) {
                tv.setMinLines(1);
                tv.setMaxLines(2);
                int height = (int) (tv.getLineHeight() / 2);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                        LayoutParams.WRAP_CONTENT);
                MarginLayoutParams mlp = (MarginLayoutParams) tv.getLayoutParams();
                height += mlp.bottomMargin;
                llp.setMargins(0, height, 0, height);
                tv.setLayoutParams(llp);
                setBoldFont(tv);
            }
        }
    }


    public static void setMargins(View view, int width, int height, float dipLeft, float dipTop, float dipRight,
            float dipBottom) {
        View parent = (View) view.getParent();
        parent.setBackgroundResource(mBackgroundColor);
        Class<?> c = view.getParent().getClass();
        float factor = mContext.getResources().getDisplayMetrics().density;
        if (c == LinearLayout.class) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(width, height);
            llp.setMargins((int) (dipLeft * factor), (int) (dipTop * factor), (int) (dipRight * factor),
                    (int) (dipBottom * factor));
            view.setLayoutParams(llp);
        } else if (c == FrameLayout.class) {
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(width, height);
            llp.setMargins((int) (dipLeft * factor), (int) (dipTop * factor), (int) (dipRight * factor),
                    (int) (dipBottom * factor));
            llp.gravity = Gravity.CENTER_HORIZONTAL;
            view.setLayoutParams(llp);
        } else if (c == RelativeLayout.class) {
            RelativeLayout.LayoutParams llp = new RelativeLayout.LayoutParams(width, height);
            llp.setMargins((int) (dipLeft * factor), (int) (dipTop * factor), (int) (dipRight * factor),
                    (int) (dipBottom * factor));
            view.setLayoutParams(llp);
        }
    }


    public static int getForegroundColor() {
        return mProgressbarsFrameColor;
    }


    public static int getBackgroundColor() {
        return mBackgroundColor;
    }


    public static int getDialogBackgroundColor() {
        return mDialogBackgroundColor;
    }


    public static int getTheme() {
        return mCurrentTheme;
    }


    public static int[] getCardBrowserBackground() {
        return mCardbrowserItemBorder;
    }


    public static void setTextViewBackground(View view) {
        view.setBackgroundResource(mTextViewStyle);
    }


    public static void showThemedToast(Context context, String text, boolean shortLength) {
        Toast result = Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
        try {
            if (mCurrentTheme >= THEME_BLUE) {
                TextView tv = new TextView(context);
                tv.setBackgroundResource(mToastBackground);
                tv.setTextColor(mProgressDialogFontColor);
                tv.setText(text);
                result.setView(tv);
            }
            result.show();
        } catch (OutOfMemoryError e) {
            Log.e(AnkiDroidApp.TAG, "showThemedToast - OutOfMemoryError occured: " + e);
            result.getView().setBackgroundResource(R.color.black);
            result.show();
        }
    }


    public static StyledDialog htmlOkDialog(Context context, String title, String text) {
        return htmlOkDialog(context, title, text, null, null);
    }


    public static StyledDialog htmlOkDialog(Context context, String title, String text, OnClickListener okListener,
            OnCancelListener cancelListener) {
        return htmlOkDialog(context, title, text, null, null, false);
    }


    public static StyledDialog htmlOkDialog(Context context, String title, String text, OnClickListener okListener,
            OnCancelListener cancelListener, boolean includeBody) {
        StyledDialog.Builder builder = new StyledDialog.Builder(context);
        builder.setTitle(title);
        WebView view = new WebView(context);
        view.setBackgroundColor(context.getResources().getColor(mDialogBackgroundColor));
        if (includeBody) {
            text = "<html><body text=\"#FFFFFF\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">" + text
                    + "</body></html>";
        }
        view.loadDataWithBaseURL("", text, "text/html", "UTF-8", "");
        builder.setView(view, true);
        builder.setPositiveButton(context.getResources().getString(R.string.ok), okListener);
        builder.setCancelable(true);
        builder.setOnCancelListener(cancelListener);
        return builder.create();
    }


    public static void setStyledProgressDialogDialogBackgrounds(View main) {
        View topPanel = ((View) main.findViewById(R.id.topPanel));
        View contentPanel = ((View) main.findViewById(R.id.contentPanel));
        if (topPanel.getVisibility() == View.VISIBLE) {
            try {
                topPanel.setBackgroundResource(mPopupTopDark);
                ((View) main.findViewById(R.id.titleDivider)).setBackgroundResource(mDividerHorizontalBright);
                contentPanel.setBackgroundResource(mPopupBottomBright);
            } catch (OutOfMemoryError e) {
                Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
                topPanel.setBackgroundResource(R.color.black);
                contentPanel.setBackgroundResource(R.color.white);
            }
        } else {
            try {
                contentPanel.setBackgroundResource(mPopupFullMedium);
            } catch (OutOfMemoryError e) {
                Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
                contentPanel.setBackgroundResource(R.color.white);
            }
        }
        ((TextView) main.findViewById(R.id.alertTitle)).setTextColor(mProgressDialogFontColor);
        ((TextView) main.findViewById(R.id.message)).setTextColor(mProgressDialogFontColor);
    }


    public static void setStyledDialogBackgrounds(View main) {
        int buttonCount = 0;
        for (int id : new int[] { R.id.button1, R.id.button2, R.id.button3 }) {
            if (main.findViewById(id).getVisibility() == View.VISIBLE) {
                buttonCount++;
            }
        }
        setStyledDialogBackgrounds(main, buttonCount);
    }


    public static void setStyledDialogBackgrounds(View main, int buttonNumbers) {
        setStyledDialogBackgrounds(main, buttonNumbers, false);
    }


    public static void setStyledDialogBackgrounds(View main, int buttonNumbers, boolean brightCustomPanelBackground) {
        setFont(main);
        if (mCurrentTheme == THEME_WHITE) {
            setTextColor(main, mContext.getResources().getColor(R.color.black));
        }
        // order of styled dialog elements:
        // 1. top panel (title)
        // 2. content panel
        // 3. listview panel
        // 4. custom view panel
        // 5. button panel
        View topPanel = ((View) main.findViewById(R.id.topPanel));
        boolean[] visibility = new boolean[5];

        if (topPanel.getVisibility() == View.VISIBLE) {
            try {
                topPanel.setBackgroundResource(mPopupTopDark);
                ((View) main.findViewById(R.id.titleDivider)).setBackgroundResource(mDividerHorizontalBright);
            } catch (OutOfMemoryError e) {
                Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
                topPanel.setBackgroundResource(R.color.black);
            }
            visibility[0] = true;
        }
        View contentPanel = ((View) main.findViewById(R.id.contentPanel));
        if (contentPanel.getVisibility() == View.VISIBLE) {
            visibility[1] = true;
        }
        View listViewPanel = ((View) main.findViewById(R.id.listViewPanel));
        if (listViewPanel.getVisibility() == View.VISIBLE) {
            visibility[2] = true;
        }
        View customPanel = ((View) main.findViewById(R.id.customPanel));
        if (customPanel.getVisibility() == View.VISIBLE) {
            visibility[3] = true;
        }
        if (buttonNumbers > 0) {
            LinearLayout buttonPanel = (LinearLayout) main.findViewById(R.id.buttonPanel);
            try {
                buttonPanel.setBackgroundResource(mPopupBottomMedium);
            } catch (OutOfMemoryError e) {
                Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
                buttonPanel.setBackgroundResource(R.color.white);
            }
            if (buttonNumbers > 1) {
                main.findViewById(R.id.rightSpacer).setVisibility(View.GONE);
                main.findViewById(R.id.leftSpacer).setVisibility(View.GONE);
            }
            visibility[4] = true;
        }

        int first = -1;
        int last = -1;
        for (int i = 0; i < 5; i++) {
            if (first == -1 && visibility[i]) {
                first = i;
            }
            if (visibility[i]) {
                last = i;
            }
        }

        int res = mPopupCenterDark;
        if (first == 1) {
            res = mPopupTopDark;
        }
        if (last == 1) {
            res = mPopupBottomDark;
            if (first == 1) {
                res = mPopupFullDark;
            }
        }
        try {
            contentPanel.setBackgroundResource(res);
        } catch (OutOfMemoryError e) {
            Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
            contentPanel.setBackgroundResource(R.color.black);
        }

        res = mPopupCenterBright;
        if (first == 2) {
            res = mPopupTopBright;
        }
        if (last == 2) {
            res = mPopupBottomBright;
            if (first == 2) {
                res = mPopupFullBright;
            }
        }
        try {
            listViewPanel.setBackgroundResource(res);
        } catch (OutOfMemoryError e) {
            Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
            listViewPanel.setBackgroundResource(R.color.white);
        }

        res = brightCustomPanelBackground ? mPopupCenterMedium : mPopupCenterDark;
        if (first == 3) {
            res = brightCustomPanelBackground ? mPopupTopMedium : mPopupTopDark;
            ;
        }
        if (last == 3) {
            res = brightCustomPanelBackground ? mPopupBottomMedium : mPopupBottomDark;
            ;
            if (first == 3) {
                res = brightCustomPanelBackground ? mPopupFullMedium : mPopupFullDark;
                ;
            }
        }
        try {
            customPanel.setBackgroundResource(res);
        } catch (OutOfMemoryError e) {
            Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
            customPanel.setBackgroundResource(brightCustomPanelBackground ? R.color.white : R.color.black);
        }

        // set divider
        if (visibility[3] && brightCustomPanelBackground) {
            ((View) main.findViewById(R.id.bottomDivider)).setBackgroundResource(mDividerHorizontalBright);
            ((View) main.findViewById(R.id.bottomDivider)).setVisibility(View.VISIBLE);
        } else if (visibility[4]) {
            ((View) main.findViewById(R.id.bottomButtonDivider)).setBackgroundResource(mDividerHorizontalBright);
            ((View) main.findViewById(R.id.bottomButtonDivider)).setVisibility(View.VISIBLE);
        }
    }


    public static int[] getChartColors() {
        return mChartColors;
    }


    public static int getNightModeCardBackground(Context context) {
        switch (mCurrentTheme) {
            case THEME_BLUE:
                return context.getResources().getColor(R.color.reviewer_night_card_background);
            case THEME_FLAT:
                return context.getResources().getColor(R.color.reviewer_night_card_background);
            case THEME_WHITE:
            default:
                return context.getResources().getColor(R.color.black);
        }
    }


    public static int[] setNightMode(Context context, View view, boolean nightMode) {
        Resources res = context.getResources();
        View flipCard = view.findViewById(R.id.flashcard_layout_flip);
        View ease1 = view.findViewById(R.id.flashcard_layout_ease1);
        View ease2 = view.findViewById(R.id.flashcard_layout_ease2);
        View ease3 = view.findViewById(R.id.flashcard_layout_ease3);
        View ease4 = view.findViewById(R.id.flashcard_layout_ease4);
        View border = view.findViewById(R.id.flashcard_border);
        View mAnswerField = view.findViewById(R.id.answer_field);
        final Drawable[] defaultButtons = new Drawable[] { flipCard.getBackground(), ease1.getBackground(),
                ease2.getBackground(), ease3.getBackground(), ease4.getBackground() };

        int foregroundColor;
        int nextTimeRecommendedColor;

        if (nightMode) {
            flipCard.setBackgroundResource(mNightModeButton);
            ease1.setBackgroundResource(mNightModeButton);
            ease2.setBackgroundResource(mNightModeButton);
            ease3.setBackgroundResource(mNightModeButton);
            ease4.setBackgroundResource(mNightModeButton);
            mAnswerField.setBackgroundResource(mNightModeButton);

            foregroundColor = Color.WHITE;
            nextTimeRecommendedColor = res.getColor(R.color.next_time_recommended_color_inv);

            switch (mCurrentTheme) {
                case THEME_BLUE:
                    border.setBackgroundResource(R.drawable.blue_bg_webview_night);
                    view.setBackgroundColor(res.getColor(R.color.background_dark_blue));
                    break;
                case THEME_WHITE:
                    border.setBackgroundResource(R.drawable.white_bg_webview_night);
                    view.setBackgroundColor(res.getColor(R.color.white_background_night));
                    ((View) view.getParent()).setBackgroundColor(res.getColor(R.color.white_background_night));
                    break;
                case THEME_FLAT:
                default:
                    view.setBackgroundColor(res.getColor(R.color.black));
                    break;
            }
        } else {
            foregroundColor = Color.BLACK;
            nextTimeRecommendedColor = res.getColor(R.color.next_time_recommended_color);
            flipCard.setBackgroundDrawable(defaultButtons[0]);
            ease1.setBackgroundDrawable(defaultButtons[1]);
            ease2.setBackgroundDrawable(defaultButtons[2]);
            ease3.setBackgroundDrawable(defaultButtons[3]);
            ease4.setBackgroundDrawable(defaultButtons[4]);
            border.setBackgroundResource(mFlashcardBorder);
        }

        return new int[] { foregroundColor, nextTimeRecommendedColor };
    }
}
