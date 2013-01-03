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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.RelativeLayout;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.OldAnkiDeckFilter;
import com.ichi2.async.Connection.Payload;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import org.apache.commons.httpclient.contrib.ssl.EasySSLSocketFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.*;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final int DIALOG_USER_NOT_LOGGED_IN_SYNC = 0;
    private static final int DIALOG_SYNC_LOG = 1;
    private static final int DIALOG_SYNC_UPGRADE_REQUIRED = 2;

    private static final int LOG_IN_FOR_SYNC = 0;

    private String mDialogMessage;

    private int mType;
    private WebView mWebView;
    private RelativeLayout mLoadingLayer;
    private String mShareDecksTemplate;
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
                if (AnkiDroidApp.SDK_VERSION > 4) {
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
                        if (AnkiDroidApp.SDK_VERSION > 4) {
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
            	mLoadingLayer = (RelativeLayout) findViewById(R.id.info_loading_layer);
                mLoadingLayer.setVisibility(View.VISIBLE);
    			try {
    				mShareDecksTemplate = Utils.convertStreamToString(getAssets().open("shared_decks_template.html"));
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
                mWebView.setWebViewClient(new MobileAnkiWebview());
                mWebView.loadUrl(res.getString(R.string.shared_decks_url));
                mWebView.getSettings().setJavaScriptEnabled(true);
                continueButton.setText(res.getString(R.string.download_button_return));
                break;

            case TYPE_UPGRADE_DECKS:
                sb.append("<html><body>");
                File[] fileList = (new File(AnkiDroidApp.getCurrentAnkiDroidDirectory())).listFiles(new OldAnkiDeckFilter());
                StringBuilder fsb = new StringBuilder();
                fsb.append("<ul>");
                for (File f : fileList) {
                	fsb.append("<li>").append(f.getName().replace(".anki", "")).append("</li>");
                }
            	fsb.append("</ul>");
                sb.append(res.getString(R.string.upgrade_decks_message, fsb.toString()));
                sb.append("<ul><li>");
                sb.append(res.getString(R.string.upgrade_decks_message_pos1,
                		AnkiDroidApp.getCurrentAnkiDroidDirectory()));
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
                        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        }
                        Connection.upgradeDecks(mUpgradeListener,
                                new Connection.Payload(new Object[] { AnkiDroidApp.getCurrentAnkiDroidDirectory() }));
                    }
                });

                // add sync button
                Button syncButton = (Button) findViewById(R.id.info_sync);
                syncButton.setVisibility(View.VISIBLE);
                syncButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                    	downloadCollection();
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
    protected Dialog onCreateDialog(int id) {
        StyledDialog dialog = null;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        switch (id) {
        case DIALOG_USER_NOT_LOGGED_IN_SYNC:
            builder.setTitle(res.getString(R.string.connection_error_title));
            builder.setIcon(R.drawable.ic_dialog_alert);
            builder.setMessage(res.getString(R.string.no_user_password_error_message));
            builder.setNegativeButton(res.getString(R.string.cancel), null);
            builder.setPositiveButton(res.getString(R.string.log_in), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent myAccount = new Intent(Info.this, MyAccount.class);
                    myAccount.putExtra("notLoggedIn", true);
                    startActivityForResult(myAccount, LOG_IN_FOR_SYNC);
                    if (AnkiDroidApp.SDK_VERSION > 4) {
                        ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.FADE);
                    }
                }
            });
            dialog = builder.create();
            break;

        case DIALOG_SYNC_LOG:
            builder.setTitle(res.getString(R.string.sync_log_title));
            builder.setPositiveButton(res.getString(R.string.ok), null);
            dialog = builder.create();
            break;

        case DIALOG_SYNC_UPGRADE_REQUIRED:
        	builder.setMessage(res.getString(R.string.upgrade_required, res.getString(R.string.link_anki)));
            builder.setPositiveButton(res.getString(R.string.retry), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                	downloadCollection();
                }
            });
            builder.setNegativeButton(res.getString(R.string.cancel), null);
            builder.setTitle(res.getString(R.string.sync_log_title));
            dialog = builder.create();
            break;
        }
        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        Resources res = getResources();
        StyledDialog ad = (StyledDialog) dialog;
        switch (id) {
    
        case DIALOG_SYNC_LOG:
        	ad.setMessage(mDialogMessage);
        	break;
        }
    }

    
    private void downloadCollection() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String hkey = preferences.getString("hkey", "");
        if (hkey.length() == 0) {
            showDialog(DIALOG_USER_NOT_LOGGED_IN_SYNC);
        } else {
            Connection.sync(mSyncListener, new Connection.Payload(new Object[] { hkey, 
                    preferences.getBoolean("syncFetchesMedia", true),
                    "download", 0 }));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == LOG_IN_FOR_SYNC && resultCode == RESULT_OK) {
        	downloadCollection();
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
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private String getTitleString() {
        StringBuilder appName = new StringBuilder();
        appName.append(AnkiDroidApp.getAppName());
        appName.append(" v");
        appName.append(AnkiDroidApp.getPkgVersion());
        return appName.toString();
    }

    private class MobileAnkiWebview extends WebViewClient {
    	WebView mWebView;
    	String mUrl;
    	
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.i(AnkiDroidApp.TAG, "LoadSharedDecks: loading: " + url);
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
        	if (mUrl != null && mUrl.equals(url)) {
        		super.onPageStarted(view, url, favicon);
        	} else if (url.matches(".*/shared/download/[0-9]*")) {
                Connection.downloadSharedDeck(mDownloadDeckListener,
                        new Connection.Payload(new Object[] { url }));
        	} else {
        		mLoadingLayer.setVisibility(View.VISIBLE);
                mWebView = view;
                mUrl = url;
                AsyncTask<String, Void, String> parseShareDecks = new ParseSharedDecks();
                parseShareDecks.execute(url);        		
        	}
        }

        private class ParseSharedDecks extends AsyncTask<String, Void, String> {
            @Override
            protected String doInBackground(String... params) {
                Log.i(AnkiDroidApp.TAG, "Info.ParseSharedDecks.doInBackground()");
                HttpGet pageGet = new HttpGet(params[0]);
                HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

                // HttpParams
                HttpParams httpParams = new BasicHttpParams();
                httpParams.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
                httpParams.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
                httpParams.setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
                httpParams.setParameter(CoreProtocolPNames.USER_AGENT, "AnkiDroid-" + AnkiDroidApp.getPkgVersion());
                HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
                HttpConnectionParams.setSoTimeout(httpParams, Connection.CONN_TIMEOUT);

                // Registry
                SchemeRegistry registry = new SchemeRegistry();
                registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
                registry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
                ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(httpParams, registry);

                ResponseHandler<String> handler = new ResponseHandler<String>() {
    				@Override
    				public String handleResponse(HttpResponse response)
    						throws ClientProtocolException, IOException {
                        HttpEntity entity = response.getEntity();
                        String html;
                        if (entity != null) {
                            html = EntityUtils.toString(entity);
                            return html;
                        } else {
                            return null;
                        }
    				}
                };

                String pageHTML = null;
                try {
                    HttpClient client = new DefaultHttpClient(cm, httpParams);
                    while (pageHTML == null){
                        pageHTML = client.execute(pageGet, handler);
                    }
                } catch (ClientProtocolException e) {
                	pageHTML = "ClientProtocolException: " + e.getMessage();
                } catch (SSLException e) {
                    pageHTML = "SSLException: " + e.getMessage();
                } catch (IOException e) {
                	pageHTML = "IOException: " + e.getMessage();
                }

                Pattern pattern = Pattern.compile("</*div(\\sid=\\\"content\\\")*");
                Matcher matcher = pattern.matcher(pageHTML);
                int start = -1;
                int end = 0;
                int inner = 0;
                while (matcher.find()) {
                	if (matcher.group().length() > 8) {
                		start = matcher.start();
                	} else if (start >= 0) {
                		if (matcher.group().equals("<div")) {
                			inner++;
                		} else {
                			if (inner == 0) {
                				end = matcher.end();
                				break;
                			} else {
                				inner--;
                			}
                		}
                	}
                }
                if (start == -1 || end <= 0) {
                	return "error";
                } else {
                	return mShareDecksTemplate.replace("::content::",  pageHTML.substring(start, end)).replaceAll(">\nDownload(.|\n)*", ">Import</a></div>");
                }
            }

            @Override
            protected void onPostExecute(String html) {
                Log.d(AnkiDroidApp.TAG, "Info.ParseSharedDecks.onPostExecute()");
                if (mWebView != null && mUrl != null & html != null) {
                    mWebView.loadDataWithBaseURL(mUrl, html, null, "utf-8", mUrl);
                    mLoadingLayer.setVisibility(View.INVISIBLE);
                }
            }
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
                ArrayList<String> failedMedia = (ArrayList<String>) data.data[1];
                String newMediaDir = (String) data.data[2];
                if (failed.size() == 0 && failedMedia.size() == 0) {
                    setResult(RESULT_OK);
                    finish();
                    if (AnkiDroidApp.SDK_VERSION > 4) {
                        ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                    }
                } else {
                    StyledDialog.Builder builder = new StyledDialog.Builder(Info.this);
                    builder.setTitle(res.getString(R.string.connection_error_title));
                    builder.setIcon(R.drawable.ic_dialog_alert);
                    String failures = "";
                    if (failed.size() > 0) {
                        StringBuilder sbb = new StringBuilder();
                        for (String s : failed) {
                            sbb.append(" \u2022 ").append(s).append("\n");
                        }
                        failures += res.getString(R.string.upgrade_decks_failed, sbb.toString());
                    }
                    if (failedMedia.size() > 0) {
                        StringBuilder sbb = new StringBuilder();
                        for (String s : failedMedia) {
                            sbb.append(" \u2022 ").append(s).append("\n");
                        }
                        if (failures.length() > 0) {
                            failures += "\n\n";
                        }
                        failures += res.getString(R.string.upgrade_decks_media_failed, newMediaDir, sbb.toString());
                    }
                    builder.setMessage(failures);
                    builder.setPositiveButton(res.getString(R.string.ok), new Dialog.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
		            		setResult(RESULT_OK);
		                    finish();
		                    if (AnkiDroidApp.SDK_VERSION > 4) {
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
    
    Connection.TaskListener mDownloadDeckListener = new Connection.TaskListener() {
    	int countDown = 0;
    	
        @Override
        public void onProgressUpdate(Object... values) {
            countDown = ((Integer) values[0]).intValue();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                // mProgressDialog.setTitle((String) values[0]);
                mProgressDialog.setMessage(getResources().getString(R.string.download_deck, countDown));
            }
        }


        @Override
        public void onPreExecute() {
            Log.i(AnkiDroidApp.TAG, "Info: mDownloadDeckListener - onPreExcecute");
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(Info.this, "",
                		getResources().getString(R.string.download_deck, countDown / 1024), true);
            }
        }


        @Override
        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "Info: mDownloadDeckListener - onPostExecute, succes = " + data.success);
        	Resources res = getResources();
            try {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }            	
            } catch (IllegalArgumentException e) {
            	Log.e(AnkiDroidApp.TAG, "Info - IllegalArgumentException: " + e);
            }

            if (data.success) {
            	Intent intent = new Intent();
            	intent.putExtra("importPath", (String) data.result);
            	setResult(RESULT_OK, intent);
                finish();
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                }
            } else {
                StyledDialog.Builder builder = new StyledDialog.Builder(Info.this);
                builder.setTitle(res.getString(R.string.connection_error_title));
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setMessage(res.getString(R.string.register_error));
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
    
    private Connection.TaskListener mSyncListener = new Connection.TaskListener() {

        String currentMessage;
        long countUp;
        long countDown;


        @Override
        public void onDisconnected() {
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }


        @Override
        public void onPreExecute() {
            countUp = 0;
            countDown = 0;
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog
                        .show(Info.this, getResources().getString(R.string.sync_title),
                                getResources().getString(R.string.sync_prepare_syncing) + "\n"
                                        + getResources().getString(R.string.sync_up_down_size, countUp, countDown),
                                true, false);
            }
        }


        @Override
        public void onProgressUpdate(Object... values) {
            Resources res = getResources();
            if (values[0] instanceof Boolean) {
                // This is the part Download missing media of syncing
                int total = ((Integer) values[1]).intValue();
                int done = ((Integer) values[2]).intValue();
                values[0] = ((String) values[3]);
                values[1] = res.getString(R.string.sync_downloading_media, done, total);
            } else if (values[0] instanceof Integer) {
                int id = (Integer) values[0];
                if (id != 0) {
                    currentMessage = res.getString(id);
                }
                if (values.length >= 3) {
                    countUp = (Long) values[1];
                    countDown = (Long) values[2];
                }
            }
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                // mProgressDialog.setTitle((String) values[0]);
                mProgressDialog.setMessage(currentMessage + "\n"
                        + res.getString(R.string.sync_up_down_size, countUp / 1024, countDown / 1024));
            }
        }


        @Override
        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "onPostExecute");
            Resources res = Info.this.getResources();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            if (!data.success) {
                Object[] result = (Object[]) data.result;
                if (result[0] instanceof String) {
                    String resultType = (String) result[0];
                    if (resultType.equals("badAuth")) {
                        // delete old auth information
                        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
                        Editor editor = preferences.edit();
                        editor.putString("username", "");
                        editor.putString("hkey", "");
                        editor.commit();
                        // then show
                        showDialog(DIALOG_USER_NOT_LOGGED_IN_SYNC);
                    } else if (resultType.equals("dbError")) {
                        mDialogMessage = res.getString(R.string.sync_corrupt_database, R.string.repair_deck);
                        showDialog(DIALOG_SYNC_LOG);
                    } else if (resultType.equals("overwriteError")) {
                        mDialogMessage = res.getString(R.string.sync_overwrite_error);
                        showDialog(DIALOG_SYNC_LOG);
                    } else if (resultType.equals("remoteDbError")) {
                        mDialogMessage = res.getString(R.string.sync_remote_db_error);
                        showDialog(DIALOG_SYNC_LOG);
                    } else if (resultType.equals("sdAccessError")) {
                        mDialogMessage = res.getString(R.string.sync_write_access_error);
                        showDialog(DIALOG_SYNC_LOG);
                    } else if (resultType.equals("genericError")) {
                        mDialogMessage = res.getString(R.string.sync_generic_error);
                        showDialog(DIALOG_SYNC_LOG);
                    } else if (resultType.equals("upgradeRequired")) {
                        showDialog(DIALOG_SYNC_UPGRADE_REQUIRED);
                    } else {
                    	if (result.length > 1 && result[1] instanceof Integer) {
                            int type = (Integer) result[1];
                            switch (type) {
                                case 503:
                                    mDialogMessage = res.getString(R.string.sync_too_busy);
                                    break;
                                default:
                                    mDialogMessage = res.getString(R.string.sync_log_error_specific,
                                            Integer.toString(type), (String) result[2]);
                                    break;
                            }                    		
                    	} else if (result[0] instanceof String) {
                            mDialogMessage = res.getString(R.string.sync_log_error_specific,
                                    -1, (String) result[0]);
                    	} else {
                            mDialogMessage = res.getString(R.string.sync_generic_error);
                    	}
                    }
                }
            } else {
                setResult(RESULT_OK);
                finish();
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(Info.this, ActivityTransitionAnimation.LEFT);
                }
            }
        }
    };
}
