/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2015 Tim Rae <perceptualchaos2@gmail.com>                              *
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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.VersionUtils;

import org.acra.util.Installation;

import timber.log.Timber;

/**
 * Shows an about box, which is a small HTML page.
 */

public class Info extends AnkiActivity {

    public static final String TYPE_EXTRA = "infoType";

    public static final int TYPE_ABOUT = 0;
    public static final int TYPE_NEW_VERSION = 2;

    private int mType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        Resources res = getResources();
        WebView webView;

        mType = getIntent().getIntExtra(TYPE_EXTRA, TYPE_ABOUT);

        setContentView(R.layout.info);
        final View mainView = findViewById(android.R.id.content);
        Toolbar toolbar = (Toolbar) mainView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        setTitle(String.format("%s v%s", VersionUtils.getAppName(), VersionUtils.getPkgVersionName()));
        webView = (WebView) findViewById(R.id.info);
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                // Hide the progress indicator when the page has finished loaded
                if (progress == 100) {
                    mainView.findViewById(R.id.progress_bar).setVisibility(View.GONE);
                }
            }
        });

        Button marketButton = (Button) findViewById(R.id.market);
        marketButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mType == TYPE_ABOUT) {
                    if (CompatHelper.isKindle()) {
                        Intent intent = new Intent("android.intent.action.VIEW",
                                Uri.parse("http://www.amazon.com/gp/mas/dl/android?p=com.ichi2.anki"));
                        startActivity(intent);
                    } else {
                        Info.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri
                                .parse("market://details?id=com.ichi2.anki")));
                    }
                    return;
                }
                setResult(RESULT_OK);
                switch (mType) {
                    case TYPE_NEW_VERSION:
                        AnkiDroidApp.getSharedPrefs(Info.this.getBaseContext()).edit()
                                .putString("lastVersion", VersionUtils.getPkgVersionName()).commit();
                        break;
                }
                finishWithAnimation();
            }
        });

        StringBuilder sb = new StringBuilder();
        switch (mType) {
            case TYPE_ABOUT:
                String[] content = res.getStringArray(R.array.about_content);

                // Apply theme colours.
                TypedValue typedValue = new TypedValue();
                getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
                webView.setBackgroundColor(typedValue.data);
                getTheme().resolveAttribute(android.R.attr.textColor, typedValue, true);
                String textColor = String.format("#%06X", (0xFFFFFF & typedValue.data)); // Color to hex string
                sb.append("<html><style>body {color:"+textColor+";}</style>");

                sb.append("<body text=\"#000000\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">");
                sb.append(
                        String.format(content[0], res.getString(R.string.app_name), res.getString(R.string.link_anki)))
                        .append("<br/><br/>");
                sb.append(
                        String.format(content[1], res.getString(R.string.link_issue_tracker),
                                res.getString(R.string.link_wiki), res.getString(R.string.link_forum))).append(
                        "<br/><br/>");
                sb.append(
                        String.format(content[2], res.getString(R.string.link_wikipedia_open_source),
                                res.getString(R.string.link_contribution))).append(" ");
                sb.append(
                        String.format(content[3], res.getString(R.string.link_translation),
                                res.getString(R.string.link_donation))).append("<br/><br/>");
                sb.append(
                        String.format(content[4], res.getString(R.string.licence_wiki),
                                res.getString(R.string.link_source))).append("<br/><br/>");
                sb.append("</body></html>");
                webView.loadDataWithBaseURL("", sb.toString(), "text/html", "utf-8", null);
                ((Button) findViewById(R.id.market)).setText(res.getString(R.string.info_rate));
                Button debugCopy = ((Button) findViewById(R.id.debug_info));
                debugCopy.setText(res.getString(R.string.feedback_copy_debug));
                debugCopy.setVisibility(View.VISIBLE);
                debugCopy.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        copyDebugInfo();
                    }
                });
                break;

            case TYPE_NEW_VERSION:
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                webView.loadUrl("file:///android_asset/changelog.html");
                //webView.loadUrl("https://ankidroid.org/docs/changelog.html");
                break;

            default:
                finish();
                break;
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("onBackPressed()");
            setResult(RESULT_CANCELED);
            finishWithAnimation();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void finishWithAnimation() {
        finishWithAnimation(ActivityTransitionAnimation.LEFT);
    }


    /**
     * Copy debug information about the device to the clipboard
     * @return debugInfo
     */
    public String copyDebugInfo() {
        StringBuilder sb = new StringBuilder();
        // AnkiDroid Version
        sb.append("AnkiDroid Version = ").append(VersionUtils.getPkgVersionName()).append("\n\n");
        // Android SDK
        sb.append("Android Version = " + Build.VERSION.RELEASE).append("\n\n");
        // ACRA install ID
        sb.append("ACRA UUID = ").append(Installation.id(this)).append("\n");
        String debugInfo = sb.toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setText(debugInfo);
        return debugInfo;
    }
}
