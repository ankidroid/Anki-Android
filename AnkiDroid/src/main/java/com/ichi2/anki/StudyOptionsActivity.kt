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
import android.view.MenuItem;
import android.view.View;

import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory;
import com.ichi2.widget.WidgetStatus;

import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.END;

public class StudyOptionsActivity extends NavigationDrawerActivity implements StudyOptionsListener,
        CustomStudyDialog.CustomStudyListener {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        CustomStudyDialogFactory customStudyDialogFactory = new CustomStudyDialogFactory(this::getCol, this);
        customStudyDialogFactory.attachToActivity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.studyoptions);
        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(findViewById(android.R.id.content));
        if (savedInstanceState == null) {
            loadStudyOptionsFragment();
        }
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
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            closeStudyOptions();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Timber.d("onActivityResult (requestCode = %d, resultCode = %d)", requestCode, resultCode);
    }


    private void closeStudyOptions() {
        closeStudyOptions(RESULT_OK);
    }


    private void closeStudyOptions(int result) {
        // mCompat.invalidateOptionsMenu(this);
        setResult(result);
        finishWithAnimation(END);
    }


    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            super.onBackPressed();
        } else {
            Timber.i("Back key pressed");
            closeStudyOptions();
        }
    }


    @Override
    public void onStop() {
        super.onStop();
        if (colIsOpen()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
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
        // Sched already reset by CollectionTask in CustomStudyDialog
        getCurrentFragment().refreshInterface();
    }

    @Override
    public void onExtendStudyLimits() {
        // Sched needs to be reset so provide true argument
        getCurrentFragment().refreshInterface(true);
    }
}
