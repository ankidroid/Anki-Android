/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.google.gson.Gson;
import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.googleimagesearch.json.ImageSearchResponse;
import com.ichi2.anki.multimediacard.googleimagesearch.json.ResponseData;
import com.ichi2.anki.multimediacard.googleimagesearch.json.Result;
import com.ichi2.anki.web.HttpFetcher;
import com.ichi2.anki.web.UrlTools;
import com.ichi2.async.Connection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class SearchImageActivity extends Activity implements DialogInterface.OnCancelListener {
    private static final String BUNDLE_KEY_SHUT_OFF = "key.multimedia.shut.off";

    public static final String EXTRA_SOURCE = "search.image.activity.extra.source";
    // Passed out as a result
    public static String EXTRA_IMAGE_FILE_PATH = "com.ichi2.anki.search.image.activity.extra.image.file.path";

    private String mSource;
    private WebView mWebView = null;
    private Button mPrevButton;
    private Button mNextButton;
    private ProgressDialog progressDialog;
    private ArrayList<String> mImages;
    private int mCurrentImage;
    private String mTemplate = null;
    private Button mPickButton;
    private DownloadFileTask mDownloadMp3Task;


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true);

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mWebView != null) {
            // Saving memory
            mWebView.clearCache(true);
        }
    }


    private void finishCancel() {
        Intent resultData = new Intent();
        setResult(RESULT_CANCELED, resultData);
        finish();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            boolean b = savedInstanceState.getBoolean(BUNDLE_KEY_SHUT_OFF, false);
            if (b) {
                finishCancel();
                return;
            }
        }

        setContentView(R.layout.activity_search_image);

        try {
            mSource = getIntent().getExtras().getString(EXTRA_SOURCE);
        } catch (Exception e) {
            mSource = "";
        }

        // If translation fails this is a default - source will be returned.

        mWebView = (WebView) findViewById(R.id.ImageSearchWebView);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                processPageLoadFinished();
            }


            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                processPageLoadStarted();
            }

        });

        mPickButton = (Button) findViewById(R.id.ImageSearchPick);
        mPickButton.setEnabled(false);
        mPickButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });

        mNextButton = (Button) findViewById(R.id.ImageSearchNext);

        mNextButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                nextClicked();
            }
        });

        mPrevButton = (Button) findViewById(R.id.ImageSearchPrev);

        mPrevButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                prevClicked();
            }
        });

        mPrevButton.setEnabled(false);

    }

    /**
     * @author zaur This is to load finally the MP3 file with pronunciation.
     */
    private class DownloadFileTask extends AsyncTask<Void, Void, String> {

        private String mAddress;
        private Context mActivity;


        @Override
        protected String doInBackground(Void... params) {
            return HttpFetcher.downloadFileToSdCard(mAddress, mActivity, "imgsearch");
        }


        public void setAddress(String address) {
            mAddress = address;
        }


        @Override
        protected void onPostExecute(String result) {
            receiveImageFile(result);
        }


        public void setActivity(Context mActivity) {
            this.mActivity = mActivity;
        }

    }


    protected void pickImage() {
        if(!Connection.isOnline()) {
            returnFailure(gtxt(R.string.network_no_connection));
            return;
        }

        String imageUrl = mImages.get(mCurrentImage);

        // And here it is possible to download it... so on,
        // then return file path.

        // Download MP3 file
        try {
            progressDialog = ProgressDialog.show(this, gtxt(R.string.multimedia_editor_progress_wait_title),
                    gtxt(R.string.multimedia_editor_imgs_saving_image), true, false);
            mDownloadMp3Task = new DownloadFileTask();
            mDownloadMp3Task.setActivity(this);
            mDownloadMp3Task.setAddress(imageUrl);
            mDownloadMp3Task.execute();
        } catch (Exception e) {
            progressDialog.dismiss();
            returnFailure(gtxt(R.string.multimedia_editor_something_wrong));
        }
    }


    public void receiveImageFile(String result) {
        dismissCarefullyProgressDialog();

        Intent resultData = new Intent();

        resultData.putExtra(EXTRA_IMAGE_FILE_PATH, result);

        setResult(RESULT_OK, resultData);

        finish();

    }


    protected void processPageLoadStarted() {
        mPickButton.setEnabled(false);
        progressDialog = ProgressDialog.show(this, getText(R.string.multimedia_editor_progress_wait_title),
                gtxt(R.string.multimedia_editor_imgs_loading_image), true, false);

        progressDialog.setCancelable(true);
    }


    protected void processPageLoadFinished() {
        dismissCarefullyProgressDialog();
        mPickButton.setEnabled(true);
    }


    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            return "";
        }
        return "";
    }

    private class BackgroundPost extends AsyncTask<Void, Void, ImageSearchResponse> {

        private String mQuery;


        @Override
        protected ImageSearchResponse doInBackground(Void... params) {
            try {
                String ip = getLocalIpAddress();

                URL url = new URL("https://ajax.googleapis.com/ajax/services/search/images?"
                        + "v=1.0&q=Q&userip=IP".replaceAll("Q", getQuery()).replaceAll("IP", UrlTools.encodeUrl(ip)));
                URLConnection connection = url.openConnection();
                connection.addRequestProperty("Referer", "anki.ichi2.com");
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(10000);

                String line;
                StringBuilder builder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                Gson gson = new Gson();
                ImageSearchResponse resp = gson.fromJson(builder.toString(), ImageSearchResponse.class);

                resp.setOk(true);
                return resp;

            } catch (Exception e) {
                return new ImageSearchResponse();
            }
        }


        @Override
        protected void onPostExecute(ImageSearchResponse result) {
            postFinished(result);
        }


        /**
         * @param query Used to set the download address
         */
        public void setQuery(String query) {
            mQuery = query;
        }


        /**
         * @return Used to know, which of the posts finished, to differentiate.
         */
        public String getQuery() {
            return UrlTools.encodeUrl(mQuery);
        }

    }


    @Override
    protected void onResume() {
        super.onResume();

        progressDialog = ProgressDialog.show(this, getText(R.string.multimedia_editor_progress_wait_title),
                getText(R.string.multimedia_editor_imgs_searching_for_images), true, false);

        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(this);

        if(!Connection.isOnline()) {
            returnFailure(gtxt(R.string.network_no_connection));
            return;
        }

        BackgroundPost p = new BackgroundPost();
        p.setQuery(mSource);
        p.execute();
    }


    public void postFinished(ImageSearchResponse response) {

        ArrayList<String> theImages = new ArrayList<String>();

        // No loop, just a good construct to break out from
        do {
            if (response == null) {
                break;
            }

            if (!response.getOk()) {
                break;
            }

            ResponseData rdata = response.getResponseData();

            if (rdata == null) {
                break;
            }

            List<Result> results = rdata.getResults();

            if (results == null) {
                break;
            }

            for (Result result : results) {
                if (result == null) {
                    continue;
                }

                String url = result.getUrl();

                if (url != null) {
                    theImages.add(url);
                }
            }

            if (theImages.size() == 0) {
                break;
            }

            proceedWithImages(theImages);

            return;

        } while (false);

        returnFailure(gtxt(R.string.multimedia_editor_imgs_no_results));
    }


    private void proceedWithImages(ArrayList<String> theImages) {
        showToast(gtxt(R.string.multimedia_editor_imgs_images_found));
        dismissCarefullyProgressDialog();

        mImages = theImages;
        mCurrentImage = 0;

        showCurrentImage();

    }


    private void showCurrentImage() {
        if (mCurrentImage <= 0) {
            mCurrentImage = 0;
            mPrevButton.setEnabled(false);
            mNextButton.setEnabled(mImages.size() > 0);
        }

        if (mCurrentImage > 0) {
            mCurrentImage = Math.min(mImages.size() - 1, mCurrentImage);
            mPrevButton.setEnabled(true);
            mNextButton.setEnabled(mCurrentImage < mImages.size() - 1);
        }

        String source = makeImageCodeTemplate();

        source = source.replaceAll("URL", mImages.get(mCurrentImage));

        mWebView.loadData(source, "text/html", null);

    }


    private void returnFailure(String explanation) {
        showToast(explanation);
        setResult(RESULT_CANCELED);
        dismissCarefullyProgressDialog();
        finish();
    }


    private void dismissCarefullyProgressDialog() {
        try {
            if (progressDialog != null) {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        } catch (Exception e) {
            // nothing is done intentionally
        }
    }


    private void showToast(CharSequence text) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }


    protected void prevClicked() {
        --mCurrentImage;
        showCurrentImage();

    }


    protected void nextClicked() {
        ++mCurrentImage;
        showCurrentImage();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_search_image, menu);
        return true;
    }


    private String makeImageCodeTemplate() {
        if (mTemplate != null) {
            return mTemplate;
        }

        String source = "<html><body><center>" + gtxt(R.string.multimedia_editor_imgs_pow_by_google)
                + "</center><br /><center><img width=\"WIDTH\" src=\"URL\" /> </center></body></html>";

        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion <= android.os.Build.VERSION_CODES.HONEYCOMB_MR2) {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            int height = metrics.heightPixels;
            int width = metrics.widthPixels;

            int min = Math.min(height, width);

            source = source.replaceAll("WIDTH", (int) Math.round(min * 0.85) + "");
        } else {
            source = source.replaceAll("WIDTH", "80%");
        }

        mTemplate = source;

        return source;
    }


    @Override
    public void onCancel(DialogInterface dialog) {
        // nothing
    }


    private String gtxt(int id) {
        return getText(id).toString();
    }

}
