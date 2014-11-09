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

package com.github.amlcurran.showcaseview.sample.legacy;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.github.amlcurran.showcaseview.sample.R;

public class MultipleShowcaseSampleActivity extends Activity {

    private static final float SHOWCASE_KITTEN_SCALE = 1.2f;
    private static final float SHOWCASE_LIKE_SCALE = 0.5f;
    //ShowcaseViews mViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_legacy);

        findViewById(R.id.buttonLike).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), R.string.like_message, Toast.LENGTH_SHORT).show();
            }
        });

        //mOptions.block = false;
//        mViews = new ShowcaseViews(this,
//                new ShowcaseViews.OnShowcaseAcknowledged() {
//            @Override
//            public void onShowCaseAcknowledged(ShowcaseView showcaseView) {
//                Toast.makeText(MultipleShowcaseSampleActivity.this, R.string.dismissed_message, Toast.LENGTH_SHORT).show();
//            }
//        });
//        mViews.addView( new ShowcaseViews.ItemViewProperties(R.id.image,
//                R.string.showcase_image_title,
//                R.string.showcase_image_message,
//                SHOWCASE_KITTEN_SCALE));
//        mViews.addView( new ShowcaseViews.ItemViewProperties(R.id.buttonLike,
//                R.string.showcase_like_title,
//                R.string.showcase_like_message,
//                SHOWCASE_LIKE_SCALE));
//        mViews.show();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            enableUp();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void enableUp() {
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }
}
