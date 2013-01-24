package com.ichi2.anki.multimediacard;

import java.io.IOException;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.ichi2.anki.AnkiDroidApp;

public class AudioView extends LinearLayout
{
	protected String mAudioPath;

	protected PlayPauseButton mPlayPause = null;
	protected StopButton mStop = null;
	protected RecordButton mRecord = null;

	private MediaRecorder mRecorder = null;
	private MediaPlayer mPlayer = null;

	OnRecordingFinishEventListener mOnRecordingFinishEventListener = null;

	private Status mStatus = Status.STOPPED;

	int mResPlayImage;
	int mResPauseImage;
	int mResStopImage;
	int mResRecordImage;
	int mResRecordStopImage;

	enum Status
	{
		PLAYING, PAUSED, STOPPED, RECORDING
	}

	/**
	 * @param context
	 *  
	 *  Resources for images
	 * @param resPlay
	 * @param resPause
	 * @param resStop
	 * 
	 * @param audioPath
	 * 
	 * @return
	 */
	public static AudioView createPlayerInstance(Context context, int resPlay, int resPause, int resStop,
			String audioPath)
	{
		AudioView audioView = new AudioView(context, resPlay, resPause, resStop, audioPath);
		return audioView;
	}

	public static AudioView createRecorderInstance(Context context, int resPlay, int resPause, int resStop,
			int resRecord, int resRecordStop, String audioPath)
	{
		return new AudioView(context, resPlay, resPause, resStop, resRecord, resRecordStop, audioPath);
	}

	private AudioView(Context context, int resPlay, int resPause, int resStop, String audioPath)
	{
		super(context);
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

	private AudioView(Context context, int resPlay, int resPause, int resStop, int resRecord, int resRecordStop,
			String audioPath)
	{
		this(context, resPlay, resPause, resStop, audioPath);
		mResRecordImage = resRecord;
		mResRecordStopImage = resRecordStop;

		this.setOrientation(HORIZONTAL);

		mRecord = new RecordButton(context);
		addView(mRecord, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	}

	public void setAudioPath(String audioPath)
	{
		mAudioPath = audioPath;
	}

	public String getAudioPath()
	{
		return mAudioPath;
	}

	public void setRecordButtonVisible(boolean isVisible)
	{
		if (isVisible)
		{
			mRecord.setVisibility(VISIBLE);
		}
		else
		{
			mRecord.setVisibility(INVISIBLE);
		}
	}

	public void setOnRecordingFinishEventListener(OnRecordingFinishEventListener listener)
	{
		mOnRecordingFinishEventListener = listener;
	}

	public void onPlay()
	{
		startPlaying();
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void onStop()
	{
		stopPlaying();
		// Send state change signal to all buttons
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void onPause()
	{
		pausePlaying();
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void onRecord()
	{
		startRecording();
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void onStopRecord()
	{
		stopRecording();
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	private void startPlaying()
	{
		try
		{
			if (mAudioPath == null || mAudioPath.equals(""))
			{
				Log.e(AnkiDroidApp.TAG, "Cannot find valid audioPath. Use setAudioPath().");
				return;
			}
			if (mPlayer == null)
			{
				mPlayer = new MediaPlayer();
			}
			mPlayer.setDataSource(mAudioPath);
			mPlayer.setOnPreparedListener(new OnPreparedListener()
                        {
                            
                            @Override
                            public void onPrepared(MediaPlayer mp)
                            {
                                mPlayer.start();
                            }
                        });
			mPlayer.setOnCompletionListener(new OnCompletionListener()
                        {
                                @Override
                                public void onCompletion(MediaPlayer mp)
                                {
                                        mStatus = Status.STOPPED;
                                        onStop();
                                }
                        });
			mPlayer.prepare();
			
		}
		catch (IOException e)
		{
			Log.e(AnkiDroidApp.TAG, "prepare() failed for file " + mAudioPath + ", " + e.getMessage());
		}
	}

	private void stopPlaying()
	{
		mPlayer.release();
		mPlayer = null;

	}

	private void pausePlaying()
	{
		mPlayer.pause();
	}

	private void startRecording()
	{
		try
		{
			if (mAudioPath == null || mAudioPath.equals(""))
			{
				Log.e(AnkiDroidApp.TAG, "Cannot find valid audioPath. Use setAudioPath().");
				return;
			}
			if (mRecorder == null)
			{
				mRecorder = new MediaRecorder();
				mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
				mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
				mRecorder.setOutputFile(mAudioPath); // audioPath could change
				mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			}

			// during lifetime
			mRecorder.prepare();
			mRecorder.start();
		}
		catch (IOException e)
		{
			Log.e(AnkiDroidApp.TAG, "prepare() failed for file " + mAudioPath);
		}
	}

	private void stopRecording()
	{
		mRecorder.stop();
		mRecorder.release();
		mRecorder = null;
	}

	protected class PlayPauseButton extends ImageButton
	{
		OnClickListener onClickListener = new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (mAudioPath == null)
					return;

				switch (mStatus)
				{
					case PAUSED:
					case STOPPED:
						setImageResource(mResPauseImage);
						mStatus = Status.PLAYING;
						onPlay();
						break;

					case PLAYING:
						setImageResource(mResPlayImage);
						mStatus = Status.PAUSED;
						onPause();
						break;

					case RECORDING:
						// this button should be disabled
				}
			}
		};

		public PlayPauseButton(Context context)
		{
			super(context);
			setImageResource(mResPlayImage);

			setOnClickListener(onClickListener);
		}

		public void update()
		{
			switch (mStatus)
			{
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

	protected class StopButton extends ImageButton
	{
		OnClickListener onClickListener = new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				switch (mStatus)
				{
					case PAUSED:
					case PLAYING:
						mStatus = Status.STOPPED;
						onStop();
						break;

					case STOPPED:
					case RECORDING:
						// do nothing
				}
			}
		};

		public StopButton(Context context)
		{
			super(context);
			setImageResource(mResStopImage);

			setOnClickListener(onClickListener);
		}

		public void update()
		{
			switch (mStatus)
			{
				case RECORDING:
					setEnabled(false);
					break;

				default:
					setEnabled(true);
			}
			// It doesn't need to update itself on any other state changes
		}

	}

	protected class RecordButton extends ImageButton
	{
		OnClickListener onClickListener = new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (mAudioPath == null)
					return;

				switch (mStatus)
				{
					case STOPPED:
						setImageResource(mResRecordImage);
						mStatus = Status.RECORDING;
						onRecord();
						break;

					case RECORDING:
						setImageResource(mResRecordStopImage);
						mStatus = Status.STOPPED;
						onStopRecord();
						if (mOnRecordingFinishEventListener != null)
						{
							mOnRecordingFinishEventListener.onRecordingFinish(AudioView.this);
						}
						break;

					default:
						// do nothing
				}
			}
		};

		public RecordButton(Context context)
		{
			super(context);
			setImageResource(mResRecordStopImage);

			setOnClickListener(onClickListener);
		}

		public void update()
		{
			switch (mStatus)
			{
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

	public interface OnRecordingFinishEventListener
	{
		public void onRecordingFinish(View v);
	}
}
