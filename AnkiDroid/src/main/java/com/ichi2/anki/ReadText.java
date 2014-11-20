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
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import com.ichi2.themes.StyledDialog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ReadText {
    private static TextToSpeech mTts;
    private static ArrayList<Locale> availableTtsLocales = new ArrayList<Locale>();
    private static String mTextToSpeak;
    private static WeakReference<Context> mReviewer;
    private static long mDid;
    private static int mOrd;
    private static int mQuestionAnswer;
    public static final String NO_TTS = "0";
    public static ArrayList<String[]> sTextQueue = new ArrayList<String[]>();
    public static HashMap<String, String> mTtsParams;


    // private boolean mTtsReady = false;

    public static void speak(String text, String loc) {
        int result = mTts.setLanguage(new Locale(loc));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message)
                    +" ("+loc+")", Toast.LENGTH_LONG).show();
            Log.e(AnkiDroidApp.TAG, "Error loading locale " + loc);
        } else {
            if (mTts.isSpeaking()) {
                Log.v(AnkiDroidApp.TAG, "tts engine appears to be busy... clearing queue");
                stopTts();
                //Log.v(AnkiDroidApp.TAG, "tts text '" + text + "' added to queue for locale ("+loc+")");
                //sTextQueue.add(new String[] { text, loc });
            }
            Log.v(AnkiDroidApp.TAG, "tts text '" + text + "' to be played for locale ("+loc+")");
            mTts.speak(mTextToSpeak, TextToSpeech.QUEUE_FLUSH, mTtsParams);
        }
    }


    public static String getLanguage(long did, int ord, int qa) {
        return MetaDB.getLanguage(mReviewer.get(), did, ord, qa);
    }


    public static void textToSpeech(String text, long did, int ord, int qa) {
        mTextToSpeak = text;
        mQuestionAnswer = qa;
        mDid = did;
        mOrd = ord;
        Log.v(AnkiDroidApp.TAG, "ReadText.textToSpeech() method started for string '" + text + "'");
        // get the user's existing language preference
        String language = getLanguage(mDid, mOrd, mQuestionAnswer);
        Log.v(AnkiDroidApp.TAG, "ReadText.textToSpeech() method found language choice '" + language + "'");
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
        Resources res = mReviewer.get().getResources();
        final StyledDialog.Builder builder = new StyledDialog.Builder(mReviewer.get());
        if (availableTtsLocales.size() == 0) {
            // builder.setTitle(res.getString(R.string.no_tts_available_title));
            Log.e(AnkiDroidApp.TAG, "ReadText.textToSpeech() no TTS languages available");
            builder.setMessage(res.getString(R.string.no_tts_available_message));
            builder.setIcon(R.drawable.ic_dialog_alert);
            builder.setPositiveButton(res.getString(R.string.dialog_ok), null);
        } else {
            ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
            final ArrayList<String> dialogIds = new ArrayList<String>();
            builder.setTitle(R.string.select_locale_title);
            // Add option: "no tts"
            dialogItems.add(res.getString(R.string.tts_no_tts));
            dialogIds.add(NO_TTS);
            for (int i = 0; i < availableTtsLocales.size(); i++) {
                dialogItems.add(availableTtsLocales.get(i).getDisplayName());
                dialogIds.add(availableTtsLocales.get(i).getISO3Language());
            }
            String[] items = new String[dialogItems.size()];
            dialogItems.toArray(items);

            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String locale = dialogIds.get(which);
                    Log.v(AnkiDroidApp.TAG, "ReadText.textToSpeech() user chose locale '" + locale + "'");
                    if (!locale.equals(NO_TTS)) {
                        speak(mTextToSpeak, locale);
                    }
                    MetaDB.storeLanguage(mReviewer.get(), mDid, mOrd, mQuestionAnswer, locale);
                }
            });
        }
        // Show the dialog after short delay so that user gets a chance to preview the card
        final Handler handler = new Handler();
        final int delay = 500;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                builder.create().show();
            }
        }, delay);
    }


    public static void initializeTts(Context context) {
        // Store weak reference to Activity to prevent memory leak
        mReviewer = new WeakReference<Context>(context);
        // Create new TTS object and setup its onInit Listener
        mTts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    // build list of available languages
                    buildAvailableLanguages();
                    if (availableTtsLocales.size() > 0) {
                        // notify the reviewer that TTS has been initialized
                        Log.v(AnkiDroidApp.TAG, "TTS initialized and available languages found");
                        ((AbstractFlashcardViewer) mReviewer.get()).ttsInitialized();
                    } else {
                        Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message), Toast.LENGTH_LONG).show();
                        Log.e(AnkiDroidApp.TAG, "TTS initialized but no available languages found");
                    }
                } else {
                    Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message), Toast.LENGTH_LONG).show();
                }
                AnkiDroidApp.getCompat().setTtsOnUtteranceProgressListener(mTts);
            }
        });
        mTtsParams = new HashMap<String, String>();
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
                    Log.v(AnkiDroidApp.TAG, "ReadText.buildAvailableLanguages() :: " + loc.getDisplayName() + " not available (error code "+Integer.toString(retCode)+")");
                }
            } catch (IllegalArgumentException e) {
                Log.e(AnkiDroidApp.TAG, "Error checking if language " + loc.getDisplayName() + " available");
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
