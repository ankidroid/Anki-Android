package com.ichi2.compat;

import android.content.Context;
import android.media.AudioManager;

import com.ichi2.anki.AnkiDroidApp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.ext.junit.runners.AndroidJUnit4;


@RunWith(AndroidJUnit4.class)
@Config(sdk = { 21, 26 })
public class CompatSoundTest {
    CompatV26 cv26;
    AudioManager audioManager;
    AudioManager.OnAudioFocusChangeListener afChangeListener;

    @Before
    public void before() {
        cv26 = new CompatV26();
        audioManager = (AudioManager) AnkiDroidApp.getInstance().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        afChangeListener = focusChange -> {
        };
    }

    @Test
    public void testRequestAudioFocus() {

        cv26.requestAudioFocus(audioManager, afChangeListener, null);
    }


    @Test
    public void testAbandonAudioFocus() {
        cv26.requestAudioFocus(audioManager, afChangeListener, null);
    }
}