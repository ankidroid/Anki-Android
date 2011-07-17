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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Themes {

	private final static int THEME_DEFAULT = 0;
	private final static int THEME_ANDROID_LIGHT = 1;
	private final static int THEME_BLUE= 2;

	public final static int CALLER_STUDYOPTIONS = 1;
	public final static int CALLER_DECKPICKER = 2;
	public final static int CALLER_DECKPICKER_DECK = 3;
	public final static int CALLER_REVIEWER= 4;

	private static int mCurrentTheme = -1;
	private static int mProgressbarsBackgroundColor;
	private static int mProgressbarsFrameColor;
	private static int mProgressbarsMatureColor;
	private static int mProgressbarsYoungColor;
	private static int mProgressbarsDeckpickerYoungColor;
	private static int mButtonStyle = 0;
	private static int[] mListColors = { 0, 0};
	private static int mDeckpickerBackground = 0;;

	public static void applyTheme(Context context) {
		if (mCurrentTheme == -1) {
			SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
			mCurrentTheme = Integer.parseInt(preferences.getString("theme", "0"));
			switch (mCurrentTheme) {
			case THEME_ANDROID_LIGHT:
				mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_light;
				mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
				mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
				mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_light;
				break;				
			case THEME_BLUE:
				mProgressbarsBackgroundColor = R.color.studyoptions_progressbar_background_blue;
				mProgressbarsFrameColor = R.color.studyoptions_progressbar_frame_light;
				mProgressbarsMatureColor = R.color.studyoptions_progressbar_mature_light;
				mProgressbarsYoungColor = R.color.studyoptions_progressbar_young_blue;
				mButtonStyle = R.drawable.blue_btn_default;
				mListColors = new int[]{R.color.deckpicker_listview_color1_blue, R.color.deckpicker_listview_color2_blue };
				mDeckpickerBackground = R.color.deckpicker_background;				
				break;
			}
		}
		switch (mCurrentTheme) {
		case THEME_DEFAULT:
			break;
		case THEME_ANDROID_LIGHT:
		case THEME_BLUE:
			context.setTheme(android.R.style.Theme_Light);
			break;
		}
	}


	public static void changeContentColors(View view, int caller) {
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
			switch (mCurrentTheme) {
			case THEME_BLUE:
		        view.setBackgroundResource(R.drawable.blue_wallpaper);
				((View)view.findViewById(R.id.studyoptions_deck_name)).setBackgroundResource(R.drawable.blue_title);
				((View)view.findViewById(R.id.studyoptions_bottom)).setBackgroundResource(R.drawable.blue_title);
				((View)view.findViewById(R.id.studyoptions_statistic_field)).setBackgroundResource(R.drawable.blue_textview);
		        ((View)view.findViewById(R.id.studyoptions_start)).setBackgroundResource(mButtonStyle);
		        ((View)view.findViewById(R.id.studyoptions_limit)).setBackgroundResource(mButtonStyle);
		        ((View)view.findViewById(R.id.studyoptions_cram)).setBackgroundResource(mButtonStyle);
		        ((View)view.findViewById(R.id.studyoptions_statistics)).setBackgroundResource(mButtonStyle);
		        ((View)view.findViewById(R.id.studyoptions_card_browser)).setBackgroundResource(mButtonStyle);
//		        ((Button)view.findViewById(R.id.studyoptions_start)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.blue_ic_browser, 0, 0, 0);
				break;
			}
			break;
		case CALLER_DECKPICKER:
			if (view.getId() == R.id.deckpicker_buttons) {
				view.setBackgroundResource(mDeckpickerBackground);
			} else {
				view.setBackgroundResource(mButtonStyle);				
			}
			break;
		case CALLER_DECKPICKER_DECK:
			if (view.getId() == R.id.DeckPickerCompletionMat) {
				view.setBackgroundResource(mProgressbarsFrameColor);
			} else if (view.getId() == R.id.DeckPickerCompletionAll) {
				view.setBackgroundResource(mProgressbarsYoungColor);
			}
			break;
		case CALLER_REVIEWER:
	        ((View)view.findViewById(R.id.flip_card)).setBackgroundResource(mButtonStyle);
	        ((View)view.findViewById(R.id.ease1)).setBackgroundResource(mButtonStyle);
	        ((View)view.findViewById(R.id.ease2)).setBackgroundResource(mButtonStyle);
	        ((View)view.findViewById(R.id.ease3)).setBackgroundResource(mButtonStyle);
	        ((View)view.findViewById(R.id.ease4)).setBackgroundResource(mButtonStyle);
			break;
		}
	}


	public static void resetTheme(){
		mCurrentTheme = -1;
	}


	public static int[] listColors() {
		return mListColors;
	}

}
