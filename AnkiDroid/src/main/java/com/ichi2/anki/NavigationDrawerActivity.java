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
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;

import java.util.ArrayList;
import timber.log.Timber;


public class NavigationDrawerActivity extends AnkiActivity {

    /** Navigation Drawer */
    protected CharSequence mTitle;
    protected Boolean mFragmented = false;
    // Preselection for DeckDropDownAdapter
    protected static boolean sIsWholeCollection = true;
    private DrawerLayout mDrawerLayout;
    private ViewGroup mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private String[] mNavigationTitles;
    private TypedArray mNavigationImages;
    // list of navdrawer items that were added to the navdrawer, in order
    private ArrayList<Integer> mNavDrawerItems = new ArrayList<>();
    // Other members
    private String mOldColPath;
    // Navigation drawer list item entries (titles and icons must be in the same order)
    protected static final int DRAWER_DECK_PICKER = 0;
    protected static final int DRAWER_BROWSER = 1;
    protected static final int DRAWER_STATISTICS = 2;
    protected static final int DRAWER_SETTINGS = 3;
    protected static final int DRAWER_HELP = 4;
    protected static final int DRAWER_FEEDBACK = 5;
    protected static final int DRAWER_INVALID = -1;
    protected static final int DRAWER_SEPARATOR = -2;
    // Intent request codes
    public static final int REQUEST_PREFERENCES_UPDATE = 100;
    public static final int REQUEST_BROWSE_CARDS = 101;
    public static final int REQUEST_STATISTICS = 102;

    /**
     * Returns the navdrawer item that corresponds to this Activity. Subclasses override this to
     * indicate what navdrawer item corresponds to them.
     * Return DRAWER_INVALID to mean that this Activity should not have a navdrawer.
     */
    protected int getSelfNavDrawerItem() {
        return DRAWER_INVALID;
    }

    // Navigation drawer initialisation
    protected void initNavigationDrawer(View mainView){
        // Create inherited navigation drawer layout here so that it can be used by parent class
        mDrawerLayout = (DrawerLayout) mainView.findViewById(R.id.drawer_layout);
        mTitle = getTitle();
        mNavigationTitles = getResources().getStringArray(R.array.navigation_titles);
        mNavigationImages = getResources().obtainTypedArray(R.array.drawer_images);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        populateNavDrawerItems();
        createNavDrawerItems(mainView);

        Toolbar toolbar = (Toolbar) mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        // enable ActionBar app icon to behave as action to toggle nav drawer
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, 0, 0) {
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

        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }


    /** Set the items to be included in the navigation drawer */
    private void populateNavDrawerItems() {
        mNavDrawerItems.add(DRAWER_DECK_PICKER);
        mNavDrawerItems.add(DRAWER_BROWSER);
        mNavDrawerItems.add(DRAWER_STATISTICS);

        mNavDrawerItems.add(DRAWER_SEPARATOR);

        mNavDrawerItems.add(DRAWER_SETTINGS);
        mNavDrawerItems.add(DRAWER_HELP);
        mNavDrawerItems.add(DRAWER_FEEDBACK);
    }


    /** Collect the views for all navigation drawer items */
    private void createNavDrawerItems(View mainview) {
        mDrawerList = (ViewGroup) mainview.findViewById(R.id.navdrawer_items_container);
        if (mDrawerList == null) {
            return;
        }

        mDrawerList.removeAllViews();
        for (int itemId : mNavDrawerItems) {
            mDrawerList.addView(makeNavDrawerItem(itemId, mDrawerList));
        }
    }


    /** Returns the view for the specified navigation drawer item */
    private View makeNavDrawerItem(final int itemId, ViewGroup container) {
        boolean selected = getSelfNavDrawerItem() == itemId;
        View view = getLayoutInflater().inflate(isSeparator(itemId) ?
                R.layout.navdrawer_divider :
                R.layout.navdrawer_item, container, false);
        if (isSeparator(itemId)) {
            // nothing else necessary for separators
            return view;
        }
        // set the text and image according to lists specified in resources
        TextView txtTitle = (TextView) view.findViewById(R.id.drawer_list_item_text);
        ImageView imgIcon = (ImageView) view.findViewById(R.id.drawer_list_item_icon);

        txtTitle.setText(mNavigationTitles[itemId]);
        imgIcon.setImageResource(mNavigationImages.getResourceId(itemId, -1));

        formatNavDrawerItem(view, selected);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.i("Item %d selected in navigation drawer", itemId);
                selectNavigationItem(itemId);
            }
        });
        return view;
    }


    /** configure item appearance according to whether or not it's selected */
    private void formatNavDrawerItem(View view, boolean selected) {
        TextView txtTitle = (TextView) view.findViewById(R.id.drawer_list_item_text);
        ImageView imgIcon = (ImageView) view.findViewById(R.id.drawer_list_item_icon);

        if (selected) view.setBackgroundColor(
                getResources().getColor(R.color.navdrawer_background_selected));
        txtTitle.setTypeface(null, selected ?
                Typeface.BOLD :
                Typeface.NORMAL);
        txtTitle.setTextColor(selected ?
                getResources().getColor(R.color.navdrawer_text_color_selected) :
                getResources().getColor(R.color.navdrawer_text_color));
        imgIcon.setColorFilter(selected ?
                getResources().getColor(R.color.navdrawer_icon_tint_selected) :
                getResources().getColor(R.color.navdrawer_icon_tint), PorterDuff.Mode.SRC_IN);
    }


    /** Launches target activity for selected navigation drawer item */
    protected void selectNavigationItem(int itemId) {
        mDrawerLayout.closeDrawer(mDrawerList);
        // Return if trying to start own activity
        if (itemId == getSelfNavDrawerItem()) {
            return;
        }

        // Set title and launch target activity
        setTitle(mNavigationTitles[itemId]);
        switch (itemId){
            case DRAWER_DECK_PICKER:
                Intent deckPicker = new Intent(this, DeckPicker.class);
                deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);    // opening DeckPicker should clear back history
                startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.RIGHT);
                break;

            case DRAWER_BROWSER:
                Intent cardBrowser = new Intent(this, CardBrowser.class);
                cardBrowser.putExtra("selectedDeck", getCol().getDecks().selected());
                startActivityForResultWithAnimation(cardBrowser, REQUEST_BROWSE_CARDS, ActivityTransitionAnimation.LEFT);
                break;

            case DRAWER_STATISTICS:
                Intent intent = new Intent(this, Statistics.class);
                intent.putExtra("selectedDeck", getCol().getDecks().selected());
                startActivityForResultWithAnimation(intent, REQUEST_STATISTICS, ActivityTransitionAnimation.LEFT);
                break;

            case DRAWER_SETTINGS:
                mOldColPath = CollectionHelper.getCurrentAnkiDroidDirectory(this);
                startActivityForResultWithAnimation(new Intent(this, Preferences.class), REQUEST_PREFERENCES_UPDATE, ActivityTransitionAnimation.FADE);
                break;

            case DRAWER_HELP:
                Intent helpIntent = new Intent("android.intent.action.VIEW", Uri.parse(AnkiDroidApp.getManualUrl()));
                startActivityWithoutAnimation(helpIntent);
                break;

            case DRAWER_FEEDBACK:
                Intent feedbackIntent = new Intent("android.intent.action.VIEW", Uri.parse(AnkiDroidApp.getFeedbackUrl()));
                startActivityWithoutAnimation(feedbackIntent);
                break;

            default:
                break;
        }
    }

    /**
     * @return the name of the currently checked item in the navigation drawer
     */
    protected String getSelectedNavDrawerTitle() {
        return mNavigationTitles[getSelfNavDrawerItem()];
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    private boolean isSeparator(int itemId) {
        return itemId == DRAWER_SEPARATOR;
    }
    
    /* Members not related directly to navigation drawer */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mNavigationImages.recycle();
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
                } else {
                    restartActivity();
                }
            } else {
                // collection path has changed so kick the user back to the DeckPicker
                CollectionHelper.getInstance().closeCollection(true);
                finishWithoutAnimation();
                Intent deckPicker = new Intent(this, DeckPicker.class);
                deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityWithoutAnimation(deckPicker);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    public static void setIsWholeCollection(boolean isWholeCollection){
        sIsWholeCollection = isWholeCollection;
    }

    public static boolean isWholeCollection() {
        return sIsWholeCollection;
    }
}