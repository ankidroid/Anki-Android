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

import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.glosbe.json.Meaning;
import com.ichi2.anki.multimediacard.glosbe.json.Phrase;
import com.ichi2.anki.multimediacard.glosbe.json.Response;
import com.ichi2.anki.multimediacard.glosbe.json.Tuc;
import com.ichi2.anki.multimediacard.language.LanguagesListerGlosbe;
import com.ichi2.anki.runtimetools.TaskOperations;
import com.ichi2.anki.web.HttpFetcher;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity used now with Glosbe.com to enable translation of words.
 * FIXME why isn't this extending from our base classes?
 */
public class TranslationActivity extends FragmentActivity implements DialogInterface.OnClickListener, OnCancelListener {

    private static final String BUNDLE_KEY_SHUT_OFF = "key.multimedia.shut.off";

    // Something to translate
    public static final String EXTRA_SOURCE = "translation.activity.extra.source";
    // Translated result
    public static final String EXTRA_TRANSLATION = "translation.activity.extra.translation";

    private String mSource;
    private String mTranslation;
    private LanguagesListerGlosbe mLanguageLister;
    private Spinner mSpinnerFrom;
    private Spinner mSpinnerTo;
    @SuppressWarnings("deprecation") // tracked in github as #5020
    private android.app.ProgressDialog progressDialog = null;
    private String mWebServiceAddress;
    private ArrayList<String> mPossibleTranslations;
    private String mLangCodeTo;
    private BackgroundPost mTranslationLoadPost = null;


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

        setContentView(R.layout.activity_translation);

        try {
            mSource = getIntent().getExtras().getString(EXTRA_SOURCE);
        } catch (Exception e) {
            mSource = "";
        }

        // If translation fails this is a default - source will be returned.
        mTranslation = mSource;

        LinearLayout linearLayout = findViewById(R.id.MainLayoutInTranslationActivity);

        TextView tv = new TextView(this);
        tv.setText(getText(R.string.multimedia_editor_trans_poweredglosbe));
        linearLayout.addView(tv);

        TextView tvFrom = new TextView(this);
        tvFrom.setText(getText(R.string.multimedia_editor_trans_from));
        linearLayout.addView(tvFrom);

        mLanguageLister = new LanguagesListerGlosbe();

        mSpinnerFrom = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                mLanguageLister.getLanguages());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFrom.setAdapter(adapter);
        linearLayout.addView(mSpinnerFrom);

        TextView tvTo = new TextView(this);
        tvTo.setText(getText(R.string.multimedia_editor_trans_to));
        linearLayout.addView(tvTo);

        mSpinnerTo = new Spinner(this);
        ArrayAdapter<String> adapterTo = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                mLanguageLister.getLanguages());
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerTo.setAdapter(adapterTo);
        linearLayout.addView(mSpinnerTo);

        final SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        // Try to set spinner value to last selected position
        String fromLang = preferences.getString("translatorLastLanguageFrom", "");
        String toLang = preferences.getString("translatorLastLanguageTo", "");
        mSpinnerFrom.setSelection(getSpinnerIndex(mSpinnerFrom, fromLang));
        mSpinnerTo.setSelection(getSpinnerIndex(mSpinnerTo, toLang));
        // Setup button
        Button btnDone = new Button(this);
        btnDone.setText(getText(R.string.multimedia_editor_trans_translate));
        btnDone.setOnClickListener(v -> {
            // Remember currently selected language
            String fromLang1 = mSpinnerFrom.getSelectedItem().toString();
            String toLang1 = mSpinnerTo.getSelectedItem().toString();
            preferences.edit().putString("translatorLastLanguageFrom", fromLang1).apply();
            preferences.edit().putString("translatorLastLanguageTo", toLang1).apply();
            // Get translation
            translate();
        });

        linearLayout.addView(btnDone);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_translation, menu);
        return true;
    }

    private class BackgroundPost extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            return HttpFetcher.fetchThroughHttp(mWebServiceAddress);
        }


        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();
            mTranslation = result;
            showPickTranslationDialog();
        }

    }


    @SuppressWarnings("deprecation") // ProgressDialog change tracked in github as #5020
    protected void translate() {
        if(!Connection.isOnline()) {
            showToast(gtxt(R.string.network_no_connection));
            return;
        }

        progressDialog = android.app.ProgressDialog.show(this, getText(R.string.multimedia_editor_progress_wait_title),
                getText(R.string.multimedia_editor_trans_translating_online), true, false);

        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(this);

        mWebServiceAddress = computeAddress();

        try {
            mTranslationLoadPost = new BackgroundPost();
            mTranslationLoadPost.execute();
        } catch (Exception e) {
            progressDialog.dismiss();
            showToast(getText(R.string.multimedia_editor_something_wrong));
        }
    }


    private String computeAddress() {
        String address = "http://glosbe.com/gapi/translate?from=FROMLANG&dest=TOLANG&format=json&phrase=SOURCE&pretty=true";

        String strFrom = mSpinnerFrom.getSelectedItem().toString();
        // Conversion to iso, lister created before.
        String langCodeFrom = mLanguageLister.getCodeFor(strFrom);

        String strTo = mSpinnerTo.getSelectedItem().toString();
        mLangCodeTo = mLanguageLister.getCodeFor(strTo);

        String query;

        try {
            query = URLEncoder.encode(mSource, "utf-8");
        } catch (UnsupportedEncodingException e) {
            query = mSource.replace(" ", "%20");
        }

        address = address.replaceAll("FROMLANG", langCodeFrom).replaceAll("TOLANG", mLangCodeTo)
                .replaceAll("SOURCE", query);

        return address;
    }


    private String gtxt(int id) {
        return getText(id).toString();
    }


    private void showToastLong(CharSequence text) {
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }


    private void showPickTranslationDialog() {
        if (mTranslation.startsWith("FAILED")) {
            returnFailure(getText(R.string.multimedia_editor_trans_getting_failure).toString());
            return;
        }

        Gson gson = new Gson();
        Response resp = gson.fromJson(mTranslation, Response.class);

        if (resp == null) {
            returnFailure(getText(R.string.multimedia_editor_trans_getting_failure).toString());
            return;
        }

        if (!resp.getResult().contentEquals("ok")) {
            if (!mSource.toLowerCase(Locale.getDefault()).contentEquals(mSource)) {
                showToastLong(gtxt(R.string.multimedia_editor_word_search_try_lower_case));
            }

            returnFailure(getText(R.string.multimedia_editor_trans_getting_failure).toString());
            return;
        }

        mPossibleTranslations = parseJson(resp, mLangCodeTo);

        if (mPossibleTranslations.size() == 0) {
            if (!mSource.toLowerCase(Locale.getDefault()).contentEquals(mSource)) {
                showToastLong(gtxt(R.string.multimedia_editor_word_search_try_lower_case));
            }

            returnFailure(getText(R.string.multimedia_editor_error_word_not_found).toString());
            return;
        }

        PickStringDialogFragment fragment = new PickStringDialogFragment();

        fragment.setChoices(mPossibleTranslations);
        fragment.setOnclickListener(this);
        fragment.setTitle(getText(R.string.multimedia_editor_trans_pick_translation).toString());

        fragment.show(this.getSupportFragmentManager(), "pick.translation");

    }


    private static ArrayList<String> parseJson(Response resp, String languageCodeTo) {
        ArrayList<String> res = new ArrayList<>();

        /*
         * The algorithm below includes the parsing of glosbe results. Glosbe.com returns a list of different phrases in
         * source and destination languages. This is done, probably, to improve the reader's understanding. We leave
         * here only the translations to the destination language.
         */

        List<Tuc> tucs = resp.getTuc();

        if (tucs == null) {
            return res;
        }

        String desiredLang = LanguagesListerGlosbe.requestToResponseLangCode(languageCodeTo);

        for (Tuc tuc : tucs) {
            if (tuc == null) {
                continue;
            }
            List<Meaning> meanings = tuc.getMeanings();
            if (meanings != null) {
                for (Meaning meaning : meanings) {
                    if (meaning == null) {
                        continue;
                    }
                    if (meaning.getLanguage() == null) {
                        continue;
                    }
                    if (meaning.getLanguage().contentEquals(desiredLang)) {
                        String unescappedString = Utils.unescape(meaning.getText());
                        res.add(unescappedString);
                    }
                }
            }

            Phrase phrase = tuc.getPhrase();
            if (phrase != null) {
                if (phrase.getLanguage() == null) {
                    continue;
                }
                if (phrase.getLanguage().contentEquals(desiredLang)) {
                    String unescappedString = Utils.unescape(phrase.getText());
                    res.add(unescappedString);
                }
            }

        }

        return res;
    }


    private void returnTheTranslation() {
        Intent resultData = new Intent();

        resultData.putExtra(EXTRA_TRANSLATION, mTranslation);

        setResult(RESULT_OK, resultData);

        finish();
    }


    private void returnFailure(String explanation) {
        showToast(explanation);
        setResult(RESULT_CANCELED);
        dismissCarefullyProgressDialog();
        finish();
    }


    private void showToast(CharSequence text) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true);

    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
        mTranslation = mPossibleTranslations.get(which);
        returnTheTranslation();
    }


    @Override
    public void onCancel(DialogInterface dialog) {
        stopWorking();
    }


    private void stopWorking() {
        TaskOperations.stopTaskGracefully(mTranslationLoadPost);
        dismissCarefullyProgressDialog();
    }


    @Override
    protected void onPause() {
        super.onPause();
        stopWorking();
    }


    private void dismissCarefullyProgressDialog() {
        try {
            if ((progressDialog != null) && progressDialog.isShowing()) {
                    progressDialog.dismiss();
            }
        } catch (Exception e) {
            // nothing is done intentionally
        }
    }

    private int getSpinnerIndex(Spinner spinner, String myString){

        int index = 0;

        for (int i=0;i<spinner.getCount();i++){
            if (spinner.getItemAtPosition(i).equals(myString)){
                index = i;
            }
        }
        return index;
    }
}
