/***************************************************************************************
 *                                                                                      *
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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.example.android.common.view.SlidingTabLayout;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.Themes;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * Allows the user to view the template for the current note type
 */
public class CardTemplateEditor extends AnkiActivity {
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private JSONObject mModel;
    private long mCardId;

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(AnkiDroidApp.TAG, "CardTemplateEditor:: onCreate");
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_template_editor_activity);
        // get model
        try {
            mModel = new JSONObject(getIntent().getStringExtra("noteType"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // get id for currently edited card (optional)
        mCardId = getIntent().getLongExtra("cardId", -1L);

        startLoadingCollection();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(getResources().getString(R.string.title_activity_template_editor, mModel.optString("name")));
    }


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mSectionsPagerAdapter.setModel(mModel);
        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mSlidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);

        // Close collection opening dialog if needed
        dismissOpeningCollectionDialog();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                //NavUtils.navigateUpFromSameTask(this);
                finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                return true;

            case R.id.action_preview:
                //openReviewer();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    private void openReviewer() {
        Intent reviewer = new Intent(CardTemplateEditor.this, Previewer.class);
        //reviewer.putExtra("currentCardId", mCurrentEditedCard.getId());
        startActivityWithoutAnimation(reviewer);
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * A {@link android.support.v4.app.FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        JSONObject mModel;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        //this is called when mSectionsPagerAdapter.notifyDataSetChanged() is called, so checkAndUpdate() here
        //works best for updating all tabs
        @Override
        public int getItemPosition(Object object) {

            //don't return POSITION_NONE, avoid fragment recreation.
            return super.getItemPosition(object);
        }

        @Override
        public Fragment getItem(int position) {
            return CardTemplateFragment.newInstance(position, mModel);
        }

        @Override
        public int getCount() {
            try {
                return mModel.getJSONArray("tmpls").length();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            try {
                return mModel.getJSONArray("tmpls").getJSONObject(position).getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public void setModel(JSONObject model) {
            mModel = model;
        }
    }


    public static class CardTemplateFragment extends Fragment{
        public static CardTemplateFragment newInstance(int position, JSONObject model) {
            CardTemplateFragment f = new CardTemplateFragment();
            Bundle args = new Bundle();
            args.putInt("position", position);
            args.putString("model", model.toString());
            f.setArguments(args);
            return f;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View mainView = inflater.inflate(R.layout.card_template_editor_item, container, false);
            int position = getArguments().getInt("position");
            try {
                JSONObject model = new JSONObject(getArguments().getString("model"));
                JSONObject template = model.getJSONArray("tmpls").getJSONObject(position);
                ((EditText) mainView.findViewById(R.id.front_edit)).setText(template.getString("qfmt"));
                ((EditText) mainView.findViewById(R.id.styling_edit)).setText(model.getString("css"));
                ((EditText) mainView.findViewById(R.id.back_edit)).setText(template.getString("afmt"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return mainView;
        }

        @Override
        public void onResume() {
            super.onResume();
        }
    }
}
