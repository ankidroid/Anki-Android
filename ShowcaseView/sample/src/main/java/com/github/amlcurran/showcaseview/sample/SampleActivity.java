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

package com.github.amlcurran.showcaseview.sample;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.amlcurran.showcaseview.ApiUtils;
import com.github.amlcurran.showcaseview.OnShowcaseEventListener;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.sample.animations.AnimationSampleActivity;
import com.github.amlcurran.showcaseview.sample.v14.ActionItemsSampleActivity;
import com.github.amlcurran.showcaseview.targets.ViewTarget;

public class SampleActivity extends Activity implements View.OnClickListener,
        OnShowcaseEventListener, AdapterView.OnItemClickListener {

    private static final float ALPHA_DIM_VALUE = 0.1f;

    ShowcaseView sv;
    Button buttonBlocked;
    ListView listView;

    private final ApiUtils apiUtils = new ApiUtils();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        HardcodedListAdapter adapter = new HardcodedListAdapter(this);
        listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);

        buttonBlocked = (Button) findViewById(R.id.buttonBlocked);
        buttonBlocked.setOnClickListener(this);

        RelativeLayout.LayoutParams lps = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lps.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lps.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        int margin = ((Number) (getResources().getDisplayMetrics().density * 12)).intValue();
        lps.setMargins(margin, margin, margin, margin);

        ViewTarget target = new ViewTarget(R.id.buttonBlocked, this);
        sv = new ShowcaseView.Builder(this, true)
                .setTarget(target)
                .setContentTitle(R.string.showcase_main_title)
                .setContentText(R.string.showcase_main_message)
                .setStyle(R.style.CustomShowcaseTheme2)
                .setShowcaseEventListener(this)
                .build();
        sv.setButtonPosition(lps);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void dimView(View view) {
        if (apiUtils.isCompatWithHoneycomb()) {
            view.setAlpha(ALPHA_DIM_VALUE);
        }
    }

    @Override
    public void onClick(View view) {

        int viewId = view.getId();
        switch (viewId) {
            case R.id.buttonBlocked:
                if (sv.isShown()) {
                    sv.setStyle(R.style.CustomShowcaseTheme);
                } else {
                    sv.show();
                }
                break;
        }
    }

    @Override
    public void onShowcaseViewHide(ShowcaseView showcaseView) {
        if (apiUtils.isCompatWithHoneycomb()) {
            listView.setAlpha(1f);
        }
        buttonBlocked.setText(R.string.button_show);
        //buttonBlocked.setEnabled(false);
    }

    @Override
    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
    }

    @Override
    public void onShowcaseViewShow(ShowcaseView showcaseView) {
        dimView(listView);
        buttonBlocked.setText(R.string.button_hide);
        //buttonBlocked.setEnabled(true);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        switch (position) {

            case 0:
                startActivity(new Intent(this, ActionItemsSampleActivity.class));
                break;

            case 1:
                startActivity(new Intent(this, AnimationSampleActivity.class));
                break;

            case 2:
                startActivity(new Intent(this, SingleShotActivity.class));
                break;

            // Not currently used
            case 3:
                startActivity(new Intent(this, MemoryManagementTesting.class));
        }
    }

    private static class HardcodedListAdapter extends ArrayAdapter {

        private static final int[] TITLE_RES_IDS = new int[] {
                R.string.title_action_items,
                R.string.title_animations,
                R.string.title_single_shot//, R.string.title_memory
        };

        private static final int[] SUMMARY_RES_IDS = new int[] {
                R.string.sum_action_items,
                R.string.sum_animations,
                R.string.sum_single_shot//, R.string.sum_memory
        };

        public HardcodedListAdapter(Context context) {
            super(context, R.layout.item_next_thing);
        }

        @Override
        public int getCount() {
            return TITLE_RES_IDS.length;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_next_thing, parent, false);
            }
            ((TextView) convertView.findViewById(R.id.textView)).setText(TITLE_RES_IDS[position]);
            ((TextView) convertView.findViewById(R.id.textView2)).setText(SUMMARY_RES_IDS[position]);
            return convertView;
        }
    }

}
