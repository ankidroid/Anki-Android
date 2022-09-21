/****************************************************************************************
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki

import android.app.Activity
import android.content.res.Configuration
import android.media.MediaPlayer
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.WindowManager.LayoutParams
import android.widget.VideoView
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.libanki.Sound
import com.ichi2.themes.Themes
import timber.log.Timber

class VideoPlayer : Activity(), SurfaceHolder.Callback {
    private lateinit var mVideoView: VideoView
    private val mSoundPlayer: Sound = Sound()
    private var mPath: String? = null

    /** Called when the activity is first created.  */
    @Suppress("DEPRECATION") // #9332: UI Visibility -> Insets
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate")
        super.onCreate(savedInstanceState)
        Themes.disableXiaomiForceDarkMode(this)
        setContentView(R.layout.video_player)
        mPath = intent.getStringExtra("path")
        Timber.i("Video Player intent had path: %s", mPath)
        window.setFlags(
            LayoutParams.FLAG_FULLSCREEN,
            LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
        mVideoView = findViewById(R.id.video_surface)
        mVideoView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Timber.i("surfaceCreated")
        if (mPath == null) {
            // #5911 - path shouldn't be null. I couldn't determine why this happens.
            CrashReportService.sendExceptionReport("Video: mPath was unexpectedly null", "VideoPlayer surfaceCreated")
            Timber.e("path was unexpectedly null")
            showThemedToast(this, getString(R.string.video_creation_error), true)
            finish()
            return
        }
        mSoundPlayer.playSound(mPath!!, { mp: MediaPlayer? ->
            finish()

            val originalListener = Sound.mediaCompletionListener
            originalListener?.onCompletion(mp)
        }, mVideoView, null)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        // TODO Auto-generated method stub
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mSoundPlayer.stopSounds()
        finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mSoundPlayer.notifyConfigurationChanged(mVideoView)
    }

    public override fun onStop() {
        super.onStop()
    }
}
