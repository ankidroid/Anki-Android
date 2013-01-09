package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicImageFieldController implements IFieldController
{
    protected static final int ACTIVITY_SELECT_IMAGE = 1;
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
    public void createUI(LinearLayout layout, final Activity activity)
    {
        mActivity = activity;

        Button btnSelectPhoto = new Button(activity);
        btnSelectPhoto.setText("Gallery");
        btnSelectPhoto.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                activity.startActivityForResult(i, ACTIVITY_SELECT_IMAGE);
            }
        });

        layout.addView(btnSelectPhoto, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode == Activity.RESULT_CANCELED)
        {
            Log.d("MainActivity", "Cancelled");
        }
        else if (requestCode == ACTIVITY_SELECT_IMAGE)
        {
            Uri selectedImage = data.getData();
            // Log.d(TAG, selectedImage.toString());
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = mActivity.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String filePath = cursor.getString(columnIndex);
            cursor.close();

            mField.setImagePath(filePath);
        }
    }

    @Override
    public void onDone()
    {
        //
    }
}
