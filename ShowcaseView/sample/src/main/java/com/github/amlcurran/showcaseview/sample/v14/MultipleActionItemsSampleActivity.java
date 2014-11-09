/*
 * Copyright 2014 Alex Curran
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.amlcurran.showcaseview.sample.v14;

import android.app.ActionBar;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.github.amlcurran.showcaseview.sample.R;

public class MultipleActionItemsSampleActivity extends SherlockActivity implements com.actionbarsherlock.app.ActionBar.OnNavigationListener {

    public static final float SHOWCASE_SPINNER_SCALE = 1f;
    public static final float SHOWCASE_OVERFLOW_ITEM_SCALE = 0.5f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        getSupportActionBar().setListNavigationCallbacks(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Item1", "Item2", "Item3"}), this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.menu, menu);

        //mOptions.block = false;
//        ShowcaseViews views = new ShowcaseViews(this, new ShowcaseViews.OnShowcaseAcknowledged() {
//            @Override
//            public void onShowCaseAcknowledged(ShowcaseView showcaseView) {
//                 Toast.makeText(getApplicationContext(), R.string.dismissed_message, Toast.LENGTH_SHORT).show();
//            }
//        });
//        ShowcaseView.ConfigOptions options = new ShowcaseView.ConfigOptions();
//        options.shotType = ShowcaseView.TYPE_ONE_SHOT;
//        options.showcaseId = 1234;
//        views.addView(new ItemViewProperties(R.id.menu_item1, R.string.showcase_menu_item_one_shot_title, R.string.showcase_menu_item_one_shot_message, ShowcaseView.ITEM_SPINNER, SHOWCASE_SPINNER_SCALE, options));
//        ShowcaseView.ConfigOptions configOptions = new ShowcaseView.ConfigOptions();
//        configOptions.fadeInDuration = 700;
//        configOptions.fadeOutDuration = 700;
//        configOptions.block = true;
//        views.addView(new ItemViewProperties(ItemViewProperties.ID_SPINNER, R.string.showcase_spinner_title, R.string.showcase_spinner_message, ShowcaseView.ITEM_SPINNER, SHOWCASE_SPINNER_SCALE, configOptions));
//        views.addView(new ItemViewProperties(ItemViewProperties.ID_OVERFLOW, R.string.showcase_overflow_title, R.string.showcase_overflow_message, ShowcaseView.ITEM_ACTION_OVERFLOW, SHOWCASE_OVERFLOW_ITEM_SCALE));
//        views.show();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(int i, long l) {
        return false;
    }
}
