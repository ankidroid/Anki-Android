package com.ichi2.anki;

import android.support.v4.app.FragmentActivity;
import android.widget.LinearLayout;

import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicAudioFieldController implements IFieldController
{
    protected static final int ACTIVITY_RECORD_AUDIO = 1;
    protected FragmentActivity mActivity;

    IField mField;
    IMultimediaEditableNote mNote;
    private int mIndex;

    @Override
    public void setField(IField field)
    {
        mField = field;
    }

    @Override
    public void setNote(IMultimediaEditableNote note)
    {
        mNote = note;
    }

    @Override
    public void setFieldIndex(int index)
    {
        mIndex = index;
    }

    @Override
    public void createUI(LinearLayout layout)
    {
        AudioView audioView = new AudioView(mActivity, R.drawable.av_play, R.drawable.av_pause, R.drawable.av_stop,
                R.drawable.av_rec, R.drawable.av_rec_stop);

        layout.addView(audioView, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onDone()
    {
        //
    }

    @Override
    public void setFragmentActivity(FragmentActivity activity)
    {
        mActivity = activity;
    }
}
