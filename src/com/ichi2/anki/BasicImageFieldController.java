package com.ichi2.anki;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

public class BasicImageFieldController implements IFieldController
{
    protected static final int ACTIVITY_SELECT_IMAGE = 1;
    protected static final int ACTIVITY_TAKE_PICTURE = 2;
    protected static final int IMAGE_PREVIEW_MAX_WIDTH = 100;

    protected FragmentActivity mActivity;
    protected Button mBtnGallery;
    protected Button mBtnCamera;
    protected ImageView mImagePreview;;

    IField mField;
    IMultimediaEditableNote mNote;
    private int mIndex;
    protected String mTempCameraImagePath;

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
    public void setFragmentActivity(FragmentActivity activity)
    {
        mActivity = activity;
    };

    @Override
    public void createUI(LinearLayout layout)
    {
        mImagePreview = new ImageView(mActivity);
        mImagePreview.setAdjustViewBounds(true);
        mImagePreview.setMaxHeight(IMAGE_PREVIEW_MAX_WIDTH);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mImagePreview.setLayoutParams(params);
        setPreviewImage(mField.getImagePath());

        mBtnGallery = new Button(mActivity);
        mBtnGallery.setText("From Gallery");
        mBtnGallery.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                mActivity.startActivityForResult(i, ACTIVITY_SELECT_IMAGE);
            }
        });

        mBtnCamera = new Button(mActivity);
        mBtnCamera.setText("From Camera");
        mBtnCamera.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                File image;
                try
                {
                    image = File.createTempFile("ankidroid_img", ".jpg");
                    mTempCameraImagePath = image.getPath();
                    Uri uriSavedImage = Uri.fromFile(image);

                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
                    mActivity.startActivityForResult(cameraIntent, ACTIVITY_TAKE_PICTURE);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });

        TextView textView = new TextView(mActivity);
        textView.setText("Current Image");
        layout.addView(textView, LinearLayout.LayoutParams.MATCH_PARENT);
        layout.addView(mImagePreview, LinearLayout.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnGallery, LinearLayout.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnCamera, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode == Activity.RESULT_CANCELED)
        {
            // Do Nothing.
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
        else if (requestCode == ACTIVITY_TAKE_PICTURE)
        {
            mField.setImagePath(mTempCameraImagePath);
            mField.setHasTemporaryMedia(true);
        }
        setPreviewImage(mField.getImagePath());
    }

    @Override
    public void onDone()
    {
        //
    }

    protected void setPreviewImage(String imagePath)
    {
        if (imagePath != null && !imagePath.equals(""))
        {
            mImagePreview.setImageURI(Uri.fromFile(new File(imagePath)));
        }
    }
}
