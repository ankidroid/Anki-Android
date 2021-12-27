package com.ichi2.compat

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [21, 26])
class CompatSoundTest {
    private var compat: Compat = CompatHelper.getCompat()
    private var audioManager: AudioManager = AnkiDroidApp.getInstance().applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var afChangeListener: OnAudioFocusChangeListener = OnAudioFocusChangeListener { _: Int -> }

    @Test
    fun testRequestAudioFocus() {
        compat.requestAudioFocus(audioManager, afChangeListener, null)
    }

    @Test
    fun testAbandonAudioFocus() {
        compat.requestAudioFocus(audioManager, afChangeListener, null)
    }
}
