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

import android.content.Context;
import android.media.MediaRecorder;

import com.ichi2.compat.CompatHelper;

import java.io.IOException;

import androidx.annotation.Nullable;
import timber.log.Timber;

public class AudioRecorder {

    @Nullable
    private MediaRecorder mRecorder = null;

    @Nullable
    private Runnable mOnRecordingInitialized;


    private MediaRecorder initMediaRecorder(Context context, String audioPath) {
        MediaRecorder mr = CompatHelper.getCompat().getMediaRecorder(context);
        mr.setAudioSource(MediaRecorder.AudioSource.MIC);
        mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        onRecordingInitialized();
        mr.setOutputFile(audioPath); // audioPath could change
        return mr;
    }


    private void onRecordingInitialized() {
        if (mOnRecordingInitialized != null) {
            mOnRecordingInitialized.run();
        }
    }

    public void startRecording(Context context, String audioPath) throws IOException {
        boolean highSampling = false;
        try {
            // try high quality AAC @ 44.1kHz / 192kbps first
            // can throw IllegalArgumentException if codec isn't supported
            mRecorder = initMediaRecorder(context, audioPath);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mRecorder.setAudioChannels(2);
            mRecorder.setAudioSamplingRate(44100);
            mRecorder.setAudioEncodingBitRate(192000);
            // this can also throw IOException if output path is invalid
            mRecorder.prepare();
            mRecorder.start();
            highSampling = true;
        } catch (Exception e) {
            Timber.w(e);
            // in all cases, fall back to low sampling
        }

        if (!highSampling) {
            // if we are here, either the codec didn't work or output file was invalid
            // fall back on default
            mRecorder = initMediaRecorder(context, audioPath);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            mRecorder.prepare();
            mRecorder.start();
        }
    }


    public void stopRecording() {
        if (mRecorder != null) {
            mRecorder.stop();
        }
    }


    public void setOnRecordingInitializedHandler(Runnable onRecordingInitialized) {
        this.mOnRecordingInitialized = onRecordingInitialized;
    }


    public void release() {
        if (mRecorder != null) {
            mRecorder.release();
        }
    }
}
