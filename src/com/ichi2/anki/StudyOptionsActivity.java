/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
import com.ichi2.anki2.R;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.libanki.Collection;
import com.ichi2.widget.WidgetStatus;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

public class StudyOptionsActivity extends FragmentActivity {

	/** Menus */
	private static final int MENU_PREFERENCES = 201;
	public static final int MENU_ROTATE = 202;

	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        if (getResources().getConfiguration().orientation
//                == Configuration.ORIENTATION_LANDSCAPE) {
//            // If the screen is now in landscape mode, we can show the
//            // dialog in-line so we don't need this activity.
//            finish();
//            return;
//        }

        if (savedInstanceState == null) {
        	setStudyContentView(StudyOptionsFragment.CONTENT_STUDY_OPTIONS);
        }
    }

    public void setStudyContentView(int view) {
        // During initial setup, plug in the details fragment.
        Fragment details = new StudyOptionsFragment(view);
        details.setArguments(getIntent().getExtras());
        getSupportFragmentManager().beginTransaction().add(android.R.id.content, details).commit();
    }
    // TODO: onpause, onresume, onstop
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		UIUtils.addMenuItem(menu, Menu.NONE, MENU_PREFERENCES, Menu.NONE,
				R.string.menu_preferences, R.drawable.ic_menu_preferences);
		UIUtils.addMenuItem(menu, Menu.NONE, MENU_ROTATE, Menu.NONE,
				R.string.menu_rotate,
				android.R.drawable.ic_menu_always_landscape_portrait);
		return true;
	}


	/** Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			closeStudyOptions();
			return true;

		case MENU_PREFERENCES:
			startActivityForResult(new Intent(this, Preferences.class), StudyOptionsFragment.PREFERENCES_UPDATE);
			if (UIUtils.getApiLevel() > 4) {
				ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
			}
			return true;

		case MENU_ROTATE:
			if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			} else {
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			}
			return true;


		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void closeStudyOptions() {
		closeStudyOptions(RESULT_OK);
	}
	private void closeStudyOptions(int result) {
//		mCompat.invalidateOptionsMenu(this);
		setResult(result);
		finish();
		if (UIUtils.getApiLevel() > 4) {
			ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			Log.i(AnkiDroidApp.TAG, "StudyOptions - onBackPressed()");
//			if (mCurrentContentView == CONTENT_CONGRATS) {
//				finishCongrats();
//			} else {
				closeStudyOptions();
//			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onStop() {
		super.onStop();
		if (!isFinishing()) {
			WidgetStatus.update(this);
	        UIUtils.saveCollectionInBackground(Collection.currentCollection());			
		}
	}
}