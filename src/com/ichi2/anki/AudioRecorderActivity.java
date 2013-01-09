package com.ichi2.anki;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.impl.AudioField;

public class AudioRecorderActivity extends Activity
{
    private static final String LOG_TAG = "AudioRecordTest";
    private static String mFileName = null;
    private boolean hasAudioFile = false;

    private RecordButton mRecordButton = null;
    private MediaRecorder mRecorder = null;

    private PlayButton mPlayButton = null;
    private MediaPlayer mPlayer = null;

    private GoBackButton mGoBackButton = null;
    private SaveButton mSaveButton = null;

    private void onRecord(boolean start)
    {
        if (start)
        {
            hasAudioFile = true;
            startRecording();
        }
        else
        {
            stopRecording();
        }
        setButtonsStatusRecording(start);
    }

    private void onPlay(boolean start)
    {
        if (start)
        {
            startPlaying();
        }
        else
        {
            stopPlaying();
        }
        setButtonsStatusPlaying(start);
    }

    private void onSave()
    {
        if (hasAudioFile)
        {
            IField value = new AudioField();
            value.setAudioPath(mFileName);

            Intent intent = new Intent();
            intent.putExtra("fieldValue", value);

            setResult(RESULT_OK, intent);
            finish();
        }
    }

    private void startPlaying()
    {
        mPlayer = new MediaPlayer();
        try
        {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.setOnCompletionListener(new OnCompletionListener()
            {
                @Override
                public void onCompletion(MediaPlayer mp)
                {
                    mPlayButton.setPlaying(false);
                }
            });
            mPlayer.start();
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void stopPlaying()
    {
        mPlayer.release();
        mPlayer = null;
    }

    private void startRecording()
    {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try
        {
            mRecorder.prepare();
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording()
    {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    class RecordButton extends Button
    {
        boolean mStartRecording = true;

        OnClickListener clicker = new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setRecording(mStartRecording);
                onRecord(mStartRecording);
                mStartRecording = !mStartRecording;
            }
        };

        public RecordButton(Context ctx)
        {
            super(ctx);
            setRecording(false);
            setOnClickListener(clicker);
        }

        protected void setRecording(boolean state)
        {
            if (state)
            {
                setText("Stop recording");
            }
            else
            {
                setText("Start recording");
            }
        }
    }

    class PlayButton extends Button
    {
        boolean mStartPlaying = true;

        OnClickListener clicker = new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setPlaying(mStartPlaying);
            }
        };

        public PlayButton(Context ctx)
        {
            super(ctx);
            setText("Start Playing");
            setOnClickListener(clicker);
        }

        protected void setPlaying(boolean state)
        {
            onPlay(state);
            if (state)
            {
                setText("Stop playing");
            }
            else
            {
                setText("Start playing");
            }
            mStartPlaying = !state;
        }
    }

    public class GoBackButton extends Button
    {
        OnClickListener listener = new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setResult(RESULT_CANCELED);
                finish();
            }
        };

        public GoBackButton(Context ctx)
        {
            super(ctx);
            setText("Cancel");
            setOnClickListener(listener);
        }
    }

    public class SaveButton extends Button
    {
        OnClickListener listener = new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onSave();
            }
        };

        public SaveButton(Context ctx)
        {
            super(ctx);
            setText("Save");
            setOnClickListener(listener);
        }
    }

    public AudioRecorderActivity()
    {
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/audiorecordtest.3gp";
    }

    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);
        // Hide status bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        RelativeLayout layout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT);
        layout.setLayoutParams(layoutParams);

        mRecordButton = new RecordButton(this);
        mRecordButton.setId(1);
        RelativeLayout.LayoutParams params1 = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mPlayButton = new PlayButton(this);
        mPlayButton.setId(2);
        RelativeLayout.LayoutParams params2 = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params2.addRule(RelativeLayout.BELOW, mRecordButton.getId());

        mGoBackButton = new GoBackButton(this);
        mGoBackButton.setId(3);
        RelativeLayout.LayoutParams params3 = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params3.addRule(RelativeLayout.BELOW, mPlayButton.getId());

        mSaveButton = new SaveButton(this);
        mSaveButton.setId(4);
        RelativeLayout.LayoutParams params4 = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params4.addRule(RelativeLayout.BELOW, mGoBackButton.getId());

        layout.addView(mRecordButton, params1);
        layout.addView(mPlayButton, params2);
        layout.addView(mGoBackButton, params3);
        layout.addView(mSaveButton, params4);

        // mGoBackButton = new GoBackButton(this);
        // layout.addView(mGoBackButton, new
        // RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        // ViewGroup.LayoutParams.WRAP_CONTENT));
        //
        // mSaveButton = new SaveButton(this);
        // layout.addView(mSaveButton, new
        // RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        // ViewGroup.LayoutParams.WRAP_CONTENT));

        setInitialButtonsStatus();
        setContentView(layout);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mRecorder != null)
        {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null)
        {
            mPlayer.release();
            mPlayer = null;
        }
    }

    protected void setInitialButtonsStatus()
    {
        mPlayButton.setEnabled(false);
        mSaveButton.setEnabled(false);
    }

    protected void setButtonsStatusRecording(boolean start)
    {
        if (start)
        {
            mRecordButton.setEnabled(true);
            mPlayButton.setEnabled(false);
            mGoBackButton.setEnabled(false);
            mSaveButton.setEnabled(false);
        }
        else
        {
            mRecordButton.setEnabled(true);
            mPlayButton.setEnabled(true);
            mGoBackButton.setEnabled(true);
            mSaveButton.setEnabled(true);
        }
    }

    protected void setButtonsStatusPlaying(boolean start)
    {
        if (start)
        {
            mRecordButton.setEnabled(false);
            mPlayButton.setEnabled(true);
            mGoBackButton.setEnabled(false);
            mSaveButton.setEnabled(false);
        }
        else
        {
            mRecordButton.setEnabled(true);
            mPlayButton.setEnabled(true);
            mGoBackButton.setEnabled(true);
            mSaveButton.setEnabled(true);
        }
    }
}
