package com.ichi2.anki;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.ichi2.anki.glosbe.json.Meaning;
import com.ichi2.anki.glosbe.json.Response;
import com.ichi2.anki.glosbe.json.Tuc;
import com.ichi2.anki.htmlutils.Unescaper;
import com.ichi2.anki.web.HttpFetcher;

public class TranslationActivity extends FragmentActivity implements DialogInterface.OnClickListener
{

    // Something to translate
    public static final String EXTRA_SOURCE = "translation.activity.extra.source";
    // Translated result
    public static final String EXTRA_TRANSLATION = "translation.activity.extra.translation";

    String mSource;
    String mTranslation;
    private LanguagesListerGlosbe mLanguageLister;
    private Spinner mSpinnerFrom;
    private Spinner mSpinnerTo;
    private ProgressDialog progressDialog;
    private String mWebServiceAddress;
    private ArrayList<String> mPossibleTranslations;
    private String mLangCodeTo;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation);

        try
        {
            mSource = getIntent().getExtras().getString(EXTRA_SOURCE).toString();
        }
        catch (Exception e)
        {
            mSource = "";
        }

        // If translation fails this is a default - source will be returned.
        mTranslation = mSource;

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.MainLayoutInTranslationActivity);

        TextView tv = new TextView(this);
        tv.setText("Powered by Glosbe.com");
        linearLayout.addView(tv);

        mLanguageLister = new LanguagesListerGlosbe();

        mSpinnerFrom = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                mLanguageLister.getLanguages());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFrom.setAdapter(adapter);
        linearLayout.addView(mSpinnerFrom);

        mSpinnerTo = new Spinner(this);
        ArrayAdapter<String> adapterTo = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                mLanguageLister.getLanguages());
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerTo.setAdapter(adapterTo);
        linearLayout.addView(mSpinnerTo);

        Button btnDone = new Button(this);
        // TODO Translation
        btnDone.setText("Translate");
        btnDone.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                translate();
            }
        });

        linearLayout.addView(btnDone);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_translation, menu);
        return true;
    }

    private class BackgroundPost extends AsyncTask<Void, Void, String>
    {

        @Override
        protected String doInBackground(Void... params)
        {
            return HttpFetcher.fetchThroughHttp(mWebServiceAddress);
        }

        @Override
        protected void onPostExecute(String result)
        {
            progressDialog.dismiss();
            mTranslation = result;
            showPickTranslationDialog();
        }

    }

    protected void translate()
    {
        progressDialog = ProgressDialog.show(this,
                "Wait...",
                "Translating Online", true, false);
        
        mWebServiceAddress = computeAddress();
        
        try {
            BackgroundPost post = new BackgroundPost();
            post.execute();
        } catch (Exception e) {
            progressDialog.dismiss();
            // TODO Translation, proper handling
            showToast("Something went wrong...");
        }
    }

    private String computeAddress()
    {
        String address = "http://glosbe.com/gapi/translate?from=FROMLANG&dest=TOLANG&format=json&phrase=SOURCE&pretty=true";

        String strFrom = mSpinnerFrom.getSelectedItem().toString();
        // Conversion to iso, lister created before.
        String langCodeFrom = mLanguageLister.getCodeFor(strFrom);

        String strTo = mSpinnerTo.getSelectedItem().toString();
        mLangCodeTo = mLanguageLister.getCodeFor(strTo);

        String query;

        try
        {
            query = URLEncoder.encode(mSource, "utf-8");
        }
        catch (UnsupportedEncodingException e)
        {
            query = mSource.replace(" ", "%20");
        }

        address = address.replaceAll("FROMLANG", langCodeFrom).replaceAll("TOLANG", mLangCodeTo)
                .replaceAll("SOURCE", query);
        
        return address;
    }

    private void showPickTranslationDialog()
    {
        if(mTranslation.startsWith("FAILED"))
        {
            // TODO Translation
            returnFailure("Fetching results from glosbe failed");
        }

        Gson gson = new Gson();
        Response resp = gson.fromJson(mTranslation, Response.class);
        
        if(!resp.getResult().contentEquals("ok"))
        {
            //TODO Translation
            returnFailure("Fetching from glosbe was not successful");
        }
        
        mPossibleTranslations = new ArrayList<String>();
        
        List<Tuc> tucs = resp.getTuc();
        
        for (Tuc tuc : tucs)
        {
            if(tuc == null)
            {
                continue;
            }
            List<Meaning> meanings = tuc.getMeanings();
            if(meanings == null)
            {
                continue;
            }
            for (Meaning meaning : meanings)
            {
                if(meaning == null)
                {
                    continue;
                }
                if(meaning.getLanguage().equals(mLangCodeTo))
                {
                    String unescappedString = Unescaper.unescapeHTML(meaning.getText());
                    mPossibleTranslations.add(unescappedString);
                }
            }
        }
        
        if(mPossibleTranslations.size() == 0)
        {
            //TODO Translation
            returnFailure("Such word was not found");
        }
        
        PickStringDialogFragment fragment = new PickStringDialogFragment();

        fragment.setChoices(mPossibleTranslations);
        fragment.setOnclickListener(this);
        //TODO translate
        fragment.setTitle("Pick translation");

        fragment.show(this.getSupportFragmentManager(), "pick.translation");
        
    }

    private void returnTheTranslation()
    {
        Intent resultData = new Intent();

        resultData.putExtra(EXTRA_TRANSLATION, mTranslation);

        setResult(RESULT_OK, resultData);

        finish();
    }

    private void returnFailure(String explanation)
    {
        showToast(explanation);
        setResult(RESULT_CANCELED);
        finish();
    }


    private void showToast(CharSequence text) {
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        mTranslation = mPossibleTranslations.get(which);
        returnTheTranslation();        
    }
    
}
