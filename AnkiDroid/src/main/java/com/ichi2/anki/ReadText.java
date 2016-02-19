/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;

import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.compat.CompatHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import timber.log.Timber;

public class ReadText {
    private static TextToSpeech mTts;
    private static ArrayList<Locale> availableTtsLocales = new ArrayList<>();
    private static String mTextToSpeak;
    private static WeakReference<Context> mReviewer;
    private static long mDid;
    private static int mOrd;
    private static int mQuestionAnswer;
    public static final String NO_TTS = "0";
    public static ArrayList<String[]> sTextQueue = new ArrayList<>();
    public static HashMap<String, String> mTtsParams;


    // private boolean mTtsReady = false;

    public static void speak(String text, String loc) {
        int result = mTts.setLanguage(new Locale(loc));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message)
                    +" ("+loc+")", Toast.LENGTH_LONG).show();
            Timber.e("Error loading locale " + loc);
        } else {
            if (mTts.isSpeaking()) {
                Timber.d("tts engine appears to be busy... clearing queue");
                stopTts();
                //sTextQueue.add(new String[] { text, loc });
            }
            Timber.d("tts text '%s' to be played for locale (%s)",text, loc);
            mTts.speak(mTextToSpeak, TextToSpeech.QUEUE_FLUSH, mTtsParams);
        }
    }


    public static String getLanguage(long did, int ord, int qa) {
        return MetaDB.getLanguage(mReviewer.get(), did, ord, qa);
    }


    /**
     * Ask the user what language they want.
     *
     * @param text The text to be read
     * @param did The deck id
     * @param ord The card template ordinal
     * @param qa The card question or card answer
     */
    public static void selectTts(String text, long did, int ord, int qa) {
        mTextToSpeak = text;
        mQuestionAnswer = qa;
        mDid = did;
        mOrd = ord;
        Resources res = mReviewer.get().getResources();
        final MaterialDialog.Builder builder = new MaterialDialog.Builder(mReviewer.get());
        // Build the language list if it's empty
        if (availableTtsLocales.isEmpty()) {
            buildAvailableLanguages();
        }
        if (availableTtsLocales.size() == 0) {
            Timber.w("ReadText.textToSpeech() no TTS languages available");
            builder.content(res.getString(R.string.no_tts_available_message))
                    .iconAttr(R.attr.dialogErrorIcon)
                    .positiveText(res.getString(R.string.dialog_ok));
        } else {
            ArrayList<CharSequence> dialogItems = new ArrayList<>();
            final ArrayList<String> dialogIds = new ArrayList<>();
            // Add option: "no tts"
            dialogItems.add(res.getString(R.string.tts_no_tts));
            dialogIds.add(NO_TTS);
            for (int i = 0; i < availableTtsLocales.size(); i++) {
                dialogItems.add(availableTtsLocales.get(i).getDisplayName());
                dialogIds.add(availableTtsLocales.get(i).getISO3Language());
            }
            String[] items = new String[dialogItems.size()];
            dialogItems.toArray(items);

            builder.title(res.getString(R.string.select_locale_title))
                    .items(items)
                    .itemsCallback(new MaterialDialog.ListCallback() {
                        @Override
                        public void onSelection(MaterialDialog materialDialog, View view, int which,
                                                CharSequence charSequence) {
                            String locale = dialogIds.get(which);
                            Timber.d("ReadText.selectTts() user chose locale '%s'", locale);
                            if (!locale.equals(NO_TTS)) {
                                speak(mTextToSpeak, locale);
                            }
                            String language = getLanguage(mDid, mOrd, mQuestionAnswer);
                            if (language.equals("")) { // No language stored
                                MetaDB.storeLanguage(mReviewer.get(), mDid, mOrd, mQuestionAnswer, locale);
                            } else {
                                MetaDB.updateLanguage(mReviewer.get(), mDid, mOrd, mQuestionAnswer, locale);
                            }

                        }
                    });
        }
        // Show the dialog after short delay so that user gets a chance to preview the card
        final Handler handler = new Handler();
        final int delay = 500;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                builder.build().show();
            }
        }, delay);
    }


    public static void textToSpeech(String text, long did, int ord, int qa) {
        mTextToSpeak = text;
        mQuestionAnswer = qa;
        mDid = did;
        mOrd = ord;
        Timber.d("ReadText.textToSpeech() method started for string '%s'", text);
        // get the user's existing language preference
        String language = getLanguage(mDid, mOrd, mQuestionAnswer);
        Timber.d("ReadText.textToSpeech() method found language choice '%s'", language);
        // rebuild the language list if it's empty
        if (availableTtsLocales.isEmpty()) {
            buildAvailableLanguages();
        }
        // Check, if stored language is available
        for (int i = 0; i < availableTtsLocales.size(); i++) {
            if (language.equals(NO_TTS)) {
                // user has chosen not to read the text
                return;
            } else if (language.equals(availableTtsLocales.get(i).getISO3Language())) {
                speak(mTextToSpeak, language);
                return;
            }
        }

        // Otherwise ask the user what language they want to use
        selectTts(mTextToSpeak, mDid, mOrd, mQuestionAnswer);
    }


    public static void initializeTts(Context context) {
        // Store weak reference to Activity to prevent memory leak
        mReviewer = new WeakReference<>(context);
        // Create new TTS object and setup its onInit Listener
        mTts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    // build list of available languages
                    buildAvailableLanguages();
                    if (availableTtsLocales.size() > 0) {
                        // notify the reviewer that TTS has been initialized
                        Timber.d("TTS initialized and available languages found");
                        ((AbstractFlashcardViewer) mReviewer.get()).ttsInitialized();
                    } else {
                        Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message), Toast.LENGTH_LONG).show();
                        Timber.w("TTS initialized but no available languages found");
                    }
                } else {
                    Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message), Toast.LENGTH_LONG).show();
                }
                CompatHelper.getCompat().setTtsOnUtteranceProgressListener(mTts);
            }
        });
        mTtsParams = new HashMap<>();
        mTtsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "stringId");
        // Show toast that it's getting initialized, as it can take a while before the sound plays the first time
        Toast.makeText(context, context.getString(R.string.initializing_tts), Toast.LENGTH_LONG).show();
    }

    public static void buildAvailableLanguages() {
        availableTtsLocales.clear();
        Locale[] systemLocales = Locale.getAvailableLocales();
        for (Locale loc : systemLocales) {
            try {
                int retCode = mTts.isLanguageAvailable(loc);
                if (retCode >= TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    availableTtsLocales.add(loc);
                } else {
                    Timber.v("ReadText.buildAvailableLanguages() :: %s  not available (error code %d)", loc.getDisplayName(), retCode);
                }
            } catch (IllegalArgumentException e) {
                Timber.e("Error checking if language " + loc.getDisplayName() + " available");
            }
        }
    }


    public static void releaseTts() {
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
    }


    public static void stopTts() {
        if (mTts != null) {
            if (sTextQueue != null) {
                sTextQueue.clear();
            }
            mTts.stop();
        }
    }
}
