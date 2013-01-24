package com.ichi2.anki.multimediacard;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class AudioView extends LinearLayout
{
	protected String mAudioPath;

	protected PlayPauseButton mPlayPause = null;
	protected StopButton mStop = null;
	protected RecordButton mRecord = null;

	private MediaRecorder mRecorder = null;
	private MediaPlayer mPlayer = null;

	OnRecordingFinishEventListener mOnRecordingFinishEventListener = null;

	private Status mStatus = Status.IDLE;

	int mResPlayImage;
	int mResPauseImage;
	int mResStopImage;
	int mResRecordImage;
	int mResRecordStopImage;

	enum Status
	{
		IDLE, // Default initial state
		INITIALIZED, // When datasource has been set
		PLAYING, PAUSED, STOPPED, // The different possible states once playing
								  // has started
		RECORDING // The recorder being played status
	}

	/**
	 * @param context
	 * 
	 *            Resources for images
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
		if (mStatus == Status.IDLE)
		{
			mAudioPath = audioPath;
		}
		else
		{
			throw new RuntimeException("Cannot set audio path after it has been initialized");
		}
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

	public void notifyPlay()
	{
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void notifyStop()
	{
		// Send state change signal to all buttons
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void notifyPause()
	{
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void notifyRecord()
	{
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
	}

	public void notifyStopRecord()
	{
		mPlayPause.update();
		mStop.update();
		if (mRecord != null)
			mRecord.update();
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
					case IDLE:
						try
						{
							setImageResource(mResPauseImage);
							mPlayer = new MediaPlayer();
							mPlayer.setDataSource(getAudioPath());
							mPlayer.setOnCompletionListener(new OnCompletionListener()
							{
								@Override
								public void onCompletion(MediaPlayer mp)
								{
									mStatus = Status.STOPPED;
									mPlayer.stop();
									notifyStop();
								}
							});
							mPlayer.prepare();
							mPlayer.start();
							mStatus = Status.PLAYING;
							notifyPlay();
						}
						catch (Exception e)
						{
							throw new RuntimeException(e);
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
						try
						{
							mPlayer.prepare();
							mPlayer.seekTo(0);
						}
						catch (Exception e)
						{
							throw new RuntimeException(e);
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
						mPlayer.stop();
						mStatus = Status.STOPPED;
						notifyStop();
						break;

					case IDLE:
					case STOPPED:
					case RECORDING:
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
				// Since mAudioPath is not compulsory, we check if it exists
				if (mAudioPath == null)
					return;

				switch (mStatus)
				{
					case IDLE: // If not already recorded or not already played
					case STOPPED: // if already recorded or played
						try
						{
							setImageResource(mResRecordImage);
							mRecorder = new MediaRecorder();
							mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
							mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
							mStatus = Status.INITIALIZED;
							mRecorder.setOutputFile(mAudioPath); // audioPath
																 // could
																 // change
							mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
							mRecorder.prepare();
							mRecorder.start();
							mStatus = Status.RECORDING;
							notifyRecord();
						}
						catch (Exception e)
						{
							throw new RuntimeException(e);
						}
						break;

					case RECORDING:
						setImageResource(mResRecordStopImage);
						mRecorder.stop();
						mStatus = Status.IDLE; // Back to idle, so if play
											   // pressed, initialize player
						notifyStopRecord();
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
