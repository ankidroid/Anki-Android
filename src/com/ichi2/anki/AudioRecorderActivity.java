package com.ichi2.anki;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.widget.LinearLayout;

import com.ichi2.anki.multimediacard.AudioView;

public class AudioRecorderActivity extends Activity
{
    private String mFileName;

    public AudioRecorderActivity()
    {
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/audiorecordtest.3gp";
    }

    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);

        AudioView audioView = new AudioView(this, R.drawable.av_play, R.drawable.av_pause, R.drawable.av_stop,
                R.drawable.av_rec, R.drawable.av_rec_stop);
        audioView.setAudioPath(mFileName);

        LinearLayout layout = new LinearLayout(this);
        layout.addView(audioView);
        setContentView(layout);
    }
}
