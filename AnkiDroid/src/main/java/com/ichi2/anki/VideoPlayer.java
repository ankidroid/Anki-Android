package com.ichi2.anki;
import com.ichi2.libanki.Sound;

import android.app.Activity;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.VideoView;



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

public class VideoPlayer extends Activity implements android.view.SurfaceHolder.Callback {
    VideoView mVideoView;
    String mPath;
    Sound mSoundPlayer;

    /** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_player);
        mPath = getIntent().getStringExtra("path");
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mVideoView = (VideoView) findViewById(R.id.video_surface);
        mVideoView.getHolder().addCallback(this);
        mSoundPlayer = new Sound();
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSoundPlayer.playSound(mPath, new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                finish();
                MediaPlayer.OnCompletionListener originalListener = mSoundPlayer.getMediaCompletionListener();
                if (originalListener != null) {
                    originalListener.onCompletion(mp);
                }
            }
        }, mVideoView);
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        // TODO Auto-generated method stub
        
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSoundPlayer.stopSounds();
        finish();
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mSoundPlayer.notifyConfigurationChanged(mVideoView);
    }
    @Override
    public void onStop() {
        super.onStop();
    }
}