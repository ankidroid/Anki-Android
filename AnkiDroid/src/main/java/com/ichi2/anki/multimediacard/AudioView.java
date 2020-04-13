/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;

import androidx.appcompat.widget.AppCompatImageButton;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.ichi2.anki.R;

import timber.log.Timber;

// Not designed for visual editing
@SuppressLint("ViewConstructor")
public class AudioView extends LinearLayout {
    protected String mAudioPath;

    protected PlayPauseButton mPlayPause = null;
    protected StopButton mStop = null;
    protected RecordButton mRecord = null;

    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;

    private OnRecordingFinishEventListener mOnRecordingFinishEventListener = null;

    private Status mStatus = Status.IDLE;

    private int mResPlayImage;
    private int mResPauseImage;
    private int mResStopImage;
    private int mResRecordImage;
    private int mResRecordStopImage;

    private Context mContext;

    enum Status {
        IDLE, // Default initial state
        INITIALIZED, // When datasource has been set
        PLAYING, PAUSED, STOPPED, // The different possible states once playing
                                  // has started
        RECORDING // The recorder being played status
    }


    public static AudioView createRecorderInstance(Context context, int resPlay, int resPause, int resStop,
            int resRecord, int resRecordStop, String audioPath) {
        return new AudioView(context, resPlay, resPause, resStop, resRecord, resRecordStop, audioPath);
    }


    private AudioView(Context context, int resPlay, int resPause, int resStop, String audioPath) {
        super(context);

        mContext = context;

        mResPlayImage = resPlay;
        mResPauseImage = resPause;
        mResStopImage = resStop;
        mAudioPath = audioPath;

        this.setOrientation(HORIZONTAL);

        mPlayPause = new PlayPauseButton(context);
        addView(mPlayPause, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mStop = new StopButton(context);
        addView(mStop, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }


    private void showToast(String msg) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(mContext, msg, duration);
        toast.show();
    }


    private String gtxt(int id) {
        return mContext.getText(id).toString();
    }


    private AudioView(Context context, int resPlay, int resPause, int resStop, int resRecord, int resRecordStop,
            String audioPath) {
        this(context, resPlay, resPause, resStop, audioPath);
        mResRecordImage = resRecord;
        mResRecordStopImage = resRecordStop;

        this.setOrientation(HORIZONTAL);

        mRecord = new RecordButton(context);
        addView(mRecord, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }


    public String getAudioPath() {
        return mAudioPath;
    }


    public void setOnRecordingFinishEventListener(OnRecordingFinishEventListener listener) {
        mOnRecordingFinishEventListener = listener;
    }


    public void notifyPlay() {
        mPlayPause.update();
        mStop.update();
        if (mRecord != null) {
            mRecord.update();
        }
    }


    public void notifyStop() {
        // Send state change signal to all buttons
        mPlayPause.update();
        mStop.update();
        if (mRecord != null) {
            mRecord.update();
        }
    }


    public void notifyPause() {
        mPlayPause.update();
        mStop.update();
        if (mRecord != null) {
            mRecord.update();
        }
    }


    public void notifyRecord() {
        mPlayPause.update();
        mStop.update();
        if (mRecord != null) {
            mRecord.update();
        }
    }


    public void notifyStopRecord() {
        if (mRecorder != null && mStatus == Status.RECORDING) {
            mRecorder.stop();
            mStatus = Status.IDLE;
            if (mOnRecordingFinishEventListener != null) {
                mOnRecordingFinishEventListener.onRecordingFinish(AudioView.this);
            }
        }
        mPlayPause.update();
        mStop.update();
        if (mRecord != null) {
            mRecord.update();
        }
    }

    public void notifyReleaseRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
        }
    }

    protected class PlayPauseButton extends AppCompatImageButton {
        private OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAudioPath == null) {
                    return;
                }

                switch (mStatus) {
                    case IDLE:
                        try {
                            mPlayer = new MediaPlayer();
                            mPlayer.setDataSource(getAudioPath());
                            mPlayer.setOnCompletionListener(mp -> {
                                mStatus = Status.STOPPED;
                                mPlayer.stop();
                                notifyStop();
                            });
                            mPlayer.prepare();
                            mPlayer.start();

                            setImageResource(mResPauseImage);
                            mStatus = Status.PLAYING;
                            notifyPlay();
                        } catch (Exception e) {
                            Timber.e(e);
                            showToast(gtxt(R.string.multimedia_editor_audio_view_playing_failed));
                            mStatus = Status.IDLE;
                        }
                        break;

                    case PAUSED:
                        // -> Play, continue playing
                        mStatus = Status.PLAYING;
                        setImageResource(mResPauseImage);
                        mPlayer.start();
                        notifyPlay();
                        break;

                    case STOPPED:
                        // -> Play, start from beginning
                        mStatus = Status.PLAYING;
                        setImageResource(mResPauseImage);
                        try {
                            mPlayer.prepare();
                            mPlayer.seekTo(0);
                        } catch (Exception e) {
                            Timber.e(e);
                        }
                        mPlayer.start();
                        notifyPlay();
                        break;

                    case PLAYING:
                        setImageResource(mResPlayImage);
                        mPlayer.pause();
                        mStatus = Status.PAUSED;
                        notifyPause();
                        break;

                    case RECORDING:
                        // this button should be disabled
                        break;
                    default:
                        break;
                }
            }
        };


        public PlayPauseButton(Context context) {
            super(context);
            setImageResource(mResPlayImage);

            setOnClickListener(onClickListener);
        }


        public void update() {
            switch (mStatus) {
                case IDLE:
                case STOPPED:
                    setImageResource(mResPlayImage);
                    setEnabled(true);
                    break;

                case RECORDING:
                    setEnabled(false);
                    break;

                default:
                    setEnabled(true);
                    break;
            }
        }
    }

    protected class StopButton extends AppCompatImageButton {
        private OnClickListener onClickListener = v -> {
            switch (mStatus) {
                case PAUSED:
                case PLAYING:
                    mPlayer.stop();
                    mStatus = Status.STOPPED;
                    notifyStop();
                    break;

                case IDLE:
                case STOPPED:
                case RECORDING:
                case INITIALIZED:
                default:
                    break;
            }
        };


        public StopButton(Context context) {
            super(context);
            setImageResource(mResStopImage);

            setOnClickListener(onClickListener);
        }


        public void update() {
            switch (mStatus) {
                case RECORDING:
                    setEnabled(false);
                    break;

                default:
                    setEnabled(true);
                    break;
            }
            // It doesn't need to update itself on any other state changes
        }

    }

    protected class RecordButton extends AppCompatImageButton {
        private OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Since mAudioPath is not compulsory, we check if it exists
                if (mAudioPath == null) {
                    return;
                }

                switch (mStatus) {
                    case IDLE: // If not already recorded or not already played
                    case STOPPED: // if already recorded or played
                        boolean highSampling = false;
                        try {
                            // try high quality AAC @ 44.1kHz / 192kbps first
                            // can throw IllegalArgumentException if codec isn't supported
                            mRecorder = initMediaRecorder();
                            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                            mRecorder.setAudioChannels(2);
                            mRecorder.setAudioSamplingRate(44100);
                            mRecorder.setAudioEncodingBitRate(192000);
                            // this can also throw IOException if output path is invalid
                            mRecorder.prepare();
                            mRecorder.start();
                            highSampling = true;
                        } catch (Exception e) {
                            // in all cases, fall back to low sampling
                        }

                        if (!highSampling) {
                            // if we are here, either the codec didn't work or output file was invalid
                            // fall back on default
                            try {
                                mRecorder = initMediaRecorder();
                                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

                                mRecorder.prepare();
                                mRecorder.start();

                            } catch (Exception e) {
                                // either output file failed or codec didn't work, in any case fail out
                                Timber.e("RecordButton.onClick() :: error recording to " + mAudioPath + "\n" +e.getMessage());
                                showToast(gtxt(R.string.multimedia_editor_audio_view_recording_failed));
                                mStatus = Status.STOPPED;
                                break;
                            }

                        }

                        mStatus = Status.RECORDING;
                        setImageResource(mResRecordImage);
                        notifyRecord();

                        break;

                    case RECORDING:
                        setImageResource(mResRecordStopImage);
                        notifyStopRecord();
                        break;

                    default:
                        break;
                }
            }

            private MediaRecorder initMediaRecorder() {
                MediaRecorder mr = new MediaRecorder();
                mr.setAudioSource(MediaRecorder.AudioSource.MIC);
                mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mStatus = Status.INITIALIZED;
                mr.setOutputFile(mAudioPath); // audioPath
                                              // could
                                              // change
                return mr;
            }
        };


        public RecordButton(Context context) {
            super(context);
            setImageResource(mResRecordStopImage);

            setOnClickListener(onClickListener);
        }


        public void update() {
            switch (mStatus) {
                case PLAYING:
                case PAUSED:
                    setEnabled(false);
                    break;

                default:
                    setEnabled(true);
                    break;
            }
        }
    }

    public interface OnRecordingFinishEventListener {
        void onRecordingFinish(View v);
    }
}
