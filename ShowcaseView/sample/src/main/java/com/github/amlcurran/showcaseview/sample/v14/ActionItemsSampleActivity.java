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

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.sample.R;
import com.github.amlcurran.showcaseview.targets.ActionItemTarget;
import com.github.amlcurran.showcaseview.targets.ActionViewTarget;

public class ActionItemsSampleActivity extends SherlockActivity {

    ShowcaseView sv;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.menu, menu);

        ActionViewTarget target = new ActionViewTarget(this, ActionViewTarget.Type.OVERFLOW);
        sv = new ShowcaseView.Builder(this)
                .setTarget(target)
                .setContentTitle(R.string.showcase_simple_title)
                .setContentText(R.string.showcase_simple_message)
                .doNotBlockTouches()
                .build();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            ActionViewTarget target = new ActionViewTarget(this, ActionViewTarget.Type.HOME);
            sv.setShowcase(target, true);
        }
        else if (itemId == R.id.menu_item1) {
            ActionItemTarget target = new ActionItemTarget(this, R.id.menu_item1);
            sv.setShowcase(target, true);
        }
        else if (itemId == R.id.menu_item2) {
            ActionViewTarget target = new ActionViewTarget(this, ActionViewTarget.Type.TITLE);
            sv.setShowcase(target, true);
        }
        return true;
    }

}
