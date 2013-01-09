package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicTextFieldController implements IFieldController
{

    IField mField;
    IMultimediaEditableNote mNote;
    private int mIndex;
    private EditText mEditText;

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
    public void createUI(LinearLayout layout, Activity context)
    {
        mEditText = new EditText(context);
        mEditText.setMinLines(3);
        mEditText.setText(mField.getText());
        layout.addView(mEditText, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // nothing
    }

    @Override
    public void onDone()
    {
        mField.setText(mEditText.getText().toString());
    }

}
