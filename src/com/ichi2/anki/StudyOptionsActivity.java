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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.ListView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

public class StudyOptionsActivity extends NavigationDrawerActivity implements StudyOptionsFragment.OnStudyOptionsReloadListener {

    private StudyOptionsFragment mCurrentFragment;

    private BroadcastReceiver mUnmountReceiver = null;
    private StyledOpenCollectionDialog mNotMountedDialog;
    private EditText mDialogEditText = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        // The empty frame layout is a workaround for fragments not showing when they are added
        // to android.R.id.content when an action bar is used in Android 2.1 (and potentially
        // higher) with the appcompat package.
        setContentView(R.layout.studyoptions);
        // create inherited navigation drawer layout here so that it can be used by parent class
        mDrawerLayout = (DrawerLayout) findViewById(R.id.studyoptions_drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.studyoptions_left_drawer);
        initNavigationDrawer();
        if (savedInstanceState == null) {
            loadStudyOptionsFragment();
        }
        registerExternalStorageListener();
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

    public void loadStudyOptionsFragment() {
        loadStudyOptionsFragment(0, null);
    }


    public void loadStudyOptionsFragment(long deckId, Bundle cramConfig) {
        mCurrentFragment = StudyOptionsFragment.newInstance(deckId, null);
        Bundle args = getIntent().getExtras();

        if (cramConfig != null) {
            args.putBundle("cramInitialConfig", cramConfig);
        }
        mCurrentFragment.setArguments(args);
        getSupportFragmentManager().beginTransaction().replace(R.id.studyoptions_frame, mCurrentFragment).commit();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        for (int i=0; i< mDrawerList.getCount(); i++) {
            mDrawerList.setItemChecked(i, false);
        }
    }    


    // TODO: onpause, onstop

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        
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
        Log.i(AnkiDroidApp.TAG, "StudyOptionsActivity: onActivityResult");

        String newLanguage = AnkiDroidApp.getSharedPrefs(this).getString(Preferences.LANGUAGE, "");
        if (AnkiDroidApp.setLanguage(newLanguage)) {
            AnkiDroidApp.getCompat().invalidateOptionsMenu(this);
        }
        if (mCurrentFragment != null) {
            mCurrentFragment.restorePreferences();
        }
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
            Log.i(AnkiDroidApp.TAG, "StudyOptions - onBackPressed()");
            closeStudyOptions();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onStop() {
        super.onStop();
        if (!isFinishing() && mCurrentFragment != null && mCurrentFragment.dbSaveNecessary()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCurrentFragment != null) {
            return mCurrentFragment.onTouchEvent(event);
        } else {
            return false;
        }
    }


    /**
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        mNotMountedDialog = StyledOpenCollectionDialog.show(StudyOptionsActivity.this, getResources()
                                .getString(R.string.sd_card_not_mounted), new OnCancelListener() {

                            @Override
                            public void onCancel(DialogInterface arg0) {
                                finish();
                            }
                        });
                    } else if (intent.getAction().equals(SdCardReceiver.MEDIA_MOUNT)) {
                        if (mNotMountedDialog != null && mNotMountedDialog.isShowing()) {
                            mNotMountedDialog.dismiss();
                        }
                        mCurrentFragment.reloadCollection();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            iFilter.addAction(SdCardReceiver.MEDIA_MOUNT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }
}
