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

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import com.ichi2.utils.IntentUtil;
import com.ichi2.utils.VersionUtils;

import org.acra.util.Installation;

import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.LEFT;

/**
 * Shows an about box, which is a small HTML page.
 */

public class Info extends AnkiActivity {

    public static final String TYPE_EXTRA = "infoType";

    public static final int TYPE_ABOUT = 0;
    public static final int TYPE_NEW_VERSION = 2;


    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        Resources res = getResources();

        int mType = getIntent().getIntExtra(TYPE_EXTRA, TYPE_ABOUT);
        // If the page crashes, we do not want to display it again (#7135 maybe)
        if (mType == TYPE_NEW_VERSION) {
            AnkiDroidApp.getSharedPrefs(Info.this.getBaseContext()).edit()
                    .putString("lastVersion", VersionUtils.getPkgVersionName()).apply();
        }

        setContentView(R.layout.info);
        final View mainView = findViewById(android.R.id.content);
        enableToolbar(mainView);
        findViewById(R.id.info_donate).setOnClickListener((v) -> openUrl(Uri.parse(getString(R.string.link_opencollective_donate))));

        setTitle(String.format("%s v%s", VersionUtils.getAppName(), VersionUtils.getPkgVersionName()));
        WebView webView = findViewById(R.id.info);
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                // Hide the progress indicator when the page has finished loaded
                if (progress == 100) {
                    mainView.findViewById(R.id.progress_bar).setVisibility(View.GONE);
                }
            }
        });

        Button marketButton = findViewById(R.id.left_button);
        if (canOpenMarketUri()) {
            marketButton.setText(R.string.info_rate);
            marketButton.setOnClickListener(arg0 -> IntentUtil.tryOpenIntent(this, AnkiDroidApp.getMarketIntent(this)));
        } else {
            marketButton.setVisibility(View.GONE);
        }

        StringBuilder sb = new StringBuilder();

        // Apply Theme colors
        TypedArray typedArray = getTheme().obtainStyledAttributes(new int[] {android.R.attr.colorBackground, android.R.attr.textColor});
        int backgroundColor = typedArray.getColor(0, -1);
        String textColor = String.format("#%06X", (0xFFFFFF & typedArray.getColor(1, -1))); // Color to hex string
        webView.setBackgroundColor(backgroundColor);

        switch (mType) {
            case TYPE_ABOUT: {
                String[] content = res.getStringArray(R.array.about_content);
                sb.append("<html><style>body {color:").append(textColor).append(";}</style>");

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
                Button debugCopy = (findViewById(R.id.right_button));
                debugCopy.setText(res.getString(R.string.feedback_copy_debug));
                debugCopy.setOnClickListener(v -> copyDebugInfo());
                break;
            }
            case TYPE_NEW_VERSION: {
                Button continueButton = (findViewById(R.id.right_button));
                continueButton.setText(res.getString(R.string.dialog_continue));
                continueButton.setOnClickListener((arg) -> close());
                String background = String.format("#%06X", (0xFFFFFF & backgroundColor));
                webView.loadUrl("file:///android_asset/changelog.html");
                webView.getSettings().setJavaScriptEnabled(true);

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {

                        /* The order of below javascript code must not change (this order works both in debug and release mode)
                         *  or else it will break in any one mode.
                         */
                        webView.loadUrl(
                                "javascript:document.body.style.setProperty(\"color\", \"" + textColor + "\");"
                                        + "x=document.getElementsByTagName(\"a\"); for(i=0;i<x.length;i++){x[i].style.color=\"#E37068\";}"
                                        + "document.getElementsByTagName(\"h1\")[0].style.color=\"" + textColor + "\";"
                                        + "x=document.getElementsByTagName(\"h2\"); for(i=0;i<x.length;i++){x[i].style.color=\"#E37068\";}"
                                        + "document.body.style.setProperty(\"background\", \"" + background + "\");"
                        );
                    }
                });
            }
            break;
            default:
                finishWithoutAnimation();
                break;
        }
    }


    private void close() {
        setResult(RESULT_OK);
        finishWithAnimation();
    }


    private boolean canOpenMarketUri() {
        try {
            return IntentUtil.canOpenIntent(this, AnkiDroidApp.getMarketIntent(this));
        } catch (Exception e) {
            Timber.w(e);
            return false;
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
        finishWithAnimation(LEFT);
    }


    /**
     * Copy debug information about the device to the clipboard
     * @return debugInfo
     */
    public String copyDebugInfo() {
        String schedName = "Not found";
        try {
            schedName = getCol().getSched().getName();
        } catch (Throwable e) {
            Timber.e(e, "Sched name not found");
        }

        Boolean dbV2Enabled = null;
        try {
            dbV2Enabled = getCol().isUsingRustBackend();
        } catch (Throwable e) {
            Timber.w(e, "Unable to detect Rust Backend");
        }


        String debugInfo = "AnkiDroid Version = " + VersionUtils.getPkgVersionName() + "\n\n" +
                "Android Version = " + Build.VERSION.RELEASE + "\n\n" +
                "ACRA UUID = " + Installation.id(this) + "\n\n" +
                "Scheduler = " + schedName + "\n\n" +
                "Crash Reports Enabled = " + isSendingCrashReports() + "\n\n" +
                "DatabaseV2 Enabled = " + dbV2Enabled + "\n";

        android.content.ClipboardManager clipboardManager = (android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(this.getTitle(), debugInfo));
            UIUtils.showThemedToast(this, getString(R.string.about_ankidroid_successfully_copied_debug), true);
        } else {
            UIUtils.showThemedToast(this, getString(R.string.about_ankidroid_error_copy_debug_info), false);
        }
        return debugInfo;
    }


    private boolean isSendingCrashReports() {
        return AnkiDroidApp.isAcraEnbled(this, false);
    }
}
