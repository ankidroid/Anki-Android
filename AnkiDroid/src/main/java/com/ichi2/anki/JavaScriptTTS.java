package com.ichi2.anki;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import com.ichi2.anki.LanguageUtils;

/**
 * Since it is assumed that only advanced users will use the JavaScript api, 
 * here, Android's TextToSpeech is converted for JavaScript almost as it is, giving priority to free behavior.
 * https://developer.android.com/reference/android/speech/tts/TextToSpeech
 */
public class JavaScriptTTS implements TextToSpeech.OnInitListener {

    private TextToSpeech mTts;
    private boolean mTtsOk;
    private static final Bundle mTtsParams = new Bundle();

    JavaScriptTTS(Context context) {
        mTts = new TextToSpeech(context, this);
    }

    @Override
    /** OnInitListener method to receive the TTS engine status */
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTtsOk = true;
        }
        else {
            mTtsOk = false;
        }
    }
    
    /**
     * A method to speak something
     * @param text Content to speak
     * @param queueMode 1 for QUEUE_ADD and 0 for QUEUE_FLUSH.
     * @return ERROR(-1) SUCCESS(0)
     */
    public int speak(String text, int queueMode) {
        return mTts.speak(text, queueMode, mTtsParams, "stringId");
    }

    /**
     * If only a string is given, set QUEUE_FLUSH to the default behavior.
     * @param text Content to speak
     * @return ERROR(-1) SUCCESS(0)
     */
    public int speak(String text) {
        return mTts.speak(text, TextToSpeech.QUEUE_FLUSH, mTtsParams, "stringId");
    }

    /**
     * Sets the text-to-speech language. 
     * The TTS engine will try to use the closest match to the specified language as represented by the Locale, but there is no guarantee that the exact same Locale will be used. 
     * @param loc Specifying the language to speak
     * @return  0 Denotes the language is available for the language by the locale, but not the country and variant.
     *     <li> 1 Denotes the language is available for the language and country specified by the locale, but not the variant.
     *     <li> 2 Denotes the language is available exactly as specified by the locale.
     *     <li> -1 Denotes the language data is missing.
     *     <li> -2 Denotes the language is not supported.
     */
    public int setLanguage(String loc) {
        // The Int values will be returned
        // Code indicating the support status for the locale. See LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA and LANG_NOT_SUPPORTED.
        return mTts.setLanguage(LanguageUtils.localeFromStringIgnoringScriptAndExtensions(loc));
    }


    /**
     * Sets the speech pitch for the TextToSpeech engine. This has no effect on any pre-recorded speech.
     * @param pitch float: Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the synthesized voice, greater values increase it.
     * @return ERROR(-1) SUCCESS(0)
     */
    public int setPitch(float pitch) {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return mTts.setPitch(pitch);
    }

    /**
     * 
     * @param speechRate Sets the speech rate. 1.0 is the normal speech rate. This has no effect on any pre-recorded speech.
     * @return ERROR(-1) SUCCESS(0)
     */
    public int setSpeechRate(float speechRate) {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return mTts.setSpeechRate(speechRate);
    }

    /**
     * Interrupts the current utterance (whether played or rendered to file) and discards other utterances in the queue.
     */
    public void stop() {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        mTts.stop();
    }

}