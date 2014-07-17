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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.DeckTask;
import com.ichi2.charts.ChartBuilder;
import com.ichi2.themes.StyledProgressDialog;


public class NavigationDrawerActivity extends AnkiActivity {
    
    /** Navigation Drawer */
    protected DrawerLayout mDrawerLayout;
    protected ListView mDrawerList;
    protected ActionBarDrawerToggle mDrawerToggle;
    protected CharSequence mTitle;
    protected Boolean mFragmented = false;
    private CharSequence mDrawerTitle;
    private String[] mNavigationTitles;
    private TypedArray mNavigationImages;
    // Porgress dialog
    private StyledProgressDialog mProgressDialog;
    // Navigation drawer list item entries
    protected static final int DRAWER_DECK_PICKER = 0;
    protected static final int DRAWER_BROWSER = 1;
    protected static final int DRAWER_STATISTICS = 2;
    protected static final int DRAWER_SETTINGS = 3;
    protected static final int DRAWER_HELP = 4;
    protected static final int DRAWER_FEEDBACK = 5;
    // Intent return codes
    private static final int PREFERENCES_UPDATE = 0;
    private static final int BROWSE_CARDS = 14;
    
    
    // navigation drawer stuff
    protected void initNavigationDrawer(){
        mTitle = mDrawerTitle = getTitle();
        mNavigationTitles = getResources().getStringArray(R.array.navigation_titles);
        mNavigationImages = getResources().obtainTypedArray(R.array.drawer_images);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener
        mDrawerList.setAdapter(new NavDrawerListAdapter(this, mNavigationTitles, mNavigationImages));
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        // enable ActionBar app icon to behave as action to toggle nav drawer
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        
        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
                ) {
            public void onDrawerClosed(View view) {
                getSupportActionBar().setTitle(mTitle);
                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                getSupportActionBar().setTitle(mDrawerTitle);
                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }
    
    /* The click listener for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectNavigationItem(position);
        }
    }

    protected void selectNavigationItem(int position) {
        // switch to new activity... be careful not to start own activity or we can get stuck in a loop
        switch (position){
            case DRAWER_DECK_PICKER:
                if (!(this instanceof DeckPicker)) {
                    Intent deckPicker = new Intent(this, DeckPicker.class);
                    deckPicker.putExtra("viaNavigationDrawer", true);
                    deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);    // opening DeckPicker should clear back history
                    startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.LEFT);
                }
                break;
            case DRAWER_BROWSER:
                Intent cardBrowser = new Intent(this, CardBrowser.class);
                if (!(this instanceof CardBrowser)) {
                    if (this instanceof DeckPicker && !mFragmented){
                        cardBrowser.putExtra("fromDeckpicker", true);
                    }                    
                    startActivityForResultWithAnimation(cardBrowser, BROWSE_CARDS, ActivityTransitionAnimation.LEFT);
                }
                break;
            case DRAWER_STATISTICS:
            	boolean selectAllDecksButton = false;
            	if ((this instanceof DeckPicker && !mFragmented)) {
            		selectAllDecksButton = true;
            	}
                Dialog dialog = ChartBuilder.getStatisticsDialog(this, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DeckTask.launchDeckTask(
                                DeckTask.TASK_TYPE_LOAD_STATISTICS,
                                mLoadStatisticsHandler,
                                new DeckTask.TaskData(AnkiDroidApp.getCol(), which, AnkiDroidApp
                                        .getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getBoolean(
                                                "statsRange", true)));
                    }
                }, selectAllDecksButton);
                dialog.show();
                break;
            case DRAWER_SETTINGS:
                startActivityForResultWithAnimation(new Intent(this, Preferences.class), PREFERENCES_UPDATE, ActivityTransitionAnimation.LEFT);
                break;
            
            case DRAWER_HELP:
                Intent helpIntent = new Intent("android.intent.action.VIEW", Uri.parse(getResources().getString(R.string.link_faq)));
                startActivityWithoutAnimation(helpIntent);
                break;
                
            case DRAWER_FEEDBACK:
                Intent feedbackIntent = new Intent("android.intent.action.VIEW", Uri.parse(getResources().getString(R.string.link_help)));
                startActivityWithoutAnimation(feedbackIntent);
                break;
            
            default:
                break;
        }
        
        // update selected item and title, then close the drawer
        mDrawerList.setItemChecked(position, true);
        setTitle(mNavigationTitles[position]);
        mDrawerLayout.closeDrawer(mDrawerList);
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

        public NavDrawerListAdapter(Context context, String[] navDrawerTitles, TypedArray navDrawerImages){
            this.context = context;
            this.navDrawerTitles = navDrawerTitles;
            this.navDrawerImages = navDrawerImages;
        }

        @Override
        public int getCount() {
            return navDrawerTitles.length;
        }

        @Override
        public Object getItem(int position) {       
            return navDrawerTitles[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater mInflater = (LayoutInflater)
                        context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                if (position > DRAWER_STATISTICS){
                    convertView = mInflater.inflate(R.layout.drawer_submenu_list_item, null);
                } else {
                    convertView = mInflater.inflate(R.layout.drawer_list_item, null);
                }
            }
            // set the text and image according to lists specified in resources
            TextView txtTitle = (TextView) convertView.findViewById(R.id.drawer_list_item_text);
            txtTitle.setText(navDrawerTitles[position]);
            txtTitle.setCompoundDrawablesWithIntrinsicBounds(navDrawerImages.getResourceId(position, -1), 0, 0, 0);
            // make current item bold
            if (NavigationDrawerActivity.this.mDrawerList.getCheckedItemPosition()==position) {
                txtTitle.setTypeface(null, Typeface.BOLD);
            } else {
                txtTitle.setTypeface(null, Typeface.NORMAL);
            }
            return convertView;
        }
    }
    
    /* Members not related directly to navigation drawer */
    
    // Statistics
    DeckTask.TaskListener mLoadStatisticsHandler = new DeckTask.TaskListener() {
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
            }
            if (result.getBoolean()) {
                // if (mStatisticType == Statistics.TYPE_DECK_SUMMARY) {
                // Statistics.showDeckSummary(DeckPicker.this);
                // } else {
                Intent intent = new Intent(NavigationDrawerActivity.this, com.ichi2.charts.ChartBuilder.class);
                startActivityWithAnimation(intent, ActivityTransitionAnimation.DOWN);
                // }
            } else {
                // TODO: db error handling
            }
        }


        @Override
        public void onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(NavigationDrawerActivity.this, "",
                    getResources().getString(R.string.calculating_statistics), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onCancelled() {
            // TODO Auto-generated method stub
            
        }

    };
    
    @Override
    protected void onDestroy() {       
        super.onDestroy();
        mNavigationImages.recycle();
    }
}
