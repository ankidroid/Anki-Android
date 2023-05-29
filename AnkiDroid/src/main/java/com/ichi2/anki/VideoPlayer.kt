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
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.VideoPlayer
import com.ichi2.themes.Themes
import timber.log.Timber

class VideoPlayer : Activity(), SurfaceHolder.Callback {
    private lateinit var mVideoView: VideoView
    private lateinit var videoPlayer: VideoPlayer
    private lateinit var path: String

    /** Called when the activity is first created.  */
    @Suppress("DEPRECATION") // #9332: UI Visibility -> Insets
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate")
        super.onCreate(savedInstanceState)
        Themes.disableXiaomiForceDarkMode(this)
        setContentView(R.layout.video_player)
        this.path = intent.getStringExtra("path").let { path ->
            if (path == null) {
                // #5911 - May happen if launched externally. Not possible inside AnkiDroid
                Timber.w("video path was null")
                showSnackbar(getString(R.string.video_creation_error), Snackbar.LENGTH_SHORT)
                finish()
                return
            }
            path
        }

        Timber.i("Video Player launched successfully")
        window.apply {
            setFlags(
                LayoutParams.FLAG_FULLSCREEN,
                LayoutParams.FLAG_FULLSCREEN
            )
            addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        mVideoView = findViewById(R.id.video_surface)
        videoPlayer = VideoPlayer(mVideoView)
        mVideoView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Timber.i("surfaceCreated")
        videoPlayer.play(path, { mp: MediaPlayer? ->
            finish()
            mediaCompletionListener?.onCompletion(mp)
        }, null)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        // intentionally blank: required for interface
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        videoPlayer.stopSounds()
        finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        videoPlayer.notifyConfigurationChanged()
    }

    companion object {
        /**
         * OnCompletionListener so that external video player can notify to play next sound
         */
        var mediaCompletionListener: MediaPlayer.OnCompletionListener? = null
    }
}
