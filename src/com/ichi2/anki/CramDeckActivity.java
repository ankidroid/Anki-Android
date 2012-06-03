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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;

public class CramDeckActivity extends FragmentActivity {

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
            // During initial setup, plug in the details fragment.
            Fragment details = new CramDeckFragment();
            details.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, details).commit();
        }
    }
    
//
//
//	private void closeStudyOptions() {
//		closeStudyOptions(RESULT_OK);
//	}
//	private void closeStudyOptions(int result) {
////		mCompat.invalidateOptionsMenu(this);
//		setResult(result);
//		finish();
//		if (UIUtils.getApiLevel() > 4) {
//			ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
//		}
//	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			Log.i(AnkiDroidApp.TAG, "StudyOptions - onBackPressed()");
//			if (mCurrentContentView == CONTENT_CONGRATS) {
//				finishCongrats();
//			} else {
//				closeStudyOptions();
//			}
			closeCramDeckAdder();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	public void closeCramDeckAdder() {
		finish();
		if (UIUtils.getApiLevel() > 4) {
			ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
		}
	}

}