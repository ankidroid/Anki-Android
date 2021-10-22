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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import com.drakeet.drawer.FullDraggableContainer;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.ichi2.anki.dialogs.HelpDialog;
import com.ichi2.themes.Themes;
import com.ichi2.utils.HandlerUtils;

import java.util.Arrays;

import androidx.drawerlayout.widget.ClosableDrawerLayout;

import androidx.drawerlayout.widget.DrawerLayout;
import timber.log.Timber;
import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;


public abstract class NavigationDrawerActivity extends AnkiActivity implements NavigationView.OnNavigationItemSelectedListener {

    /**
     * Navigation Drawer
     */
    protected CharSequence mTitle;
    protected Boolean mFragmented = false;
    private boolean mNavButtonGoesBack = false;
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
    private static final String NIGHT_MODE_PREFERENCE = "invertedColors";
    public static final String FULL_SCREEN_NAVIGATION_DRAWER = "gestureFullScreenNavigationDrawer";

    /**
     * runnable that will be executed after the drawer has been closed.
     */
    private Runnable mPendingRunnable;

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        // Using ClosableDrawerLayout as a parent view.
        ClosableDrawerLayout closableDrawerLayout = (ClosableDrawerLayout) LayoutInflater.from(this).inflate(getNavigationDrawerLayout(), null, false);
        // Get CoordinatorLayout using resource ID
        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) LayoutInflater.from(this).inflate(layoutResID, closableDrawerLayout, false);
        if (preferences.getBoolean(FULL_SCREEN_NAVIGATION_DRAWER, false)) {
            // If full screen navigation drawer is needed, then add FullDraggableContainer as a child view of closableDrawerLayout.
            // Then add coordinatorLayout as a child view of fullDraggableContainer.
            FullDraggableContainer fullDraggableContainer = new FullDraggableContainer(this);
            fullDraggableContainer.addView(coordinatorLayout);
            closableDrawerLayout.addView(fullDraggableContainer, 0);
        } else {
            // If full screen navigation drawer is not needed, then directly add coordinatorLayout as the child view.
            closableDrawerLayout.addView(coordinatorLayout, 0);
        }

        setContentView(closableDrawerLayout);
    }

    private @LayoutRes int getNavigationDrawerLayout() {
        return fitsSystemWindows() ? R.layout.navigation_drawer_layout : R.layout.navigation_drawer_layout_fullscreen;
    }

    /** Whether android:fitsSystemWindows="true" should be applied to the navigation drawer */
    protected boolean fitsSystemWindows() {
        return true;
    }

    // Navigation drawer initialisation
    protected void initNavigationDrawer(View mainView) {
        // Create inherited navigation drawer layout here so that it can be used by parent class
        mDrawerLayout = mainView.findViewById(R.id.drawer_layout);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // Force transparent status bar with primary dark color underlayed so that the drawer displays under status bar
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.transparent));
        mDrawerLayout.setStatusBarBackgroundColor(Themes.getColorFromAttr(this, R.attr.colorPrimaryDark));
        // Setup toolbar and hamburger
        mNavigationView = mDrawerLayout.findViewById(R.id.navdrawer_items_container);
        mNavigationView.setNavigationItemSelectedListener(this);
        Toolbar toolbar = mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            // enable ActionBar app icon to behave as action to toggle nav drawer
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);

            // Decide which action to take when the navigation button is tapped.
            toolbar.setNavigationOnClickListener(v -> onNavigationPressed());
        }
        // Configure night-mode switch
        final SharedPreferences preferences = getPreferences();
        View actionLayout = mNavigationView.getMenu().findItem(R.id.nav_night_mode).getActionView();
        mNightModeSwitch = actionLayout.findViewById(R.id.switch_compat);
        mNightModeSwitch.setChecked(preferences.getBoolean(NIGHT_MODE_PREFERENCE, false));
        mNightModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> applyNightMode(isChecked));
        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, 0, 0) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                supportInvalidateOptionsMenu();

                // If animations are disabled, this is executed before onNavigationItemSelected is called
                // PERF: May be able to reduce this delay
                HandlerUtils.postDelayedOnNewHandler(() -> {
                    if (mPendingRunnable != null) {
                        HandlerUtils.postOnNewHandler(mPendingRunnable); // TODO: See if we can use the same handler here
                        mPendingRunnable = null;
                    }
                }, 100);
            }


            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                supportInvalidateOptionsMenu();
            }
        };
        if (mDrawerLayout instanceof ClosableDrawerLayout) {
            ((ClosableDrawerLayout) mDrawerLayout).setAnimationEnabled(animationEnabled());
        } else {
            Timber.w("Unexpected Drawer layout - could not modify navigation animation");
        }
        mDrawerToggle.setDrawerSlideAnimationEnabled(animationEnabled());
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        enablePostShortcut(this);
    }


    @TargetApi(Build.VERSION_CODES.N_MR1)
    public static void enablePostShortcut(@NonNull Context context) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }
        
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);

        // Review Cards Shortcut
        Intent intentReviewCards = new Intent(context, Reviewer.class);
        intentReviewCards.setAction(Intent.ACTION_VIEW);
        intentReviewCards.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ShortcutInfo reviewCardsShortcut = new ShortcutInfo.Builder(context, "reviewCardsShortcutId")
                .setShortLabel(context.getString(R.string.card_info_reviews))
                .setLongLabel(context.getString(R.string.card_info_reviews))
                .setIcon(Icon.createWithResource(context, R.drawable.ankidroid_logo))
                .setIntent(intentReviewCards)
                .build();

        // Add Note Shortcut
        Intent intentAddNote = new Intent(context, NoteEditor.class);
        intentAddNote.setAction(Intent.ACTION_VIEW);
        intentAddNote.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intentAddNote.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
        ShortcutInfo NoteEditorShortcut = new ShortcutInfo.Builder(context, "noteEditorShortcutId")
                .setShortLabel(context.getString(R.string.menu_add_note))
                .setLongLabel(context.getString(R.string.menu_add_note))
                .setIcon(Icon.createWithResource(context, R.drawable.ankidroid_logo))
                .setIntent(intentAddNote)
                .build();

        // CardBrowser Shortcut
        Intent intentCardBrowser = new Intent(context, CardBrowser.class);
        intentCardBrowser.setAction(Intent.ACTION_VIEW);
        intentCardBrowser.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ShortcutInfo cardBrowserShortcut = new ShortcutInfo.Builder(context, "cardBrowserShortcutId")
                .setShortLabel(context.getString(R.string.card_browser))
                .setLongLabel(context.getString(R.string.card_browser))
                .setIcon(Icon.createWithResource(context, R.drawable.ankidroid_logo))
                .setIntent(intentCardBrowser)
                .build();

        shortcutManager.addDynamicShortcuts(Arrays.asList(reviewCardsShortcut, NoteEditorShortcut, cardBrowserShortcut));

    }


    /**
     * Sets selected navigation drawer item
     */
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

    private SharedPreferences getPreferences() {
        return AnkiDroidApp.getSharedPrefs(NavigationDrawerActivity.this);
    }

    private void applyNightMode(boolean setToNightMode) {
        final SharedPreferences preferences = getPreferences();
        Timber.i("Night mode was %s", setToNightMode ? "enabled" : "disabled");
        preferences.edit().putBoolean(NIGHT_MODE_PREFERENCE, setToNightMode).apply();
        restartActivityInvalidateBackstack(NavigationDrawerActivity.this);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
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
        final SharedPreferences preferences = getPreferences();
        Timber.i("Handling Activity Result: %d. Result: %d", requestCode, resultCode);
        NotificationChannels.setup(getApplicationContext());
        // Restart the activity on preference change
        if (requestCode == REQUEST_PREFERENCES_UPDATE) {
            // collection path hasn't been changed so just restart the current activity
            if ((this instanceof Reviewer) && preferences.getBoolean("tts", false)) {
                // Workaround to kick user back to StudyOptions after opening settings from Reviewer
                // because onDestroy() of old Activity interferes with TTS in new Activity
                finishWithoutAnimation();
            } else if (mOldTheme != Themes.getCurrentTheme(getApplicationContext())) {
                // The current theme was changed, so need to reload the stack with the new theme
                restartActivityInvalidateBackstack(this);
            } else {
                restartActivity();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            Timber.i("Back key pressed");
            closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Called, when navigation button of the action bar is pressed.
     * Design pattern: template method. Subclasses can override this to define their own behaviour.
     */
    protected void onNavigationPressed() {
        if (mNavButtonGoesBack) {
            finishWithAnimation(END);
        } else {
            openDrawer();
        }
    }

    @Override
    public boolean onNavigationItemSelected(final MenuItem item) {
        // Don't do anything if user selects already selected position
        if (item.isChecked()) {
            return true;
        }

        /*
         * This runnable will be executed in onDrawerClosed(...)
         * to make the animation more fluid on older devices.
         */
        mPendingRunnable = () -> {
            // Take action if a different item selected
            int itemId = item.getItemId();
            if (itemId == R.id.nav_decks) {
                Timber.i("Navigating to decks");
                Intent deckPicker = new Intent(NavigationDrawerActivity.this, DeckPicker.class);
                // opening DeckPicker should use the instance on the back stack & clear back history
                deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivityWithAnimation(deckPicker, END);
            } else if (itemId == R.id.nav_browser) {
                Timber.i("Navigating to card browser");
                openCardBrowser();
            } else if (itemId == R.id.nav_stats) {
                Timber.i("Navigating to stats");
                Intent intent = new Intent(NavigationDrawerActivity.this, Statistics.class);
                startActivityForResultWithAnimation(intent, REQUEST_STATISTICS, START);
            } else if (itemId == R.id.nav_night_mode) {
                Timber.i("Toggling Night Mode");
                mNightModeSwitch.performClick();
            } else if (itemId == R.id.nav_settings) {
                Timber.i("Navigating to settings");
                // Remember the theme we started with so we can restart the Activity if it changes
                mOldTheme = Themes.getCurrentTheme(getApplicationContext());
                startActivityForResultWithAnimation(new Intent(NavigationDrawerActivity.this, Preferences.class), REQUEST_PREFERENCES_UPDATE, FADE);
                // #6192 - stop crash on changing collection path - cancel tasks if moving to settings
                if (this instanceof Statistics) {
                    finishWithAnimation(FADE);
                }
            } else if (itemId == R.id.nav_help) {
                Timber.i("Navigating to help");
                showDialogFragment(HelpDialog.createInstance(this));
            } else if (itemId == R.id.support_ankidroid) {
                Timber.i("Navigating to support AnkiDroid");
                showDialogFragment(HelpDialog.createInstanceForSupportAnkiDroid(this));
            }
        };

        closeDrawer();
        return true;
    }

    protected void openCardBrowser() {
        Intent intent = new Intent(NavigationDrawerActivity.this, CardBrowser.class);
        Long currentCardId = getCurrentCardId();
        if (currentCardId != null) {
            intent.putExtra("currentCard", currentCardId);
        }
        startActivityForResultWithAnimation(intent, REQUEST_BROWSE_CARDS, START);
    }

    // Override this to specify a specific card id
    @Nullable
    protected Long getCurrentCardId() {
        return null;
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

    protected void restoreDrawerIcon() {
        if (mDrawerToggle != null) {
            getDrawerToggle().setDrawerIndicatorEnabled(true);
        }
        mNavButtonGoesBack = false;
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    /**
     * Restart the activity and discard old backstack, creating it new from the hierarchy in the manifest
     */
    protected void restartActivityInvalidateBackstack(AnkiActivity activity) {
        Timber.i("AnkiActivity -- restartActivityInvalidateBackstack()");
        Intent intent = new Intent();
        intent.setClass(activity, activity.getClass());
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(activity);
        stackBuilder.addNextIntentWithParentStack(intent);
        stackBuilder.startActivities(new Bundle());
        activity.finishWithoutAnimation();
    }


    public void toggleDrawer() {
        if (!isDrawerOpen()) {
            openDrawer();
        } else {
            closeDrawer();
        }
    }


    private void openDrawer() {
        mDrawerLayout.openDrawer(GravityCompat.START, animationEnabled());
    }

    private void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START, animationEnabled());
    }


    public void focusNavigation() {
        // mNavigationView.getMenu().getItem(0).setChecked(true);
        selectNavigationItem(R.id.nav_decks);
        mNavigationView.requestFocus();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isDrawerOpen()) {
            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            closeDrawer();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
