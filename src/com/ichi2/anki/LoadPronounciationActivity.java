package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.anki.web.HttpFetcher;

public class LoadPronounciationActivity extends Activity
{
    public static String EXTRA_SOURCE = "com.ichi2.anki.LoadPronounciationActivity.extra.source";

    String mSource;

    private String mTranslationAddress;

    private ProgressDialog progressDialog;

    private String mTranslation;

    private String mPronunciationAddress;

    private String mPronunciationPage;

    private String mMp3Address;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_pronounciation);
        mSource = getIntent().getExtras().getString(EXTRA_SOURCE);

        TextView wordToPronounce = (TextView) findViewById(R.id.textViewWordInLoadPronounciation);
        wordToPronounce.setText(mSource);

        Button buttonGo = (Button) findViewById(R.id.buttonGoLoadPronounciation);
        buttonGo.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                go(v);

            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_load_pronounciation, menu);
        return true;
    }

    protected void go(View v)
    {
        progressDialog = ProgressDialog.show(this, "Wait...", "Loading Online", true, false);

        mTranslationAddress = computeAddressOfTranslationPage();

        try
        {
            BackgroundPost post = new BackgroundPost();
            post.setAddress(mTranslationAddress);
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

        @Override
        protected String doInBackground(Void... params)
        {
//            // TMP CODE
//            if (mAddress.contentEquals(mTranslationAddress))
//            {
//                return MockTranslationFetcher.get();
//            }
//            else if (mAddress.contentEquals(mPronunciationAddress))
//            {
//                return MockPronounciationPageFetcher.get();
//            }

            // Should be just this
            return HttpFetcher.fetchThroughHttp(mAddress);
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
            processPostFinished(this, result);
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

        
        //Else 
        // First call returned
        if (post.getAddress().contentEquals(mPronunciationAddress))
        {
            // else here = pronunciation post returned;
    
            mPronunciationPage = result;
    
            getMp3AddressFromPronounciation();
            
            if(mMp3Address.contentEquals("no"))
            {
                return;
            }
            
            //Here a file can be fetched.
        }

        
        
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

        String mp3 = ".mp3\">Listen";

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
        
        //Freeing resources
        mPronunciationPage = null;
        

    }

    //It's okay with addresses
    @SuppressLint("DefaultLocale")
    private void getPronounciationAddressFromTranslation()
    {
        String pronounciationIndicator = "<img src=\"/pics/s1.png\"";
        if (!mTranslation.contains(pronounciationIndicator))
        {
            failNoPronunciation();
            return;
        }

        int indIndicator = 0;
        do
        {
            indIndicator = mTranslation.indexOf(pronounciationIndicator);
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

            if (!titleValue.toLowerCase().contentEquals(mSource))
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
        String res = "http://dict.tu-chemnitz.de/dings.cgi?lang=en&service=deen&opterrors=0&optpro=0&query=Welt";
        return res.replaceAll("Welt", mSource);
    }

    private void showToast(CharSequence text)
    {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }
}
