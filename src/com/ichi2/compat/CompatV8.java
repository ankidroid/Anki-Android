
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;

/** Implementation of {@link Compat} for SDK level 8 */
@TargetApi(8)
public class CompatV8 extends CompatV7 implements Compat {

    /**
     * Listener to handle audio focus. Currently blank because we're not respecting losing focus from other apps.
     */
    private static OnAudioFocusChangeListener afChangeListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
        }
    };


    @Override
    public void requestAudioFocus(AudioManager audioManager) {
        audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }


    @Override
    public void abandonAudioFocus(AudioManager audioManager) {
        audioManager.abandonAudioFocus(afChangeListener);
    }

}
