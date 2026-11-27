// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.libanki.SoundOrVideoTag
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class SoundTagPlayerTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `denied audio focus stops the media`() =
        runTest {
            ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1, 0) }
            val audioManager = appContext.getSystemService(AudioManager::class.java)
            shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
            val player = SoundTagPlayer(soundUriBase = "file:///", videoPlayer = VideoPlayer { })

            val exception =
                assertFailsWith<MediaException> {
                    player.play(SoundOrVideoTag("a.mp3"), mediaErrorListener = null)
                }

            assertEquals(MediaErrorBehavior.STOP_MEDIA, exception.continuationBehavior)
        }
}
