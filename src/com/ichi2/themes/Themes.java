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

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewParent;
import android.webkit.WebView;
import android.widget.Button;
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
	public final static int THEME_FLAT = 3;
	public final static int THEME_WHITE = 4;
	public final static int THEME_NO_THEME = 100;

	public final static int CALLER_STUDYOPTIONS = 1;
	public final static int CALLER_DECKPICKER_DECK = 3;
	public final static int CALLER_REVIEWER= 4;
	public final static int CALLER_FEEDBACK= 5;
	public final static int CALLER_DOWNLOAD_DECK= 6;
	public final static int CALLER_DECKPICKER = 7;
	public final static int CALLER_CARDBROWSER = 8;
	public final static int CALLER_CARDEDITOR_INTENTDIALOG = 9;

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
	private static int mTextViewStyle= 0;
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
			context.setTheme(R.style.Theme_Black);
			Log.i(AnkiDroidApp.TAG, "Set theme: dark");
			break;
		case THEME_ANDROID_LIGHT:
			context.setTheme(R.style.Theme_Light);
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
			((View) view.findViewById(R.id.studyoptions_progressbar1_border)).setBackgroundResource(mProgressbarsFrameColor);
			((View) view.findViewById(R.id.studyoptions_progressbar2_border)).setBackgroundResource(mProgressbarsFrameColor);
			((View) view.findViewById(R.id.studyoptions_global_limit_bars)).setBackgroundResource(mProgressbarsFrameColor);
			((View) view.findViewById(R.id.studyoptions_progressbar4_border)).setBackgroundResource(mProgressbarsFrameColor);

			((View) view.findViewById(R.id.studyoptions_bars_max)).setBackgroundResource(mProgressbarsBackgroundColor);
			((View) view.findViewById(R.id.studyoptions_progressbar2_content)).setBackgroundResource(mProgressbarsBackgroundColor);
			((View) view.findViewById(R.id.studyoptions_global_limit_bars_content)).setBackgroundResource(mProgressbarsBackgroundColor);
			((View) view.findViewById(R.id.studyoptions_progressbar4_content)).setBackgroundResource(mProgressbarsBackgroundColor);

			((View) view.findViewById(R.id.studyoptions_global_mat_limit_bar)).setBackgroundResource(mProgressbarsMatureColor);
			((View) view.findViewById(R.id.studyoptions_global_mat_bar)).setBackgroundResource(mProgressbarsMatureColor);

			((View) view.findViewById(R.id.studyoptions_global_limit_bar)).setBackgroundResource(mProgressbarsYoungColor);
			((View) view.findViewById(R.id.studyoptions_global_bar)).setBackgroundResource(mProgressbarsYoungColor);

			if (mCurrentTheme == THEME_WHITE) {
				((View) view.findViewById(R.id.studyoptions_deckinformation)).setBackgroundResource(mTextViewStyle);
				((View) view.findViewById(R.id.studyoptions_statistic_field)).setBackgroundResource(R.color.transparent);
				((View) view.findViewById(R.id.studyoptions_deckinformation)).setBackgroundResource(mTextViewStyle);
		        setMargins(view.findViewById(R.id.studyoptions_mainframe), LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 4f, 0, 4f, 4f);				
			} else {
				((View) view.findViewById(R.id.studyoptions_statistic_field)).setBackgroundResource(mTextViewStyle);				
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
				lv.setBackgroundResource(R.drawable.white_textview);
				lv.setSelector(R.drawable.white_deckpicker_list_selector);
				lv.setDivider(mContext.getResources().getDrawable(R.drawable.white_listdivider));
//				setFont(view);
		        setMargins(view, LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 4f, 4f, 4f, 4f);
//		        for (int i = 0; i < lv.getChildCount(); i++) {
//		        	ViewGroup child = (ViewGroup) lv.getChildAt(i);
//		        	for (int j = 0; j < child.getChildCount(); j++) {
//		        		View cc = child.getChildAt(j);
//		        		if (cc.getId() == R.id.DeckPickerName) {
//		        			setBoldFont((TextView) cc);
//		        		}
//		        	}
//		        }
				//lv.setDividerHeight(0);
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
				lv2.setSelector(R.drawable.blue_cardbrowser_list_selector);
				lv2.setDividerHeight(0);
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
			if (view.getId() == R.id.DeckPickerCompletionMat) {
				view.setBackgroundResource(mProgressbarsFrameColor);
			} else if (view.getId() == R.id.DeckPickerCompletionAll) {
				view.setBackgroundResource(mProgressbarsDeckpickerYoungColor);
			} else if (view.getId() == R.id.deckpicker_deck) {
				view.setBackgroundResource(mDeckpickerItemBorder);
			}
			break;
		case CALLER_REVIEWER:
		        ((View)view.findViewById(R.id.main_layout)).setBackgroundResource(mReviewerBackground);
		        ((View)view.findViewById(R.id.flashcard_border)).setBackgroundResource(mFlashcardBorder);
			switch (mCurrentTheme) {
			case THEME_ANDROID_DARK:
			case THEME_ANDROID_LIGHT:
			        ((View)view.findViewById(R.id.flashcard_frame)).setBackgroundResource(PrefSettings.getSharedPrefs(mContext).getBoolean("invertedColors", false) ? R.color.black : R.color.white);
				break;
			case THEME_BLUE:
			        ((View)view.findViewById(R.id.flashcard_frame)).setBackgroundResource(PrefSettings.getSharedPrefs(mContext).getBoolean("invertedColors", false) ? R.color.reviewer_night_card_background : R.color.white);
				break;
			case THEME_FLAT:
			        ((View)view.findViewById(R.id.flashcard_frame)).setBackgroundResource(PrefSettings.getSharedPrefs(mContext).getBoolean("invertedColors", false) ? R.color.reviewer_night_card_background : R.color.white);
				break;
			case THEME_WHITE:
			        ((View)view.findViewById(R.id.flashcard_frame)).setBackgroundResource(PrefSettings.getSharedPrefs(mContext).getBoolean("invertedColors", false) ? R.color.reviewer_night_card_background : R.color.white);
			        setMargins(view.findViewById(R.id.main_layout), LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 4f, 0, 4f, 4f);
			        
			        //			        ((View)view.findViewById(R.id.nextTime1)).setBackgroundResource(R.drawable.white_next_time_separator);
//			        ((View)view.findViewById(R.id.nextTime2)).setBackgroundResource(R.drawable.white_next_time_separator);
//			        ((View)view.findViewById(R.id.nextTime3)).setBackgroundResource(R.drawable.white_next_time_separator);
//			        ((View)view.findViewById(R.id.nextTime4)).setBackgroundResource(R.drawable.white_next_time_separator);
				break;
			}
	        ((View)view.findViewById(R.id.session_progress)).setBackgroundResource(mReviewerProgressbar);
			break;

		case CALLER_FEEDBACK:
			((TextView)view).setTextColor(mProgressbarsFrameColor);
			break;

		case CALLER_DOWNLOAD_DECK:
			view.setBackgroundResource(mDeckpickerItemBorder);
			break;
		}
	}


	public static void loadTheme(){
			SharedPreferences preferences = PrefSettings.getSharedPrefs(mContext);
			mCurrentTheme = Integer.parseInt(preferences.getString("theme", "2"));
			switch (mCurrentTheme) {
			case THEME_ANDROID_DARK:
				mDialogBackgroundColor = R.color.card_browser_background;
				mProgressbarsBackgroundColor = 0;
				mProgressbarsFrameColor = 0;
				mProgressbarsMatureColor = 0;
				mProgressbarsYoungColor = 0;
				mProgressbarsDeckpickerYoungColor = 0;
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
				mCardbrowserItemBorder = new int[] {0, R.color.card_browser_marked, R.color.card_browser_suspended, R.color.card_browser_marked};
				mChartColors = new int[] {Color.WHITE, Color.BLACK};
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
				mCardbrowserItemBorder = new int[] {0, R.color.card_browser_marked, R.color.card_browser_suspended, R.color.card_browser_marked};
				mReviewerProgressbar = mProgressbarsYoungColor;
				mChartColors = new int[] {Color.BLACK, Color.WHITE};
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
				mCardbrowserItemBorder = new int[] {R.drawable.blue_bg_cardbrowser, R.drawable.blue_bg_cardbrowser_marked, R.drawable.blue_bg_cardbrowser_suspended, R.drawable.blue_bg_cardbrowser_marked_suspended};
				mChartColors = new int[] {Color.BLACK, Color.WHITE};
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
				mCardbrowserItemBorder = new int[] {R.drawable.blue_bg_cardbrowser, R.drawable.blue_bg_cardbrowser_marked, R.drawable.blue_bg_cardbrowser_suspended, R.drawable.blue_bg_cardbrowser_marked_suspended};
				mChartColors = new int[] {Color.BLACK, Color.WHITE};
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
				break;

			case THEME_WHITE:
			mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_blue;
			mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
			mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
			mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_blue;
			mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_light;
				mReviewerBackground = R.color.white_background;
				mFlashcardBorder = R.drawable.white_bg_webview;
				mDeckpickerItemBorder = R.drawable.white_bg_deckpicker;
			mTitleStyle = R.drawable.flat_title;
			mTitleTextColor = mContext.getResources().getColor(R.color.flat_title_color);
				mTextViewStyle = R.drawable.white_textview;
				mWallpaper = R.color.white_background;
				mBackgroundColor = R.color.white_background;
			mToastBackground = R.drawable.blue_toast_frame;
			mDialogBackgroundColor = R.color.background_dialog_blue;
			mBackgroundDarkColor = R.color.background_dark_blue;
			mReviewerProgressbar = R.color.reviewer_progressbar_session_blue;
			mCardbrowserItemBorder = new int[] {R.drawable.blue_bg_cardbrowser, R.drawable.blue_bg_cardbrowser_marked, R.drawable.blue_bg_cardbrowser_suspended, R.drawable.blue_bg_cardbrowser_marked_suspended};
			mChartColors = new int[] {Color.BLACK, Color.WHITE};
				mPopupTopDark = mPopupTopBright;
				mPopupTopBright = R.drawable.white_popup_top_bright;
				mPopupTopMedium = R.drawable.white_popup_top_medium;
				mPopupCenterDark = R.drawable.white_popup_center_bright;
				mPopupCenterBright = R.drawable.white_popup_center_bright;
				mPopupCenterMedium = R.drawable.white_popup_center_medium;
				mPopupBottomDark = mPopupBottomBright;
				mPopupBottomBright = R.drawable.white_popup_bottom_bright;
				mPopupBottomMedium = R.drawable.white_popup_bottom_medium;
				mPopupFullBright = R.drawable.white_popup_full_bright;
				mPopupFullMedium = R.drawable.white_popup_full_medium;
				mPopupFullDark = mPopupFullBright;
				mDividerHorizontalBright = R.drawable.white_dialog_divider;
				mLightFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Light.ttf");
				mRegularFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Regular.ttf");
				mBoldFont = Typeface.createFromAsset(mContext.getAssets(), "fonts/OpenSans-Bold.ttf");
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
				LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
				MarginLayoutParams mlp = (MarginLayoutParams) tv.getLayoutParams();
				height += mlp.bottomMargin;
				llp.setMargins(0, height, 0, height);
				tv.setLayoutParams(llp);
				setBoldFont(tv);
			}
		}
	}


	public static void setMargins(View view, int width, int height, float dipLeft, float dipTop, float dipRight, float dipBottom) {
		View parent = (View) view.getParent();
		parent.setBackgroundResource(mBackgroundColor);
		Class c = view.getParent().getClass();
    	float factor = mContext.getResources().getDisplayMetrics().density;
		if (c == LinearLayout.class) {
			parent.setPadding((int)(dipLeft * factor), (int)(dipTop * factor), (int)(dipRight * factor), (int)(dipBottom * factor));
		} else if (c == FrameLayout.class) {
			parent.setPadding((int)(dipLeft * factor), (int)(dipTop * factor), (int)(dipRight * factor), (int)(dipBottom * factor));
		} else if (c == RelativeLayout.class) {
			parent.setPadding((int)(dipLeft * factor), (int)(dipTop * factor), (int)(dipRight * factor), (int)(dipBottom * factor));
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


	public static void showThemedToast(Context context, String text, boolean shortLength) {
		Toast result = Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
		try {
			if (mCurrentTheme >= THEME_BLUE) {
				result.getView().setBackgroundResource(mToastBackground);
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
	public static StyledDialog htmlOkDialog(Context context, String title, String text, OnClickListener okListener, OnCancelListener cancelListener) {
		return htmlOkDialog(context, title, text, null, null, false);		
	}
	public static StyledDialog htmlOkDialog(Context context, String title, String text, OnClickListener okListener, OnCancelListener cancelListener, boolean includeBody) {
		StyledDialog.Builder builder = new StyledDialog.Builder(context);
        builder.setTitle(title);
        WebView view = new WebView(context);
        view.setBackgroundColor(context.getResources().getColor(mDialogBackgroundColor));
        if (includeBody) {
        	text = "<html><body text=\"#FFFFFF\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">" + text + "</body></html>";
        }
        view.loadDataWithBaseURL("", text, "text/html", "UTF-8", "");
        builder.setView(view, true);
        builder.setPositiveButton(context.getResources().getString(R.string.ok), okListener);
        builder.setCancelable(true);
        builder.setOnCancelListener(cancelListener);
        return builder.create();
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
			res = brightCustomPanelBackground ? mPopupTopMedium : mPopupTopDark;;
		}
		if (last == 3) {
			res = brightCustomPanelBackground ? mPopupBottomMedium : mPopupBottomDark;;
			if (first == 3) {
				res = brightCustomPanelBackground ? mPopupFullMedium : mPopupFullDark;;
			}
		}
		try {
			customPanel.setBackgroundResource(res);
		} catch (OutOfMemoryError e) {
			Log.e(AnkiDroidApp.TAG, "setStyledDialogBackgrounds - OutOfMemoryError occured: " + e);
			customPanel.setBackgroundResource(brightCustomPanelBackground ? R.color.white : R.color.black);
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
			return context.getResources().getColor(R.color.reviewer_night_card_background);
		default:
			return Color.BLACK;
		}
	}
}
