package com.ichi2.anki;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

// Since it is assumed that only advanced users will use the JavaScript api, 
// here, Android's TextToSpeech is converted for JavaScript almost as it is, giving priority to free behavior.
// https://developer.android.com/reference/android/speech/tts/TextToSpeech
//
// 

public class JavaScriptTTS implements TextToSpeech.OnInitListener {

    private TextToSpeech mTts;
    private boolean mTtsOk;
    private static final Bundle mTtsParams = new Bundle();

    //The constructor will create a TextToSpeech instance.
    JavaScriptTTS(Context context) {
        mTts = new TextToSpeech(context, this);
    }

    @Override
    //OnInitListener method to receive the TTS engine status
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTtsOk = true;
        }
        else {
            mTtsOk = false;
        }
    }

    // A method to speak something
    // The QueMode value is 1 for QUEUE_ADD and 0 for QUEUE_FLUSH.
    public int speak(String text, int queueMode) {
        return mTts.speak(text, queueMode, mTtsParams, "stringId");
    }

    // If only a string is given, set QUEUE_FLUSH to the default behavior.
    public int speak(String text) {
        return mTts.speak(text, TextToSpeech.QUEUE_FLUSH, mTtsParams, "stringId");
    }

    // Sets the text-to-speech language. 
    //The TTS engine will try to use the closest match to the specified language as represented by the Locale, but there is no guarantee that the exact same Locale will be used. 
    public int setLanguage(String loc) {
        // The Int values will be returned
        // Code indicating the support status for the locale. See LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA and LANG_NOT_SUPPORTED.
        return mTts.setLanguage(localeFromStringIgnoringScriptAndExtensions(loc));
    }

    // Sets the speech pitch for the TextToSpeech engine. This has no effect on any pre-recorded speech.
    // float: Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the synthesized voice, greater values increase it.
    public int setPitch(float pitch) {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return mTts.setPitch(pitch);
    }

    // Sets the speech rate. This has no effect on any pre-recorded speech.
    public int setSpeechRate(float speechRate) {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return mTts.setSpeechRate(speechRate);
    }

    // Interrupts the current utterance (whether played or rendered to file) and discards other utterances in the queue.
    public void stop() {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        mTts.stop();
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

}