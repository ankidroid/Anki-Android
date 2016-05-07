/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.multimediacard.fields;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.DisplayMetrics;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.BitmapUtil;
import com.ichi2.utils.ExifUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import timber.log.Timber;

public class BasicImageFieldController extends FieldControllerBase implements IFieldController {

    protected static final int ACTIVITY_SELECT_IMAGE = 1;
    protected static final int ACTIVITY_TAKE_PICTURE = 2;
    protected static final int IMAGE_PREVIEW_MAX_WIDTH = 100;
    private static final int IMAGE_SAVE_MAX_WIDTH = 1920;

    protected Button mBtnGallery;
    protected Button mBtnCamera;
    protected ImageView mImagePreview;

    protected String mTempCameraImagePath;
    private DisplayMetrics mMetrics = null;


    private int getMaxImageSize() {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        return (int) Math.min(height * 0.4, width * 0.6);
    }


    @Override
    public void createUI(Context context, LinearLayout layout) {
        mImagePreview = new ImageView(mActivity);

        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        LinearLayout.LayoutParams p = new LayoutParams(android.view.ViewGroup.LayoutParams.FILL_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        setPreviewImage(mField.getImagePath(), getMaxImageSize());
        mImagePreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mImagePreview.setAdjustViewBounds(true);

        mImagePreview.setMaxHeight((int) Math.round(height * 0.4));
        mImagePreview.setMaxWidth((int) Math.round(width * 0.6));

        mBtnGallery = new Button(mActivity);
        mBtnGallery.setText(gtxt(R.string.multimedia_editor_image_field_editing_galery));
        mBtnGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                mActivity.startActivityForResult(i, ACTIVITY_SELECT_IMAGE);
            }
        });

        mBtnCamera = new Button(mActivity);
        mBtnCamera.setText(gtxt(R.string.multimedia_editor_image_field_editing_photo));
        mBtnCamera.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                File image;
                File storageDir;
                String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
                try {
                    storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    image = File.createTempFile("img_" + timeStamp, ".jpg", storageDir);
                    mTempCameraImagePath = image.getPath();
                    Uri uriSavedImage = Uri.fromFile(image);

                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
                    mActivity.startActivityForResult(cameraIntent, ACTIVITY_TAKE_PICTURE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        // Chromebooks do not support camera capture yet.
        if (CompatHelper.isChromebook()) {
            mBtnCamera.setVisibility(View.INVISIBLE);
        }

        layout.addView(mImagePreview, android.view.ViewGroup.LayoutParams.FILL_PARENT, p);
        layout.addView(mBtnGallery, android.view.ViewGroup.LayoutParams.FILL_PARENT);
        layout.addView(mBtnCamera, android.view.ViewGroup.LayoutParams.FILL_PARENT);
    }


    private String gtxt(int id) {
        return mActivity.getText(id).toString();
    }


    protected DisplayMetrics getDisplayMetrics() {
        if (mMetrics == null) {
            mMetrics = new DisplayMetrics();
            mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        }
        return mMetrics;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            // Do Nothing.
        } else if (requestCode == ACTIVITY_SELECT_IMAGE) {
            Uri selectedImage = data.getData();
            // Timber.d(selectedImage.toString());
            String[] filePathColumn = { MediaColumns.DATA };

            Cursor cursor = mActivity.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String filePath = cursor.getString(columnIndex);
            cursor.close();

            mField.setImagePath(filePath);
        } else if (requestCode == ACTIVITY_TAKE_PICTURE) {
            String imagePath = rotateAndCompress(mTempCameraImagePath);
            mField.setImagePath(imagePath);
            mField.setHasTemporaryMedia(true);
        }
        setPreviewImage(mField.getImagePath(), getMaxImageSize());
    }

    @Override
    public void onFocusLost() {

    }


    @Override
    public void onDone() {
        //
    }


    private String rotateAndCompress(String inPath) {
        // Set the rotation of the camera image and save as png
        File f = new File(inPath);
        // use same filename but with png extension for output file
        String outPath = inPath.substring(0, inPath.lastIndexOf(".")) + ".png";
        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        Bitmap b = BitmapUtil.decodeFile(f, IMAGE_SAVE_MAX_WIDTH);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(outPath);
            b = ExifUtil.rotateFromCamera(f, b);
            b.compress(Bitmap.CompressFormat.PNG, 90, out);
            f.delete();
            return outPath;
        } catch (FileNotFoundException e) {
            Timber.e("Error in BasicImageFieldController.rotateAndCompress() : " + e.getMessage());
            return inPath;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    protected void setPreviewImage(String imagePath, int maxsize) {
        if (imagePath != null && !imagePath.equals("")) {
            // Caused bug on API <= 7
            // mImagePreview.setImageURI(Uri.fromFile(new File(imagePath)));

            // fix but crashes with out of memory
            // mImagePreview.setImageURI(Uri.parse(new File(imagePath).toString()));

            // fix for both
            File f = new File(imagePath);

            Bitmap b = BitmapUtil.decodeFile(f, maxsize);

            int currentapiVersion = android.os.Build.VERSION.SDK_INT;
            if (currentapiVersion >= android.os.Build.VERSION_CODES.ECLAIR) {
                b = ExifUtil.rotateFromCamera(f, b);
            }

            mImagePreview.setImageBitmap(b);
        }
    }


    @Override
    public void onDestroy() {
        ImageView imageView = mImagePreview;
        BitmapUtil.freeImageView(imageView);
    }

}
