package com.ichi2.anki;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.annotation.SuppressLint;
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
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.anki.web.HttpFetcher;

public class LoadPronounciationActivity extends Activity
{
    public static String EXTRA_SOURCE = "com.ichi2.anki.LoadPronounciationActivity.extra.source";
    public static String EXTRA_PRONUNCIATION_FILE_PATH = "com.ichi2.anki.LoadPronounciationActivity.extra.pronun.file.path";
    
    //Http Fetching is stopped on it
    protected static String MP3_STOPPER = ".mp3\">Listen";
    protected static String PRONUNC_STOPPER = "<img src=\"/pics/s1.png\"";

    String mSource;

    private String mTranslationAddress;

    private ProgressDialog progressDialog;

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

    protected void onLoadPronunciation(View v)
    {
        progressDialog = ProgressDialog.show(this, "Wait...", "Loading Online", true, false);

        mTranslationAddress = computeAddressOfTranslationPage();

        try
        {
            BackgroundPost post = new BackgroundPost();
            post.setAddress(mTranslationAddress);
//            post.setStopper(PRONUNC_STOPPER);
            post.execute();
        }
        catch (Exception e)
        {
            progressDialog.dismiss();
            // TODO Translation, proper handling
            showToast("Something went wrong...");
        }
    }

    private class BackgroundPost extends AsyncTask<Void, Void, String>
    {

        private String mAddress;
//        private String mStopper;

        @Override
        protected String doInBackground(Void... params)
        {
            // TMP CODE
//            if (mAddress.contentEquals(mTranslationAddress))
//            {
//                return MockTranslationFetcher.get();
//            }
//            else if (mAddress.contentEquals(mPronunciationAddress))
//            {
//                return MockPronounciationPageFetcher.get();
//            }

            // Should be just this
//            return HttpFetcher.fetchThroughHttpUntil(getAddress(), getStopper());
            return HttpFetcher.fetchThroughHttp(getAddress(), "ISO-8859-1");
        }

//        private String getStopper()
//        {
//            return mStopper;
//        }
//        
//        private void setStopper(String stopper)
//        {
//            mStopper = stopper;
//        }

        public void setAddress(String address)
        {
            mAddress = address;
        }

        public String getAddress()
        {
            return mAddress;
        }

        @Override
        protected void onPostExecute(String result)
        {
            processPostFinished(this, result);
        }

    }

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

        public String getAddress()
        {
            return mAddress;
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
        if (post.getAddress().contentEquals(mTranslationAddress))
        {
            mTranslation = result;

            if (mTranslation.startsWith("FAILED"))
            {
                failNoPronunciation();
                return;
            }

            // Will assign mPrononaddr
            getPronounciationAddressFromTranslation();
            if (mPronunciationAddress.contentEquals("no"))
            {
                return;
            }

            try
            {
                BackgroundPost post2 = new BackgroundPost();
                post2.setAddress(mPronunciationAddress);
//                post2.setStopper(MP3_STOPPER);
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

        // Else
        // First call returned
        if (post.getAddress().contentEquals(mPronunciationAddress))
        {
            // else here = pronunciation post returned;

            mPronunciationPage = result;

            getMp3AddressFromPronounciation();

            if (mMp3Address.contentEquals("no"))
            {
                return;
            }

            try
            {
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
        
        //TODO Translate
        showToast("Pronounciation loaded!");
        
        Intent resultData = new Intent();

        resultData.putExtra(EXTRA_PRONUNCIATION_FILE_PATH, result);

        setResult(RESULT_OK, resultData);

        finish();

    }

    private void getMp3AddressFromPronounciation()
    {
        if (mPronunciationPage.startsWith("FAILED"))
        {
            failNoPronunciation();
            return;
        }

        // Freed
        mTranslation = null;

        String mp3 = MP3_STOPPER;

        if (!mPronunciationPage.contains(mp3))
        {
            failNoPronunciation();
            return;
        }

        int indMp3 = mPronunciationPage.indexOf(mp3);
        int indAddrEnd = indMp3 + ".mp3".length();

        int addrStart = 0;
        // Back to find the address start;
        while (indMp3 > 0)
        {
            indMp3 -= 1;
            if (mPronunciationPage.charAt(indMp3) == '\"')
            {
                addrStart = indMp3 + 1;
                break;
            }

        }

        mMp3Address = "http://dict.tu-chemnitz.de" + mPronunciationPage.substring(addrStart, indAddrEnd);

        // Freeing resources
        mPronunciationPage = null;

    }

    private void getPronounciationAddressFromTranslation()
    {
        String pronounciationIndicator = PRONUNC_STOPPER;
        if (!mTranslation.contains(pronounciationIndicator))
        {
            failNoPronunciation();
            return;
        }

        int indIndicator = 0;
        do
        {
            indIndicator = mTranslation.indexOf(pronounciationIndicator, indIndicator + 1);
            if (indIndicator == -1)
            {
                failNoPronunciation();
                return;
            }
            String title = "title=\"";

            int indTitle = mTranslation.indexOf(title, indIndicator);

            if (indTitle == -1)
            {
                failNoPronunciation();
                return;
            }

            int indNextQuote = mTranslation.indexOf("\"", indTitle + title.length());
            if (indNextQuote == -1)
            {
                failNoPronunciation();
                return;
            }

            // Must be equal to the word translating
            String titleValue = mTranslation.substring(indTitle + title.length(), indNextQuote);

            if (!titleValue.contentEquals(mSource))
            {
                continue;
            }

            break;
            // indIndicator is pointing to the right one indicator!
        }
        while (true);

        String href = "href=\"";
        // Rolling back for the reference
        while (indIndicator > 0)
        {
            indIndicator -= 1;
            if (!mTranslation.substring(indIndicator, indIndicator + href.length()).contentEquals(href))
            {
                continue;
            }

            break;
            // indIndicator contains where href starts;
        }

        int indNextQuote = mTranslation.indexOf("\"", indIndicator + href.length());
        if (indNextQuote == -1)
        {
            failNoPronunciation();
            return;
        }

        String pronounciationAddress = mTranslation.substring(indIndicator + href.length(), indNextQuote);

        mPronunciationAddress = "http://dict.tu-chemnitz.de" + pronounciationAddress;
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
        String address = "http://dict.tu-chemnitz.de/dings.cgi?lang=en&service=deen&opterrors=0&optpro=0&query=Welt";
        
        String strFrom = mSpinnerFrom.getSelectedItem().toString();
        // Conversion to iso, lister created before.
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

        address = address.replaceAll("deen", langCodeFrom)
                .replaceAll("Welt", query);
        
        return address;
    }

    private void showToast(CharSequence text)
    {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }
}

