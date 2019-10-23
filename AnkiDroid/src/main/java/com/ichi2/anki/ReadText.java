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
import android.net.Uri;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;

import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.ichi2.compat.Compat;
import com.ichi2.compat.CompatHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
    private static Compat compat = CompatHelper.getCompat();
    private static Object mTtsParams = compat.initTtsParams();


    public static void speak(String text, String loc, int queueMode) {
        int result = mTts.setLanguage(localeFromStringIgnoringScriptAndExtensions(loc));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message)
                    + " (" + loc + ")", Toast.LENGTH_LONG).show();
            Timber.e("Error loading locale " + loc);
        } else {
            if (mTts.isSpeaking() && queueMode == TextToSpeech.QUEUE_FLUSH) {
                Timber.d("tts engine appears to be busy... clearing queue");
                stopTts();
                //sTextQueue.add(new String[] { text, loc });
            }
            Timber.d("tts text '%s' to be played for locale (%s)", text, loc);
            compat.speak(mTts, mTextToSpeak, queueMode, mTtsParams, "stringId");
        }
    }

    
    public static String getLanguage(long did, int ord, int qa) {
        return MetaDB.getLanguage(mReviewer.get(), did, ord, qa);
    }


    /**
     * Ask the user what language they want.
     *
     * @param text The text to be read
     * @param did  The deck id
     * @param ord  The card template ordinal
     * @param qa   The card question or card answer
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
                                speak(mTextToSpeak, locale, TextToSpeech.QUEUE_FLUSH);
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

    /**
     * Read a card side using a TTS service.
     *
     * @param cardSide         Card side to be read; Sound.SOUNDS_QUESTION or Sound.SOUNDS_ANSWER.
     * @param cardSideContents Contents of the card side to be read, in HTML format. If it contains
     *                         any &lt;tts service="android"&gt; elements, only their contents is
     *                         read; otherwise, all text is read. See TtsParser for more details.
     * @param did              Index of the deck containing the card.
     * @param ord              The card template ordinal.
     */
    public static void readCardSide(int cardSide, String cardSideContents, long did, int ord) {
        boolean isFirstText = true;
        for (TtsParser.LocalisedText textToRead : TtsParser.getTextsToRead(cardSideContents)) {
            if (!textToRead.getText().isEmpty()) {
                textToSpeech(textToRead.getText(), did, ord, cardSide,
                        textToRead.getLocaleCode(),
                        isFirstText ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD);
                isFirstText = false;
            }
        }
    }

    /**
     * Read the given text using an appropriate TTS voice.
     * <p>
     * The voice is chosen as follows:
     * <p>
     * 1. If localeCode is a non-empty string representing a locale in the format returned
     * by Locale.toString(), and a voice matching the language of this locale (and ideally,
     * but not necessarily, also the country and variant of the locale) is available, then this
     * voice is used.
     * 2. Otherwise, if the database contains a saved language for the given 'did', 'ord' and 'qa'
     * arguments, and a TTS voice matching that language is available, then this voice is used
     * (unless the saved language is NO_TTS, in which case the text is not read at all).
     * 3. Otherwise, the user is asked to select a language from among those for which a voice is
     * available.
     *
     * @param queueMode TextToSpeech.QUEUE_ADD or TextToSpeech.QUEUE_FLUSH.
     */
    private static void textToSpeech(String text, long did, int ord, int qa, String localeCode,
                                     int queueMode) {
        mTextToSpeak = text;
        mQuestionAnswer = qa;
        mDid = did;
        mOrd = ord;
        Timber.d("ReadText.textToSpeech() method started for string '%s', locale '%s'", text, localeCode);

        final String originalLocaleCode = localeCode;

        if (!localeCode.isEmpty()) {
            if (!isLanguageAvailable(localeCode)) {
                localeCode = "";
            }
        }
        if (localeCode.isEmpty()) {
            // get the user's existing language preference
            localeCode = getLanguage(mDid, mOrd, mQuestionAnswer);
            Timber.d("ReadText.textToSpeech() method found language choice '%s'", localeCode);
        }

        if (localeCode.equals(NO_TTS)) {
            // user has chosen not to read the text
            return;
        }
        if (!localeCode.isEmpty() && isLanguageAvailable(localeCode)) {
            speak(mTextToSpeak, localeCode, queueMode);
            return;
        }

        // Otherwise ask the user what language they want to use
        if (!originalLocaleCode.isEmpty()) {
            // (after notifying them first that no TTS voice was found for the locale
            // they originally requested)
            Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message)
                    + " (" + originalLocaleCode + ")", Toast.LENGTH_LONG).show();
        }
        selectTts(mTextToSpeak, mDid, mOrd, mQuestionAnswer);
    }

    /**
     * Convert a string representation of a locale, in the format returned by Locale.toString(),
     * into a Locale object, disregarding any script and extensions fields (i.e. using solely the
     * language, country and variant fields).
     * <p>
     * Returns a Locale object constructed from an empty string if the input string is null, empty
     * or contains more than 3 fields separated by underscores.
     */
    private static Locale localeFromStringIgnoringScriptAndExtensions(String localeCode) {
        if (localeCode == null) {
            return new Locale("");
        }

        localeCode = stripScriptAndExtensions(localeCode);

        String[] fields = localeCode.split("_");
        switch (fields.length) {
            case 1:
                return new Locale(fields[0]);
            case 2:
                return new Locale(fields[0], fields[1]);
            case 3:
                return new Locale(fields[0], fields[1], fields[2]);
            default:
                return new Locale("");
        }
    }

    private static String stripScriptAndExtensions(String localeCode) {
        int hashPos = localeCode.indexOf('#');
        if (hashPos >= 0) {
            localeCode = localeCode.substring(0, hashPos);
        }
        return localeCode;
    }

    /**
     * Returns true if the TTS engine supports the language of the locale represented by localeCode
     * (which should be in the format returned by Locale.toString()), false otherwise.
     */
    private static boolean isLanguageAvailable(String localeCode) {
        return mTts.isLanguageAvailable(localeFromStringIgnoringScriptAndExtensions(localeCode)) >=
                TextToSpeech.LANG_AVAILABLE;
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
                    mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onDone(String arg0) {
                            if (ReadText.sTextQueue.size() > 0) {
                                String[] text = ReadText.sTextQueue.remove(0);
                                ReadText.speak(text[0], text[1], TextToSpeech.QUEUE_FLUSH);
                            }
                        }
                        @Override
                        @Deprecated
                        public void onError(String utteranceId) {
                            Timber.v("Andoid TTS failed. Check logcat for error. Indicates a problem with Android TTS engine.");

                            final Uri helpUrl = Uri.parse(mReviewer.get().getString(R.string.link_faq_tts));
                            final AnkiActivity ankiActivity = (AnkiActivity) mReviewer.get();
                            ankiActivity.mayOpenUrl(helpUrl);
                            UIUtils.showSnackbar(ankiActivity, R.string.no_tts_available_message, false, R.string.help,
                                    v -> ankiActivity.openUrl(helpUrl), ankiActivity.findViewById(R.id.root_layout),
                                    new Snackbar.Callback());
                        }
                        @Override
                        public void onStart(String arg0) {
                            // no nothing
                        }
                    });
                } else {
                    Toast.makeText(mReviewer.get(), mReviewer.get().getString(R.string.no_tts_available_message), Toast.LENGTH_LONG).show();
                    Timber.w("TTS not successfully initialized");
                }
            }
        });
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
