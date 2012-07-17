/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

import java.io.File;
import java.util.ArrayList;

import com.ichi2.anki.R;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.OldAnkiDeckFilter;
import com.ichi2.async.Connection.Payload;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

/**
 * Shows an about box, which is a small HTML page.
 */

public class Info extends Activity {

    public static final String TYPE_EXTRA = "infoType";

    public static final int TYPE_ABOUT = 0;
    public static final int TYPE_WELCOME = 1;
    public static final int TYPE_NEW_VERSION = 2;
    public static final int TYPE_SHARED_DECKS = 3;
    public static final int TYPE_UPGRADE_DECKS = 4;

    private int mType;
    private WebView mWebView;
    private StyledProgressDialog mProgressDialog;
    private StyledDialog mNoConnectionAlert;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(AnkiDroidApp.TAG, "Info - onCreate()");
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        Resources res = getResources();

        mType = getIntent().getIntExtra(TYPE_EXTRA, TYPE_ABOUT);

        setTitle(getTitleString());

        setContentView(R.layout.info);

        mWebView = (WebView) findViewById(R.id.info);
        mWebView.setBackgroundColor(res.getColor(Themes.getBackgroundColor()));
        Themes.setWallpaper((View) mWebView.getParent());

        Button continueButton = (Button) findViewById(R.id.info_continue);
        continueButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
            	if (mType == TYPE_ABOUT) {
            		Info.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ichi2.anki")));
            		return;
            	}
                setResult(RESULT_OK);
                switch (mType) {
                    case TYPE_WELCOME:
                        AnkiDroidApp.getSharedPrefs(Info.this.getBaseContext()).edit()
                                .putLong("lastTimeOpened", System.currentTimeMillis()).commit();
                        break;
                    case TYPE_NEW_VERSION:
                        AnkiDroidApp.getSharedPrefs(Info.this.getBaseContext()).edit()
                                .putString("lastVersion", AnkiDroidApp.getPkgVersion()).commit();
                        break;
                    case TYPE_UPGRADE_DECKS:
                        break;
                }
                finish();
                if (UIUtils.getApiLevel() > 4) {
                    ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                }
            }
        });

        StringBuilder sb = new StringBuilder();
        switch (mType) {
            case TYPE_ABOUT:
                String[] content = res.getStringArray(R.array.about_content);
                sb.append("<html><body text=\"#000000\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">");
                sb.append(
                        String.format(content[0], res.getString(R.string.app_name), res.getString(R.string.link_anki)))
                        .append("<br/><br/>");
                sb.append(
                        String.format(content[1], res.getString(R.string.link_issue_tracker),
                                res.getString(R.string.link_wiki), res.getString(R.string.link_forum))).append(
                        "<br/><br/>");
                sb.append(
                        String.format(content[2], res.getString(R.string.link_wikipedia_open_source),
                                res.getString(R.string.link_contribution),
                                res.getString(R.string.link_contribution_contributors))).append(" ");
                sb.append(
                        String.format(content[3], res.getString(R.string.link_translation),
                                res.getString(R.string.link_donation))).append("<br/><br/>");
                sb.append(
                        String.format(content[4], res.getString(R.string.licence_wiki),
                                res.getString(R.string.link_source))).append("<br/><br/>");
                sb.append("</body></html>");
                mWebView.loadDataWithBaseURL("", sb.toString(), "text/html", "utf-8", null);
                ((Button) findViewById(R.id.info_continue)).setText(res.getString(R.string.info_rate));
                break;

            case TYPE_WELCOME:
                // title
                sb.append("<html><body text=\"#000000\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">");
                sb.append("<big><b>");
                sb.append(res.getString(R.string.studyoptions_welcome_title));
                sb.append("</big></b><br><br>");
                // message
                sb.append(res.getString(R.string.welcome_message).replace("\n", "<br>"));
                sb.append("</body></html>");
                mWebView.loadDataWithBaseURL("", sb.toString(), "text/html", "utf-8", null);

                // add tutorial button
                Button tutBut = (Button) findViewById(R.id.info_tutorial);
                tutBut.setVisibility(View.VISIBLE);
                tutBut.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        setResult(RESULT_OK);
                        Editor edit = AnkiDroidApp.getSharedPrefs(Info.this.getBaseContext()).edit();
                        edit.putLong("lastTimeOpened", System.currentTimeMillis());
                        edit.putBoolean("createTutorial", true);
                        edit.commit();
                        finish();
                        if (UIUtils.getApiLevel() > 4) {
                            ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                        }
                    }
                });
                break;

            case TYPE_NEW_VERSION:
                sb.append(res.getString(R.string.new_version_message));
                sb.append("<ul>");
                String[] features = res.getStringArray(R.array.new_version_features);
                for (int i = 0; i < features.length; i++) {
                    sb.append("<li>");
                    sb.append(features[i]);
                    sb.append("</li>");
                }
                sb.append("</ul>");
                sb.append("</body></html>");
                mWebView.loadDataWithBaseURL("", sb.toString(), "text/html", "utf-8", null);

                // reactivating ssl check for every new version
                AnkiDroidApp.getSharedPrefs(Info.this.getBaseContext()).edit().putBoolean("sslAcceptAll", false)
                        .commit();
                break;

            case TYPE_SHARED_DECKS:
                mWebView.loadUrl(res.getString(R.string.shared_decks_url));
                mWebView.setWebViewClient(new CustomWebViewClient());
                mWebView.getSettings().setJavaScriptEnabled(true);
                continueButton.setText(res.getString(R.string.download_button_return));

                // this is only shown until ankiweb gives a possibility of logging in by hkey
                StyledDialog.Builder builder = new StyledDialog.Builder(this);
                builder.setMessage("At the moment, it's still necessary to log in manually.\nPlease log in here and choose then your deck");
                builder.setPositiveButton("ok", null);
                builder.show();
                break;

            case TYPE_UPGRADE_DECKS:
                sb.append("<html><body>");
                File[] fileList = (new File(AnkiDroidApp.getDefaultAnkiDroidDirectory())).listFiles(new OldAnkiDeckFilter());
                StringBuilder fsb = new StringBuilder();
                fsb.append("<ul>");
                for (File f : fileList) {
                	fsb.append("<li>").append(f.getName().replace(".anki", "")).append("</li>");
                }
            	fsb.append("</ul>");
                sb.append(res.getString(R.string.upgrade_decks_message, fsb.toString()));
                sb.append("<ul><li>");
                sb.append(res.getString(R.string.upgrade_decks_message_pos1,
                        AnkiDroidApp.getDefaultAnkiDroidDirectory()));
                sb.append("</li><li>");
                sb.append(res.getString(R.string.upgrade_decks_message_pos2, res.getString(R.string.link_anki)));
                sb.append("</li><li>");
                sb.append(res.getString(R.string.upgrade_decks_message_pos3));
                sb.append("</li></ul>");
                sb.append(res.getString(R.string.upgrade_decks_message_finish));
                sb.append("</body></html>");
                mWebView.loadDataWithBaseURL("", sb.toString(), "text/html", "utf-8", null);
                // add upgrade button
                Button but = (Button) findViewById(R.id.info_tutorial);
                but.setVisibility(View.VISIBLE);
                but.setText(res.getString(R.string.upgrade_decks_button));
                but.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        Connection.upgradeDecks(mUpgradeListener,
                                new Connection.Payload(new Object[] { AnkiDroidApp.getDefaultAnkiDroidDirectory() }));
                    }
                });
                StyledDialog.Builder builder2 = new StyledDialog.Builder(this);

                builder2.setTitle(res.getString(R.string.connection_error_title));
                builder2.setIcon(R.drawable.ic_dialog_alert);
                builder2.setMessage(res.getString(R.string.connection_needed));
                builder2.setPositiveButton(res.getString(R.string.ok), null);
                mNoConnectionAlert = builder2.create();
                break;

            default:
                finish();
                break;
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (mType == TYPE_SHARED_DECKS && mWebView.canGoBack()) {
                mWebView.goBack();
            } else {
                Log.i(AnkiDroidApp.TAG, "Info - onBackPressed()");
                setResult(RESULT_CANCELED);
                finish();
                if (UIUtils.getApiLevel() > 4) {
                    ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private String getTitleString() {
        StringBuilder appName = new StringBuilder();
        appName.append(AnkiDroidApp.getPkgName());
        appName.append(" v");
        appName.append(AnkiDroidApp.getPkgVersion());
        return appName.toString();
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.i(AnkiDroidApp.TAG, "LoadSharedDecks: loading: " + url);
            view.loadUrl(url);
            return true;
        }
    }

    Connection.TaskListener mUpgradeListener = new Connection.TaskListener() {

        @Override
        public void onProgressUpdate(Object... values) {
            int id = (Integer) values[0];
            if (values.length > 1) {
                mProgressDialog.setMessage(getResources().getString(id, (String) values[1]));
            } else {
                mProgressDialog.setMessage(getResources().getString(id));
            }
        }


        @Override
        public void onPreExecute() {
            Log.i(AnkiDroidApp.TAG, "Info: UpgradeDecks - onPreExcecute");
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(Info.this, "",
                        getResources().getString(R.string.upgrade_decks_zipping), true);
            }
        }


        @Override
        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "Info: UpgradeDecks - onPostExecute, succes = " + data.success);
        	Resources res = getResources();
            try {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }            	
            } catch (IllegalArgumentException e) {
            	Log.e(AnkiDroidApp.TAG, "Info - IllegalArgumentException: " + e);
            }

            if (data.success) {
            	ArrayList<String> failed = (ArrayList<String>) data.data[0];
            	if (failed.size() == 0) {
            		setResult(RESULT_OK);
                    finish();
                    if (UIUtils.getApiLevel() > 4) {
                        ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                    }
            	} else {
                    StyledDialog.Builder builder = new StyledDialog.Builder(Info.this);
                    builder.setTitle(res.getString(R.string.connection_error_title));
                    builder.setIcon(R.drawable.ic_dialog_alert);
                    StringBuilder sbb = new StringBuilder();
                    for (String s : failed) {
                    	sbb.append(" \u2022 ").append(s).append("\n");
                    }
                    builder.setMessage(res.getString(R.string.upgrade_decks_failed, sbb.toString()));
                    builder.setPositiveButton(res.getString(R.string.ok), new Dialog.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
		            		setResult(RESULT_OK);
		                    finish();
		                    if (UIUtils.getApiLevel() > 4) {
		                        ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
		                    }
						}});
                    builder.setCancelable(false);
                    builder.show();
            	}
            } else {
                StyledDialog.Builder builder = new StyledDialog.Builder(Info.this);
                builder.setTitle(res.getString(R.string.connection_error_title));
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setMessage((String) data.data[0]);
                builder.setPositiveButton(res.getString(R.string.ok), null);
                builder.show();
            }
        }


        @Override
        public void onDisconnected() {
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }
    };
}
