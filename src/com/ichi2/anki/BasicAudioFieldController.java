package com.ichi2.anki;

import android.content.Intent;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicAudioFieldController extends FieldControllerBase implements IFieldController
{
    protected static final int ACTIVITY_RECORD_AUDIO = 1;

  
    @Override
    public void createUI(LinearLayout layout)
    {
        TextView label = new TextView(mActivity);
        label.setText("You can play existing sound or record a new sound");
        layout.addView(label, LinearLayout.LayoutParams.MATCH_PARENT);

        final TextView currentFilePath = new TextView(mActivity);
        if (mField.getAudioPath() == null || mField.getAudioPath().equals(""))
        {
            currentFilePath.setText("No file selected");
        }
        else
        {
            currentFilePath.setText("Current file :" + mField.getAudioPath());
        }
        layout.addView(currentFilePath, LinearLayout.LayoutParams.MATCH_PARENT);

        AudioView audioView = new AudioView(mActivity, R.drawable.av_play, R.drawable.av_pause, R.drawable.av_stop,
                R.drawable.av_rec, R.drawable.av_rec_stop);
//        audioView.setAudioPath(Environment.getExternalStorageDirectory() + "/ankidroid_temp_recaudio.3gp");
        audioView.setAudioPath(mField.getAudioPath());
        audioView.setOnRecordingFinishEventListener(new AudioView.OnRecordingFinishEventListener()
        {
            @Override
            public void onRecordingFinish(View v)
            {
                currentFilePath.setText("Recording done, you can preview it. Hit save after finish");
                mField.setAudioPath(Environment.getExternalStorageDirectory() + "/ankidroid_temp_recaudio.3gp");
                mField.setHasTemporaryMedia(true);
            }
        });
        layout.addView(audioView, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onDone()
    {
        //
    }

   
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // TODO Auto-generated method stub

    }
}
