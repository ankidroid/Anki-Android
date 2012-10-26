package com.ichi2.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;

import com.ichi2.anki.ReadText;

import android.annotation.TargetApi;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/** Implementation of {@link Compat} for SDK level 15 */
@TargetApi(11)
public class CompatV15 extends CompatV11 implements Compat {
    @Override
    public void setTtsOnUtteranceProgressListener(TextToSpeech tts) {
    	tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
			@Override
			public void onDone(String arg0) {
				if (ReadText.sTextQueue.size() > 0) {
					String[] text = ReadText.sTextQueue.remove(0);
					ReadText.speak(text[0], text[1]);
				}
			}
			@Override
			public void onError(String arg0) {
			}
			@Override
			public void onStart(String arg0) {
			}
        	
        });

    }
}
