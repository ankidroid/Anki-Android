package com.ichi2.anki;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicAudioFieldController extends  FieldControllerBase implements IFieldController 
{
    
    @Override
    public void createUI(LinearLayout layout)
    {
        TextView tv = new TextView(mActivity);
        tv.setText("Path to file: " + mField.getAudioPath());
        layout.addView(tv);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDone()
    {
        // TODO Auto-generated method stub

    }

}
