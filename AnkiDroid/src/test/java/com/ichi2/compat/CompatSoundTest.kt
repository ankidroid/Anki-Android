/*
 *  Copyright (c) 2021 Rudransh Dixit <dixitrudransh01@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.compat

import android.annotation.TargetApi
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@TargetApi(26)
class CompatSoundTest {
    private val compat: Compat = CompatHelper.compat
    private val audioManager: AudioManager = ApplicationProvider.getApplicationContext<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val afChangeListener: OnAudioFocusChangeListener = OnAudioFocusChangeListener { }

    @Test
    fun testRequestAudioFocus() {
        compat.requestAudioFocus(audioManager, afChangeListener, null)
    }

    @Test
    fun testAbandonAudioFocus() {
        compat.abandonAudioFocus(audioManager, afChangeListener, null)
    }
}
