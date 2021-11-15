/*
 *  Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 *  Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 *  Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 *  Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.multimediacard;

import android.media.MediaPlayer;

import java.io.IOException;

import androidx.annotation.Nullable;
import timber.log.Timber;

public class AudioPlayer {

    private MediaPlayer mPlayer;
    @Nullable private Runnable mOnStoppingListener;
    @Nullable private Runnable mOnStoppedListener;


    public void play(String audioPath) throws IOException {
        mPlayer = new MediaPlayer();
        mPlayer.setDataSource(audioPath);
        mPlayer.setOnCompletionListener(mp -> {
            onStopping();
            mPlayer.stop();
            onStopped();
        });
        mPlayer.prepare();
        mPlayer.start();
    }


    private void onStopped() {
        if (mOnStoppedListener == null) {
            return;
        }
        mOnStoppedListener.run();
    }


    private void onStopping() {
        if (mOnStoppingListener == null) {
            return;
        }
        mOnStoppingListener.run();
    }


    public void start() {
        mPlayer.start();
    }


    public void stop() {
        try {
            mPlayer.prepare();
            mPlayer.seekTo(0);
        } catch (Exception e) {
            Timber.e(e);
        }
    }


    public void pause() {
        mPlayer.pause();
    }


    public void setOnStoppingListener(Runnable listener) {
        this.mOnStoppingListener = listener;
    }


    public void setOnStoppedListener(Runnable listener) {
        this.mOnStoppedListener = listener;
    }
}
