package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicTextFieldController implements IFieldController
{

    IField mField;
    IMultimediaEditableNote mNote;
    private int mIndex;
    private EditText mEditText;
    private FragmentActivity mActivity;

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
    public void setFragmentActivity(FragmentActivity activity) {
        mActivity = activity;
    };


    @Override
    public void createUI(LinearLayout layout)
    {
        mEditText = new EditText(mActivity);
        mEditText.setMinLines(3);
        mEditText.setText(mField.getText());
        layout.addView(mEditText, LinearLayout.LayoutParams.MATCH_PARENT);

        LinearLayout layoutTools = new LinearLayout(mActivity);
        layoutTools.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(layoutTools);

        createCloneButton(mActivity, layoutTools);

    }

    private void createCloneButton(Activity activity, LinearLayout layoutTools)
    {
        if (mNote.getNumberOfFields() > 1)
        {
            // Should be more than one text not empty fields for clone to make
            // sense
            int numTextFields = 0;
            for (int i = 0; i < mNote.getNumberOfFields(); ++i)
            {
                IField curField = mNote.getField(i);
                if (curField == null)
                {
                    continue;
                }

                if (curField.getType() != EFieldType.TEXT)
                {
                    continue;
                }

                if (curField.getText().length() == 0)
                {
                    continue;
                }

                ++numTextFields;
            }

            if (numTextFields < 2)
            {
                return;
            }

            Button btnOtherField = new Button(activity);
            // TODO Translation
            btnOtherField.setText("Clone");
            layoutTools.addView(btnOtherField);

            btnOtherField.setOnClickListener(new OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                    PickCloneDialogFragment fragment = new PickCloneDialogFragment();
                    fragment.show(mActivity.getSupportFragmentManager(), "pick.clone");
                }
            });

        }
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
