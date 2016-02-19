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
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.beolingus.parsing.BeolingusParser;
import com.ichi2.anki.multimediacard.language.LanguageListerBeolingus;
import com.ichi2.anki.runtimetools.TaskOperations;
import com.ichi2.anki.web.HttpFetcher;
import com.ichi2.async.Connection;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Activity to load pronunciation files from Beolingus.
 * <p>
 * User picks a source language and the source is passed as extra.
 * <p>
 * When activity finished, it passes the filepath as another extra to the caller.
 */
public class LoadPronounciationActivity extends Activity implements OnCancelListener {

    private static final String BUNDLE_KEY_SHUT_OFF = "key.multimedia.shut.off";
    // Must be passed in
    public static String EXTRA_SOURCE = "com.ichi2.anki.LoadPronounciationActivity.extra.source";
    // Passed out as a result
    public static String EXTRA_PRONUNCIATION_FILE_PATH = "com.ichi2.anki.LoadPronounciationActivity.extra.pronun.file.path";

    String mSource;

    private String mTranslationAddress;

    private ProgressDialog progressDialog = null;

    private String mTranslation;

    private String mPronunciationAddress;

    private String mPronunciationPage;

    private String mMp3Address;

    private LoadPronounciationActivity mActivity;
    private LanguageListerBeolingus mLanguageLister;
    private Spinner mSpinnerFrom;
    private Button mSaveButton;

    private BackgroundPost mPostTranslation = null;
    private BackgroundPost mPostPronunciation = null;
    private DownloadFileTask mDownloadMp3Task = null;

    private boolean mStopped;


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

        setContentView(R.layout.activity_load_pronounciation);
        mSource = getIntent().getExtras().getString(EXTRA_SOURCE);

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.layoutInLoadPronActivity);

        mLanguageLister = new LanguageListerBeolingus(this);

        mSpinnerFrom = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                mLanguageLister.getLanguages());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFrom.setAdapter(adapter);
        linearLayout.addView(mSpinnerFrom);

        Button buttonLoadPronunciation = new Button(this);
        buttonLoadPronunciation.setText(gtxt(R.string.multimedia_editor_pron_load));
        linearLayout.addView(buttonLoadPronunciation);
        buttonLoadPronunciation.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                onLoadPronunciation(v);

            }
        });

        mSaveButton = new Button(this);
        mSaveButton.setText("Save");
        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        mActivity = this;

        mStopped = false;

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_load_pronounciation, menu);
        return true;
    }


    /**
     * @param v Start of the story.
     */
    protected void onLoadPronunciation(View v) {
        if(!Connection.isOnline()) {
            showToast(gtxt(R.string.network_no_connection));
            return;
        }

        String message = gtxt(R.string.multimedia_editor_searching_word);

        showProgressDialog(message);

        mTranslationAddress = computeAddressOfTranslationPage();

        try {
            mPostTranslation = new BackgroundPost();
            mPostTranslation.setAddress(mTranslationAddress);
            // post.setStopper(PRONUNC_STOPPER);
            mPostTranslation.execute();
        } catch (Exception e) {
            progressDialog.dismiss();
            showToast(gtxt(R.string.multimedia_editor_something_wrong));
        }
    }


    private void showProgressDialog(String message) {

        dismissCarefullyProgressDialog();

        progressDialog = ProgressDialog.show(this, gtxt(R.string.multimedia_editor_progress_wait_title), message, true,
                false);
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(this);
    }

    /**
     * @author zaur This class is used two times. First time from Beolingus it requests a page with the word
     *         translation. Second time it loads a page with the link to mp3 pronunciation file.
     */
    private class BackgroundPost extends AsyncTask<Void, Void, String> {

        private String mAddress;


        // private String mStopper;

        @Override
        protected String doInBackground(Void... params) {
            // TMP CODE for quick testing
            // if (mAddress.contentEquals(mTranslationAddress))
            // {
            // return MockTranslationFetcher.get();
            // }
            // else if (mAddress.contentEquals(mPronunciationAddress))
            // {
            // return MockPronounciationPageFetcher.get();
            // }

            // Should be just this
            return HttpFetcher.fetchThroughHttp(getAddress(), "ISO-8859-1");
        }


        /**
         * @param address Used to set the download address
         */
        public void setAddress(String address) {
            mAddress = address;
        }


        /**
         * @return Used to know, which of the posts finished, to differentiate.
         */
        public String getAddress() {
            return mAddress;
        }


        @Override
        protected void onPostExecute(String result) {
            // Result here is the whole HTML of the page
            // this is passed to ask for address and differentiate, which of the
            // post has finished.
            processPostFinished(this, result);
        }

    }

    /**
     * @author zaur This is to load finally the MP3 file with pronunciation.
     */
    private class DownloadFileTask extends AsyncTask<Void, Void, String> {

        private String mAddress;


        @Override
        protected String doInBackground(Void... params) {
            return HttpFetcher.downloadFileToSdCard(mAddress, mActivity, "pronunc");
        }


        public void setAddress(String address) {
            mAddress = address;
        }


        @Override
        protected void onPostExecute(String result) {
            receiveMp3File(result);
        }

    }


    protected void processPostFinished(BackgroundPost post, String result) {

        if (mStopped) {
            return;
        }

        // First call returned
        // Means we get the page with the word translation,
        // And we have to start fetching the page with pronunciation
        if (post.getAddress().contentEquals(mTranslationAddress)) {
            mTranslation = result;

            if (mTranslation.startsWith("FAILED")) {

                failNoPronunciation();

                return;
            }

            mPronunciationAddress = BeolingusParser.getPronounciationAddressFromTranslation(mTranslation, mSource);

            if (mPronunciationAddress.contentEquals("no")) {

                failNoPronunciation();

                if (!mSource.toLowerCase(Locale.getDefault()).contentEquals(mSource)) {
                    showToastLong(gtxt(R.string.multimedia_editor_word_search_try_lower_case));
                }

                return;
            }

            try {
                showProgressDialog(gtxt(R.string.multimedia_editor_pron_looking_up));
                mPostPronunciation = new BackgroundPost();
                mPostPronunciation.setAddress(mPronunciationAddress);
                mPostPronunciation.execute();
            } catch (Exception e) {
                progressDialog.dismiss();
                showToast(gtxt(R.string.multimedia_editor_something_wrong));
            }

            return;
        }

        // Else
        // second call returned
        // This is a call when pronunciation page has been fetched.
        // We chekc if mp3 file could be downloaded and download it.
        if (post.getAddress().contentEquals(mPronunciationAddress)) {
            // else here = pronunciation post returned;

            mPronunciationPage = result;

            mMp3Address = BeolingusParser.getMp3AddressFromPronounciation(mPronunciationPage);

            if (mMp3Address.contentEquals("no")) {
                failNoPronunciation();
                return;
            }

            // Download MP3 file
            try {
                showProgressDialog(gtxt(R.string.multimedia_editor_general_downloading));
                mDownloadMp3Task = new DownloadFileTask();
                mDownloadMp3Task.setAddress(mMp3Address);
                mDownloadMp3Task.execute();
            } catch (Exception e) {
                progressDialog.dismiss();
                showToast(gtxt(R.string.multimedia_editor_something_wrong));
            }

            return;

        }

    }


    // This is called when MP3 Download is finished.
    public void receiveMp3File(String result) {
        if (mStopped) {
            return;
        }

        if (result == null) {
            failNoPronunciation();
            return;
        }

        if (result.startsWith("FAIL")) {
            failNoPronunciation();
            return;
        }

        progressDialog.dismiss();

        showToast(gtxt(R.string.multimedia_editor_general_done));

        Intent resultData = new Intent();

        resultData.putExtra(EXTRA_PRONUNCIATION_FILE_PATH, result);

        setResult(RESULT_OK, resultData);

        finish();

    }


    private void finishCancel() {
        Intent resultData = new Intent();
        setResult(RESULT_CANCELED, resultData);
        finish();
    }


    private void failNoPronunciation() {
        stop(gtxt(R.string.multimedia_editor_error_word_not_found));
        mPronunciationAddress = "no";
        mMp3Address = "no";
    }


    private void stop(String string) {
        progressDialog.dismiss();
        showToast(string);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true);
    }


    private String computeAddressOfTranslationPage() {
        // Service name has to be replaced from the language lister.
        String address = "http://dict.tu-chemnitz.de/dings.cgi?lang=en&service=SERVICE&opterrors=0&optpro=0&query=Welt";

        String strFrom = mSpinnerFrom.getSelectedItem().toString();
        String langCodeFrom = mLanguageLister.getCodeFor(strFrom);

        String query;

        try {
            query = URLEncoder.encode(mSource, "utf-8");
        } catch (UnsupportedEncodingException e) {
            query = mSource.replace(" ", "%20");
        }

        address = address.replaceAll("SERVICE", langCodeFrom).replaceAll("Welt", query);

        return address;
    }


    private void showToast(CharSequence text) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }


    private void showToastLong(CharSequence text) {
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }


    // If the loading and dialog are cancelled
    @Override
    public void onCancel(DialogInterface dialog) {
        mStopped = true;

        dismissCarefullyProgressDialog();

        stopAllTasks();

        Intent resultData = new Intent();

        setResult(RESULT_CANCELED, resultData);

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


    private void stopAllTasks() {
        AsyncTask<?, ?, ?> t;
        t = mPostTranslation;
        TaskOperations.stopTaskGracefully(t);
        t = mPostPronunciation;
        TaskOperations.stopTaskGracefully(t);
        t = mDownloadMp3Task;
        TaskOperations.stopTaskGracefully(t);
    }


    @Override
    protected void onPause() {
        super.onPause();
        dismissCarefullyProgressDialog();
        stopAllTasks();
    }


    private String gtxt(int id) {
        return getText(id).toString();
    }

}
