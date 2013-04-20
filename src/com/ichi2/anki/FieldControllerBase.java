package com.ichi2.anki;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public abstract class FieldControllerBase implements IFieldController
{

    protected EditFieldActivity mActivity;
    protected IField mField;
    protected    IMultimediaEditableNote mNote;
    protected int mIndex;

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
    public void setEditingActivity(EditFieldActivity activity)
    {
        mActivity = activity;
    };

}
