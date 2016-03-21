/****************************************************************************************
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.compat.CompatHelper;
import com.ichi2.themes.Themes;

import timber.log.Timber;


public class NavigationDrawerActivity extends AnkiActivity implements NavigationView.OnNavigationItemSelectedListener {

    /** Navigation Drawer */
    protected CharSequence mTitle;
    protected Boolean mFragmented = false;
    private boolean mNavButtonGoesBack = false;
    // Other members
    private String mOldColPath;
    private int mOldTheme;
    // Navigation drawer list item entries
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private SwitchCompat mNightModeSwitch;
    // Intent request codes
    public static final int REQUEST_PREFERENCES_UPDATE = 100;
    public static final int REQUEST_BROWSE_CARDS = 101;
    public static final int REQUEST_STATISTICS = 102;
    // Navigation drawer initialisation
    protected void initNavigationDrawer(View mainView){
        // Create inherited navigation drawer layout here so that it can be used by parent class
        mDrawerLayout = (DrawerLayout) mainView.findViewById(R.id.drawer_layout);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        mNavigationView = (NavigationView) mDrawerLayout.findViewById(R.id.navdrawer_items_container);
        mNavigationView.setNavigationItemSelectedListener(this);
        Toolbar toolbar = (Toolbar) mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            // enable ActionBar app icon to behave as action to toggle nav drawer
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);

            // Decide which action to take when the navigation button is tapped.
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mNavButtonGoesBack) {
                        finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                    } else {
                        mDrawerLayout.openDrawer(Gravity.LEFT);
                    }
                }
            });
        }
        // Configure night-mode switch
        final SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(NavigationDrawerActivity.this);
        View actionLayout = MenuItemCompat.getActionView(mNavigationView.getMenu().findItem(R.id.nav_night_mode));
        mNightModeSwitch = (SwitchCompat) actionLayout.findViewById(R.id.switch_compat);
        mNightModeSwitch.setChecked(preferences.getBoolean("invertedColors", false));
        mNightModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Timber.i("StudyOptionsFragment:: Night mode was enabled");
                    preferences.edit().putBoolean("invertedColors", true).commit();
                } else {
                    Timber.i("StudyOptionsFragment:: Night mode was disabled");
                    preferences.edit().putBoolean("invertedColors", false).commit();
                }
                CompatHelper.getCompat().restartActivityInvalidateBackstack(NavigationDrawerActivity.this);
            }
        });
        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, 0, 0) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                supportInvalidateOptionsMenu();
            }


            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                supportInvalidateOptionsMenu();
            }
        };

        mDrawerLayout.addDrawerListener(mDrawerToggle);
    }



    /** Sets selected navigation drawer item */
    protected void selectNavigationItem(int itemId) {
        if (mNavigationView == null) {
            Timber.e("Could not select item in navigation drawer as NavigationView null");
            return;
        }
        Menu menu = mNavigationView.getMenu();
        if (itemId == -1) {
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setChecked(false);
            }
        } else {
            MenuItem item = menu.findItem(itemId);
            if (item != null) {
                item.setChecked(true);
            } else {
                Timber.e("Could not find item %d", itemId);
            }
        }
    }



    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(mTitle);
        }
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }


    public ActionBarDrawerToggle getDrawerToggle() {
        return mDrawerToggle;
    }

    /**
     * This function locks the navigation drawer closed in regards to swipes,
     * but continues to allowed it to be opened via it's indicator button. This
     * function in a noop if the drawer hasn't been initialized.
     */
    protected void disableDrawerSwipe() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
    }

    /**
     * This function allows swipes to open the navigation drawer. This
     * function in a noop if the drawer hasn't been initialized.
     */
    protected void enableDrawerSwipe() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        // Update language
        AnkiDroidApp.setLanguage(preferences.getString(Preferences.LANGUAGE, ""));
        // Restart the activity on preference change
        if (requestCode == REQUEST_PREFERENCES_UPDATE) {
            if (mOldColPath!=null && CollectionHelper.getCurrentAnkiDroidDirectory(this).equals(mOldColPath)) {
                // collection path hasn't been changed so just restart the current activity
                if ((this instanceof Reviewer) && preferences.getBoolean("tts", false)) {
                    // Workaround to kick user back to StudyOptions after opening settings from Reviewer
                    // because onDestroy() of old Activity interferes with TTS in new Activity
                    finishWithoutAnimation();
                } else if (mOldTheme != Themes.getCurrentTheme(getApplicationContext())) {
                    // The current theme was changed, so need to reload the stack with the new theme
                    CompatHelper.getCompat().restartActivityInvalidateBackstack(NavigationDrawerActivity.this);
                } else {
                    restartActivity();
                }
            } else {
                // collection path has changed so kick the user back to the DeckPicker
                CollectionHelper.getInstance().closeCollection(true);
                CompatHelper.getCompat().restartActivityInvalidateBackstack(this);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            Timber.i("Back key pressed");
            mDrawerLayout.closeDrawers();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Don't do anything if user selects already selected position
        if (item.isChecked()) {
            return true;
        }
        // Take action if a different item selected
        switch (item.getItemId()) {
            case R.id.nav_decks:
                Intent deckPicker = new Intent(this, DeckPicker.class);
                deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);    // opening DeckPicker should clear back history
                startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.RIGHT);
                break;
            case R.id.nav_browser:
                openCardBrowser();
                break;
            case R.id.nav_stats:
                Intent intent = new Intent(this, Statistics.class);
                intent.putExtra("selectedDeck", getCol().getDecks().selected());
                startActivityForResultWithAnimation(intent, REQUEST_STATISTICS, ActivityTransitionAnimation.LEFT);
                break;
            case R.id.nav_night_mode:
                mNightModeSwitch.performClick();
                return true;
            case R.id.nav_settings:
                mOldColPath = CollectionHelper.getCurrentAnkiDroidDirectory(this);
                // Remember the theme we started with so we can restart the Activity if it changes
                mOldTheme = Themes.getCurrentTheme(getApplicationContext());
                startActivityForResultWithAnimation(new Intent(this, Preferences.class), REQUEST_PREFERENCES_UPDATE, ActivityTransitionAnimation.FADE);
                break;
            case R.id.nav_help:
                openUrl(Uri.parse(AnkiDroidApp.getManualUrl()));
                break;
            case R.id.nav_feedback:
                openUrl(Uri.parse(AnkiDroidApp.getFeedbackUrl()));
                break;
            default:
                return false;
        }
        mDrawerLayout.closeDrawers();
        return true;
    }

    /**
     * Open the card browser. Override this method to pass it custom arguments
     */
    protected void openCardBrowser() {
        Intent cardBrowser = new Intent(this, CardBrowser.class);
        cardBrowser.putExtra("selectedDeck", getCol().getDecks().selected());
        startActivityForResultWithAnimation(cardBrowser, REQUEST_BROWSE_CARDS, ActivityTransitionAnimation.LEFT);
    }

    protected void showBackIcon() {
        if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(false);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mNavButtonGoesBack = true;
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }
}