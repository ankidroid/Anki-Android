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

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener;
import com.ichi2.anki.dialogs.CustomStudyDialog;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONArray;

import timber.log.Timber;

public class StudyOptionsActivity extends NavigationDrawerActivity implements StudyOptionsListener,
        CustomStudyDialog.CustomStudyListener {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The empty frame layout is a workaround for fragments not showing when they are added
        // to android.R.id.content when an action bar is used in Android 2.1 (and potentially
        // higher) with the appcompat package.
        View mainView = getLayoutInflater().inflate(R.layout.studyoptions, null);
        setContentView(mainView);
        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(mainView);
        if (savedInstanceState == null) {
            loadStudyOptionsFragment();
        }
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        // Make the add button visible when not fragmented layout
        MenuItem addFromStudyOptions = menu.findItem(R.id.action_add_note_from_study_options);
        if (addFromStudyOptions != null) {
            addFromStudyOptions.setVisible(true);
        }
        return true;
    }


    private void loadStudyOptionsFragment() {
        boolean withDeckOptions = false;
        if (getIntent().getExtras() != null) {
            withDeckOptions = getIntent().getExtras().getBoolean("withDeckOptions");
        }
        StudyOptionsFragment currentFragment = StudyOptionsFragment.newInstance(withDeckOptions);
        getSupportFragmentManager().beginTransaction().replace(R.id.studyoptions_frame, currentFragment).commit();
    }


    private StudyOptionsFragment getCurrentFragment() {
        return (StudyOptionsFragment) getSupportFragmentManager().findFragmentById(R.id.studyoptions_frame);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                closeStudyOptions();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Timber.d("onActivityResult (requestCode = %d, resultCode = %d)", requestCode, resultCode);

        String newLanguage = AnkiDroidApp.getSharedPrefs(this).getString(Preferences.LANGUAGE, "");
        if (AnkiDroidApp.setLanguage(newLanguage)) {
            supportInvalidateOptionsMenu();
        }
        getCurrentFragment().restorePreferences();
    }


    private void closeStudyOptions() {
        closeStudyOptions(RESULT_OK);
    }


    private void closeStudyOptions(int result) {
        // mCompat.invalidateOptionsMenu(this);
        setResult(result);
        finishWithAnimation(ActivityTransitionAnimation.RIGHT);
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("StudyOptionsActivity:: onBackPressed()");
            closeStudyOptions();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onStop() {
        super.onStop();
        if (colIsOpen()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground(this);
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        selectNavigationItem(-1);
    }


    @Override
    public void onRequireDeckListUpdate() {
        getCurrentFragment().refreshInterface();
    }

    /**
     * Callback methods from CustomStudyDialog
     */
    @Override
    public void onCreateCustomStudySession() {
        // Sched already reset by DeckTask in CustomStudyDialog
        getCurrentFragment().refreshInterface();
    }

    @Override
    public void onExtendStudyLimits() {
        // Sched needs to be reset so provide true argument
        getCurrentFragment().refreshInterface(true);
    }
}
