package com.ichi2.anki;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.app.Activity;
import android.app.ProgressDialog;
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

import com.ichi2.anki.beolingus.parsing.BeolingusParser;
import com.ichi2.anki.web.HttpFetcher;

public class LoadPronounciationActivity extends Activity
{
    public static String EXTRA_SOURCE = "com.ichi2.anki.LoadPronounciationActivity.extra.source";
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

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_pronounciation);
        mSource = getIntent().getExtras().getString(EXTRA_SOURCE);

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.layoutInLoadPronActivity);

        mLanguageLister = new LanguageListerBeolingus();

        mSpinnerFrom = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                mLanguageLister.getLanguages());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFrom.setAdapter(adapter);
        linearLayout.addView(mSpinnerFrom);

        Button buttonLoadPronunciation = (Button) new Button(this);
        buttonLoadPronunciation.setText("Load pronounciation!");
        linearLayout.addView(buttonLoadPronunciation);
        buttonLoadPronunciation.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                onLoadPronunciation(v);

            }
        });

        mActivity = this;

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_load_pronounciation, menu);
        return true;
    }

    /**
     * @param v
     * 
     *            Start of the story.
     * 
     */
    protected void onLoadPronunciation(View v)
    {

        // TODO Translate
        String message = "Looking up Beolingus...";

        showProgressDialog(message);

        mTranslationAddress = computeAddressOfTranslationPage();

        try
        {
            BackgroundPost post = new BackgroundPost();
            post.setAddress(mTranslationAddress);
            // post.setStopper(PRONUNC_STOPPER);
            post.execute();
        }
        catch (Exception e)
        {
            progressDialog.dismiss();
            // TODO Translation, proper handling
            showToast("Something went wrong...");
        }
    }

    private void showProgressDialog(String message)
    {

        if (progressDialog != null)
        {
            if (progressDialog.isShowing())
            {
                progressDialog.dismiss();
            }
        }

        progressDialog = ProgressDialog.show(this, "Wait...", message, true, false);
    }

    /**
     * @author zaur
     * 
     *         This class is used two times.
     * 
     *         First time from Beolingus it requests a page with the word
     *         translation. Second time it loads a page with the link to mp3
     *         pronunciation file.
     * 
     */
    private class BackgroundPost extends AsyncTask<Void, Void, String>
    {

        private String mAddress;

        // private String mStopper;

        @Override
        protected String doInBackground(Void... params)
        {
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
         * @param address
         * 
         *            Used to set the download address
         * 
         */
        public void setAddress(String address)
        {
            mAddress = address;
        }

        /**
         * @return
         * 
         *         Used to know, which of the posts finished, to differentiate.
         * 
         */
        public String getAddress()
        {
            return mAddress;
        }

        @Override
        protected void onPostExecute(String result)
        {
            // Result here is the whole HTML of the page
            // this is passed to ask for address and differentiate, which of the
            // post has finished.
            processPostFinished(this, result);
        }

    }

    /**
     * @author zaur
     * 
     *         This is to load finally the MP3 file with pronunciation.
     * 
     */
    private class DownloadFileTask extends AsyncTask<Void, Void, String>
    {

        private String mAddress;

        @Override
        protected String doInBackground(Void... params)
        {
            return HttpFetcher.downloadFileToCache(mAddress, mActivity);
        }

        public void setAddress(String address)
        {
            mAddress = address;
        }

        @Override
        protected void onPostExecute(String result)
        {
            receiveMp3File(result);
        }

    }

    protected void processPostFinished(BackgroundPost post, String result)
    {

        // First call returned
        // Means we get the page with the word translation,
        // And we have to start fetching the page with pronunciation
        if (post.getAddress().contentEquals(mTranslationAddress))
        {
            mTranslation = result;

            if (mTranslation.startsWith("FAILED"))
            {
                failNoPronunciation();
                return;
            }

            mPronunciationAddress = BeolingusParser.getPronounciationAddressFromTranslation(mTranslation, mSource);

            if (mPronunciationAddress.contentEquals("no"))
            {
                failNoPronunciation();
                return;
            }

            try
            {
                showToast("Word found!");
                showProgressDialog("Looking up pronunciation...");
                BackgroundPost post2 = new BackgroundPost();
                post2.setAddress(mPronunciationAddress);
                post2.execute();
            }
            catch (Exception e)
            {
                progressDialog.dismiss();
                // TODO Translation
                showToast("Something went wrong...");
            }

            return;
        }

        // Else
        // second call returned
        // This is a call when pronunciation page has been fetched.
        // We chekc if mp3 file could be downloaded and download it.
        if (post.getAddress().contentEquals(mPronunciationAddress))
        {
            // else here = pronunciation post returned;

            mPronunciationPage = result;

            mMp3Address = BeolingusParser.getMp3AddressFromPronounciation(mPronunciationPage);

            if (mMp3Address.contentEquals("no"))
            {
                failNoPronunciation();
                return;
            }

            // Download MP3 file
            try
            {
                showToast("Pronunciation found!");
                showProgressDialog("Downloading pronunciation...");
                DownloadFileTask post2 = new DownloadFileTask();
                post2.setAddress(mMp3Address);
                post2.execute();
            }
            catch (Exception e)
            {
                progressDialog.dismiss();
                // TODO Translation, proper handling
                showToast("Something went wrong...");
            }

            return;

        }

    }

    // This is called when MP3 Download is finished.
    public void receiveMp3File(String result)
    {
        if (result == null)
        {
            failNoPronunciation();
            return;
        }

        if (result.startsWith("FAIL"))
        {
            failNoPronunciation();
            return;
        }

        progressDialog.dismiss();

        // TODO Translate
        showToast("Pronounciation loaded!");

        Intent resultData = new Intent();

        resultData.putExtra(EXTRA_PRONUNCIATION_FILE_PATH, result);

        setResult(RESULT_OK, resultData);

        finish();

    }

    private void failNoPronunciation()
    {
        stop("No pronounciation");
        mPronunciationAddress = "no";
        mMp3Address = "no";
    }

    private void stop(String string)
    {
        progressDialog.dismiss();
        showToast(string);
    }

    private String computeAddressOfTranslationPage()
    {
        // Service name has to be replaced from the language lister.
        String address = "http://dict.tu-chemnitz.de/dings.cgi?lang=en&service=SERVICE&opterrors=0&optpro=0&query=Welt";

        String strFrom = mSpinnerFrom.getSelectedItem().toString();
        String langCodeFrom = mLanguageLister.getCodeFor(strFrom);

        String query;

        try
        {
            query = URLEncoder.encode(mSource, "utf-8");
        }
        catch (UnsupportedEncodingException e)
        {
            query = mSource.replace(" ", "%20");
        }

        address = address.replaceAll("SERVICE", langCodeFrom).replaceAll("Welt", query);

        return address;
    }

    private void showToast(CharSequence text)
    {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }
}
