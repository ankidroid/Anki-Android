package com.ichi2.anki;

import java.io.File;
import java.io.IOException;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.multimediacard.AudioView;

public class BasicAudioFieldController extends FieldControllerBase implements IFieldController
{
	protected static final int ACTIVITY_RECORD_AUDIO = 1;

	/**
	 * This controller always return a temporary path where it writes the audio
	 */
	private String tempAudioPath;
	private String origAudioPath;

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

		origAudioPath = mField.getAudioPath();
		boolean audioFileExists = false;
		if (mField.hasTemporaryMedia())
		{
			tempAudioPath = origAudioPath;
		}
		else
		{
			File file = null;
			try
			{
				// TODO a good strategy is to store audio files inside
				// AnkiDroid/.tmp/
				// which can be cleaned periodically
				file = File.createTempFile("ankidroid_audiorec", ".3gp", Environment.getExternalStorageDirectory());
				tempAudioPath = file.getAbsolutePath();
			}
			catch (IOException e)
			{
				Log.e(AnkiDroidApp.TAG, "Could not create temporary audio file. " + e.getMessage());
				tempAudioPath = null;
			}
		}

		AudioView audioView = AudioView.createRecorderInstance(mActivity, R.drawable.av_play, R.drawable.av_pause,
				R.drawable.av_stop, R.drawable.av_rec, R.drawable.av_rec_stop, tempAudioPath);
		audioView.setOnRecordingFinishEventListener(new AudioView.OnRecordingFinishEventListener()
		{
			@Override
			public void onRecordingFinish(View v)
			{
				currentFilePath.setText("Recording done, you can preview it. Hit save after finish");
				mField.setAudioPath(tempAudioPath);
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

    @Override
    public void onDestroy()
    {
        // TODO Auto-generated method stub
        
    }
}
