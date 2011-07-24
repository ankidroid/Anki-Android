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

package com.ichi2.anki;

import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Themes {

	private final static int THEME_DEFAULT = 0;
	private final static int THEME_ANDROID_LIGHT = 1;
	private final static int THEME_BLUE= 2;

	public final static int CALLER_STUDYOPTIONS = 1;
	public final static int CALLER_DECKPICKER = 2;
	public final static int CALLER_DECKPICKER_DECK = 3;
	public final static int CALLER_REVIEWER= 4;
	public final static int CALLER_FEEDBACK= 5;

	private static int mCurrentTheme = -1;
	private static int mProgressbarsBackgroundColor;
	private static int mProgressbarsFrameColor;
	private static int mProgressbarsMatureColor;
	private static int mProgressbarsYoungColor;
	private static int mProgressbarsDeckpickerYoungColor;
	private static int mDeckpickerBackground = 0;
	private static int mReviewerBackground = 0;
	private static int mFlashcardBorder = 0;
	private static int mDeckpickerItemBorder = 0;
	private static int mTitleStyle = 0;
	private static int mTextViewStyle= 0;
	private static int mWallpaper = 0;
	private static int mBackgroundColor = 0;
	private static int mToastBackground = 0;
	

	public static void applyTheme(Context context) {
		if (mCurrentTheme == -1) {
			SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
			mCurrentTheme = Integer.parseInt(preferences.getString("theme", "0"));
			switch (mCurrentTheme) {
			case THEME_DEFAULT:
				mDeckpickerBackground = R.color.card_browser_background;
				break;
			case THEME_ANDROID_LIGHT:
				mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_light;
				mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
				mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
				mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_light;
				mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_light;
				break;				
			case THEME_BLUE:
				mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_blue;
				mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
				mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
				mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_blue;
				mProgressbarsDeckpickerYoungColor = R.color.deckpicker_progressbar_young_light;
				mDeckpickerBackground = R.color.deckpicker_background;
				mReviewerBackground = R.color.reviewer_background;
				mFlashcardBorder = R.drawable.blue_bg_webview;
				mDeckpickerItemBorder = R.drawable.blue_bg_deckpicker;
				mTitleStyle = R.drawable.blue_title;
				mTextViewStyle = R.drawable.blue_textview;
				mWallpaper = R.drawable.blue_background;
				mBackgroundColor = R.color.background_blue;
				mToastBackground = R.drawable.blue_toast_frame;
				break;
			}
		}
		switch (mCurrentTheme) {
		case THEME_DEFAULT:
			break;
		case THEME_ANDROID_LIGHT:
			context.setTheme(android.R.style.Theme_Light);
			break;
		case THEME_BLUE:
			context.setTheme(R.style.Theme_Blue);
			break;
		}
	}


	public static void setContentStyle(View view, int caller) {
		if (mCurrentTheme == THEME_DEFAULT) {
			return;
		}
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
			break;
		case CALLER_DECKPICKER:
			view.setBackgroundResource(mDeckpickerBackground);		
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
			break;
		case CALLER_FEEDBACK:
			((TextView)view).setTextColor(mProgressbarsFrameColor);
			break;
		}
	}


	public static void resetTheme(){
		mCurrentTheme = -1;
		mProgressbarsBackgroundColor = 0;
		mProgressbarsFrameColor = 0;
		mProgressbarsMatureColor = 0;
		mProgressbarsYoungColor = 0;
		mProgressbarsDeckpickerYoungColor = 0;
		mDeckpickerBackground = 0;
		mReviewerBackground = 0;
		mFlashcardBorder = 0;
		mDeckpickerItemBorder = 0;
		mTitleStyle = 0;
		mTextViewStyle = 0;
		mWallpaper = 0;
		mBackgroundColor = 0;
		mToastBackground = 0;
	}


	public static boolean changeFlashcardBorder() {
		switch (mCurrentTheme) {
		case THEME_BLUE:
			return false;
		}
		return true;
	}


	public static void setWallpaper(View view) {
		view.setBackgroundResource(mWallpaper);
	}


	public static void setTextViewStyle(View view) {
		view.setBackgroundResource(mTextViewStyle);
	}


	public static void setTitleStyle(View view) {
		view.setBackgroundResource(mTitleStyle);
	}


	public static int getForegroundColor() {
		return mProgressbarsFrameColor;
	}


	public static int getBackgroundColor() {
		return mBackgroundColor;
	}


	public static int getTheme() {
		return mCurrentTheme;
	}


	public static void showThemedToast(Context context, String text, boolean shortLength) {
		Toast result = Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
		if (mCurrentTheme >= THEME_BLUE) {
			result.getView().setBackgroundResource(mToastBackground);
		}
        result.show();
	}
}
