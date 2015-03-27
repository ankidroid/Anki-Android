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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;

import java.util.ArrayList;
import timber.log.Timber;


public class NavigationDrawerActivity extends AnkiActivity {
    
    /** Navigation Drawer */
    protected CharSequence mTitle;
    protected Boolean mFragmented = false;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private String[] mNavigationTitles;
    private TypedArray mNavigationImages;
    // list of navdrawer items that were added to the navdrawer, in order
    private ArrayList<Integer> mNavDrawerItems = new ArrayList<>();
    // Other members
    private String mOldColPath;
    // Navigation drawer list item entries
    protected static final int DRAWER_DECK_PICKER = 0;
    protected static final int DRAWER_BROWSER = 1;
    protected static final int DRAWER_STATISTICS = 2;
    protected static final int DRAWER_SETTINGS = 3;
    protected static final int DRAWER_HELP = 4;
    protected static final int DRAWER_FEEDBACK = 5;
    protected static final int DRAWER_SEPARATOR = -1;
    // Intent request codes
    public static final int REQUEST_PREFERENCES_UPDATE = 100;
    public static final int REQUEST_BROWSE_CARDS = 101;
    public static final int REQUEST_STATISTICS = 102;
    
    
    // navigation drawer stuff
    protected void initNavigationDrawer(View mainView){
        // Create inherited navigation drawer layout here so that it can be used by parent class
        mDrawerLayout = (DrawerLayout) mainView.findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) mainView.findViewById(R.id.left_drawer);
        mTitle = getTitle();
        mNavigationTitles = getResources().getStringArray(R.array.navigation_titles);
        mNavigationImages = getResources().obtainTypedArray(R.array.drawer_images);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener
        mDrawerList.setAdapter(new NavDrawerListAdapter(this, mNavigationTitles, mNavigationImages, mNavDrawerItems));
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        mNavDrawerItems.add(DRAWER_DECK_PICKER);
        mNavDrawerItems.add(DRAWER_BROWSER);
        mNavDrawerItems.add(DRAWER_STATISTICS);
        mNavDrawerItems.add(DRAWER_SEPARATOR);
        mNavDrawerItems.add(DRAWER_SETTINGS);
        mNavDrawerItems.add(DRAWER_HELP);
        mNavDrawerItems.add(DRAWER_FEEDBACK);

        Toolbar toolbar = (Toolbar) mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        // enable ActionBar app icon to behave as action to toggle nav drawer
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        
        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_menu_white_24dp,  /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
                ) {
            public void onDrawerClosed(View view) {
                getSupportActionBar().setTitle(mTitle);
                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                getSupportActionBar().setTitle(getResources().getString(R.string.app_name));
                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }
    
    /* The click listener for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Timber.i("Item %d selected in navigation drawer", position);
            selectNavigationItem(position);
        }
    }

    protected void selectNavigationItem(int position) {
        // switch to new activity... be careful not to start own activity or we can get stuck in a loop
        // update selected item and title, then close the drawer
        int itemId = mNavDrawerItems.get(position);
        mDrawerList.setItemChecked(position, true);
        setTitle(mNavigationTitles[itemId]);
        mDrawerLayout.closeDrawer(mDrawerList);

        switch (itemId){
            case DRAWER_DECK_PICKER:
                if (!(this instanceof DeckPicker)) {
                    Intent deckPicker = new Intent(this, DeckPicker.class);
                    deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);    // opening DeckPicker should clear back history
                    startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.RIGHT);
                }
                break;
            case DRAWER_BROWSER:
                Intent cardBrowser = new Intent(this, CardBrowser.class);
                if (!(this instanceof CardBrowser)) {
                    if (this instanceof DeckPicker && !mFragmented){
                        cardBrowser.putExtra("fromDeckpicker", true);
                    }                    
                    startActivityForResultWithAnimation(cardBrowser, REQUEST_BROWSE_CARDS, ActivityTransitionAnimation.LEFT);
                }
                break;
            case DRAWER_STATISTICS:
            	boolean selectAllDecksButton = false;
                if(!(this instanceof Statistics)) {
                    if ((this instanceof DeckPicker && !mFragmented)) {
                        selectAllDecksButton = true;
                    }
                    AnkiStatsTaskHandler.setIsWholeCollection(selectAllDecksButton);
                    Intent intent = new Intent(this, Statistics.class);
                    intent.putExtra("selectedDeck", getCol().getDecks().selected());
                    startActivityForResultWithAnimation(intent, REQUEST_STATISTICS, ActivityTransitionAnimation.LEFT);
                }

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
        int position = mDrawerList.getCheckedItemPosition();
        return mNavigationTitles[position];
    }

    protected void deselectAllNavigationItems() {
        // Deselect all entries in navigation drawer
        for (int i=0; i< mDrawerList.getCount(); i++) {
            mDrawerList.setItemChecked(i, false);
        }
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
    
    
    /* Adapter which controls how to display the items in the navigation drawer */
    private class NavDrawerListAdapter extends BaseAdapter {
        
        private Context context;
        private String[] navDrawerTitles;
        private TypedArray navDrawerImages;
        private ArrayList<Integer> navDrawerItems;

        public NavDrawerListAdapter(Context context, String[] navDrawerTitles,
                TypedArray navDrawerImages, ArrayList<Integer> navDrawerItems){
            this.context = context;
            this.navDrawerTitles = navDrawerTitles;
            this.navDrawerImages = navDrawerImages;
            this.navDrawerItems = navDrawerItems;
        }

        @Override
        public int getCount() {
            return navDrawerItems.size();
        }

        @Override
        public Object getItem(int position) {
            return navDrawerTitles[navDrawerItems.get(position)];
        }

        @Override
        public long getItemId(int position) {
            return navDrawerItems.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int itemId = navDrawerItems.get(position);
            if (convertView == null) {
                LayoutInflater mInflater = (LayoutInflater)
                        context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(isSeparator(itemId) ?
                            R.layout.navdrawer_divider :
                            R.layout.navdrawer_item, parent, false);
            }
            if (isSeparator(itemId)) {
                // nothing else necessary for separators
                return convertView;
            }
            // set the text and image according to lists specified in resources
            TextView txtTitle = (TextView) convertView.findViewById(R.id.drawer_list_item_text);
            ImageView imgIcon = (ImageView) convertView.findViewById(R.id.drawer_list_item_icon);

            txtTitle.setText(navDrawerTitles[itemId]);
            imgIcon.setImageResource(navDrawerImages.getResourceId(itemId, -1));

            formatNavDrawerItem(convertView, NavigationDrawerActivity.this.mDrawerList.getCheckedItemPosition()==position);
            return convertView;
        }

        // configure item appearance according to whether or not it's selected
        private void formatNavDrawerItem(View view, boolean selected) {
            TextView txtTitle = (TextView) view.findViewById(R.id.drawer_list_item_text);
            ImageView imgIcon = (ImageView) view.findViewById(R.id.drawer_list_item_icon);

            view.setBackgroundColor(selected ?
                    getResources().getColor(R.color.navdrawer_background_selected) :
                    getResources().getColor(R.color.navdrawer_background));
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

    public DrawerLayout getDrawerLayout() {
        return mDrawerLayout;
    }
    
    public ListView getDrawerList() {
        return mDrawerList;
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
}
