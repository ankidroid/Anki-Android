package com.ichi2.anki;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.ichi2.utils.BitmapUtil;
import com.ichi2.utils.ExifUtil;

public class BasicImageFieldController extends FieldControllerBase implements IFieldController
{
    protected static final int ACTIVITY_SELECT_IMAGE = 1;
    protected static final int ACTIVITY_TAKE_PICTURE = 2;
    protected static final int IMAGE_PREVIEW_MAX_WIDTH = 100;

    protected Button mBtnGallery;
    protected Button mBtnCamera;
    protected ImageView mImagePreview;

    protected String mTempCameraImagePath;
    private DisplayMetrics mMetrics = null;

    
    
    private int getMaxImageSize()
    {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;
        
        return (int)Math.min(height * 0.4, width*0.6);
    }
    
    @Override
    public void createUI(LinearLayout layout)
    {
        mImagePreview = new ImageView(mActivity);
        
    
      
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;


        LinearLayout.LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        setPreviewImage(mField.getImagePath(), getMaxImageSize());        
        mImagePreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mImagePreview.setAdjustViewBounds(true);
        
                
        mImagePreview.setMaxHeight((int)Math.round(height*0.4));
        mImagePreview.setMaxWidth((int)Math.round(width*0.6));
        
        mBtnGallery = new Button(mActivity);
        mBtnGallery.setText(gtxt(R.string.multimedia_editor_image_field_editing_galery));
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
        mBtnCamera.setText(gtxt(R.string.multimedia_editor_image_field_editing_photo));
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

        layout.addView(mImagePreview, LinearLayout.LayoutParams.MATCH_PARENT, p);
        layout.addView(mBtnGallery, LinearLayout.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnCamera, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    private String gtxt(int id)        
    {
        return mActivity.getText(id).toString();
    }
    
    
    protected DisplayMetrics getDisplayMetrics()
    {
        if(mMetrics == null)
        {
            mMetrics = new DisplayMetrics();
            mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        }
        return mMetrics;
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
        setPreviewImage(mField.getImagePath(), getMaxImageSize());
    }

    @Override
    public void onDone()
    {
        //
    }

    protected void setPreviewImage(String imagePath, int maxsize)
    {
        if (imagePath != null && !imagePath.equals(""))
        {
            // Caused bug on API <= 7
            // mImagePreview.setImageURI(Uri.fromFile(new File(imagePath)));
            
            //fix but crashes with out of memory
            //mImagePreview.setImageURI(Uri.parse(new File(imagePath).toString()));
            
            
            //fix for both
            File f = new File(imagePath);
            
            Bitmap b = BitmapUtil.decodeFile(f, maxsize);
            
            int currentapiVersion = android.os.Build.VERSION.SDK_INT;                
            if (currentapiVersion >= android.os.Build.VERSION_CODES.ECLAIR)
            {
                b = ExifUtil.rotateFromCamera(f, b);
            }
            
            mImagePreview.setImageBitmap(b);
        }
    }

    @Override
    public void onDestroy()
    {
        ImageView imageView = mImagePreview;
        BitmapUtil.freeImageView(imageView);   
    }
}
