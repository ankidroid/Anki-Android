package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicAudioFieldController implements IFieldController
{
    protected static final int ACTIVITY_RECORD_AUDIO = 1;
    protected Activity mActivity;

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
    public void createUI(LinearLayout layout, final Activity context)
    {
        mActivity = context;

        Button btnStartAudioRecordActivity = new Button(context);
        btnStartAudioRecordActivity.setText("Record Audio");
        btnStartAudioRecordActivity.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent audioRecorder = new Intent(mActivity, AudioRecorderActivity.class);
                mActivity.startActivityForResult(audioRecorder, ACTIVITY_RECORD_AUDIO);
            }
        });

        layout.addView(btnStartAudioRecordActivity, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode == Activity.RESULT_CANCELED)
        {
            Log.d(AnkiDroidApp.TAG, "Cancelled");
        }
        else if (requestCode == ACTIVITY_RECORD_AUDIO)
        {
            Log.d("MainActivity", "" + ACTIVITY_RECORD_AUDIO);
            IField value = (IField) data.getExtras().get("fieldValue");
            Log.d(AnkiDroidApp.TAG, value.getAudioPath());
        }
    }

    @Override
    public void onDone()
    {
        //
    }
}
