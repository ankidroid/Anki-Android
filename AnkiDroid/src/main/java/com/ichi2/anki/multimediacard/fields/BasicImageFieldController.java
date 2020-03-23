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
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import androidx.core.content.FileProvider;

import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.BitmapUtil;
import com.ichi2.utils.ExifUtil;
import com.ichi2.utils.Permissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import timber.log.Timber;

public class BasicImageFieldController extends FieldControllerBase implements IFieldController {

    private static final int ACTIVITY_SELECT_IMAGE = 1;
    private static final int ACTIVITY_TAKE_PICTURE = 2;
    private static final int IMAGE_SAVE_MAX_WIDTH = 1920;

    private ImageView mImagePreview;
    private TextView mImageFileSize;
    private TextView mImageFileSizeWarning;

    private String mTempCameraImagePath;
    private DisplayMetrics mMetrics = null;


    private int getMaxImageSize() {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        return (int) Math.min(height * 0.4, width * 0.6);
    }

    // The NewApi deprecation should be removed with API21. UnsupportedChromeOsCameraSystemFeature can be fixed in API16
    @SuppressLint( {"UnsupportedChromeOsCameraSystemFeature", "NewApi"})
    @Override
    public void createUI(Context context, LinearLayout layout) {
        mImagePreview = new ImageView(mActivity);

        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        LinearLayout.LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        mImagePreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mImagePreview.setAdjustViewBounds(true);

        mImagePreview.setMaxHeight((int) Math.round(height * 0.4));
        mImagePreview.setMaxWidth((int) Math.round(width * 0.6));

        mImageFileSize = new EditText(context);
        mImageFileSize.setMaxWidth((int) Math.round(width * 0.6));
        mImageFileSize.setEnabled(false);
        mImageFileSize.setGravity(Gravity.CENTER_HORIZONTAL);
        mImageFileSize.setBackground(null);
        mImageFileSize.setVisibility(View.GONE);

        //#5513 - Image compression failed, but we'll confuse most users if we tell them that. Instead, just imply that
        //there's an action that they can take.
        mImageFileSizeWarning = new EditText(context);
        mImageFileSizeWarning.setMaxWidth((int) Math.round(width * 0.6));
        mImageFileSizeWarning.setEnabled(false);
        mImageFileSizeWarning.setTextColor(Color.parseColor("#FF4500")); //Orange-Red
        mImageFileSizeWarning.setGravity(Gravity.CENTER_HORIZONTAL);
        mImageFileSizeWarning.setVisibility(View.GONE);
        mImageFileSizeWarning.setBackground(null);
        mImageFileSizeWarning.setText(R.string.multimedia_editor_image_compression_failed);
        
        setPreviewImage(mField.getImagePath(), getMaxImageSize());

        Button mBtnGallery = new Button(mActivity);
        mBtnGallery.setText(gtxt(R.string.multimedia_editor_image_field_editing_galery));
        mBtnGallery.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            mActivity.startActivityForResultWithoutAnimation(i, ACTIVITY_SELECT_IMAGE);
        });

        Button mBtnCamera = new Button(mActivity);
        mBtnCamera.setText(gtxt(R.string.multimedia_editor_image_field_editing_photo));
        mBtnCamera.setOnClickListener(v -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File image;
            File storageDir;
            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
            try {
                storageDir = mActivity.getCacheDir();
                image = File.createTempFile("img_" + timeStamp, ".jpg", storageDir);
                mTempCameraImagePath = image.getPath();
                Uri uriSavedImage = FileProvider.getUriForFile(mActivity,
                        mActivity.getApplicationContext().getPackageName() + ".apkgfileprovider",
                        image);

                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);

                // Until Android API21 (maybe 22) you must manually handle permissions for image capture w/FileProvider
                // It does not exist on API15 so they will still crash sadly. This can be removed once minSDK is >= 22
                // https://medium.com/@quiro91/sharing-files-through-intents-part-2-fixing-the-permissions-before-lollipop-ceb9bb0eec3a
                if (CompatHelper.getSdkVersion() <= Build.VERSION_CODES.LOLLIPOP &&
                    CompatHelper.getSdkVersion() >= Build.VERSION_CODES.JELLY_BEAN) {
                    cameraIntent.setClipData(ClipData.newRawUri("", uriSavedImage));
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                if (cameraIntent.resolveActivity(context.getPackageManager()) != null) {
                    mActivity.startActivityForResultWithoutAnimation(cameraIntent, ACTIVITY_TAKE_PICTURE);
                }
                else {
                    Timber.w("Device has a camera, but no app to handle ACTION_IMAGE_CAPTURE Intent");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        if (!Permissions.canUseCamera(context)) {
            mBtnCamera.setVisibility(View.INVISIBLE);
        }

        // Some hardware has no camera or reports yes but has zero (e.g., cheap devices, and Chromebook emulator)
        if ((!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) &&
                !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) ||
                (CompatHelper.getCompat().getCameraCount() < 1)) {
            mBtnCamera.setVisibility(View.INVISIBLE);
        }

        layout.addView(mImagePreview, ViewGroup.LayoutParams.MATCH_PARENT, p);
        layout.addView(mImageFileSize, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mImageFileSizeWarning, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnGallery, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnCamera, ViewGroup.LayoutParams.MATCH_PARENT);
    }


    private String gtxt(int id) {
        return mActivity.getText(id).toString();
    }


    private DisplayMetrics getDisplayMetrics() {
        if (mMetrics == null) {
            mMetrics = new DisplayMetrics();
            mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        }
        return mMetrics;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // ignore RESULT_CANCELED but handle image select and take
        if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }
        mImageFileSizeWarning.setVisibility(View.GONE);
        if (requestCode == ACTIVITY_SELECT_IMAGE) {
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
        // do nothing

    }


    @Override
    public void onDone() {
        // do nothing
    }


    private String rotateAndCompress(String inPath) {
        // Set the rotation of the camera image and save as png
        File f = new File(inPath);
        // use same filename but with png extension for output file
        String outPath = inPath.substring(0, inPath.lastIndexOf(".")) + ".png";
        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        Bitmap b = BitmapUtil.decodeFile(f, IMAGE_SAVE_MAX_WIDTH);
        if (b == null) {
            //#5513 - if we can't decode a bitmap, return the original image
            //And display a warning to push users to compress manually.
            mImageFileSizeWarning.setVisibility(View.VISIBLE);
            return inPath;
        }

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(outPath);
            b = ExifUtil.rotateFromCamera(f, b);
            b.compress(Bitmap.CompressFormat.PNG, 90, out);
            f.delete();
            return outPath;
        } catch (FileNotFoundException e) {
            Timber.e(e, "Error in BasicImageFieldController.rotateAndCompress()");
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


    private void setPreviewImage(String imagePath, int maxsize) {
        if (imagePath != null && !"".equals(imagePath)) {
            File f = new File(imagePath);
            Bitmap b = BitmapUtil.decodeFile(f, maxsize);
            b = ExifUtil.rotateFromCamera(f, b);
            mImagePreview.setImageBitmap(b);
            mImageFileSize.setVisibility(View.VISIBLE);
            mImageFileSize.setText(Formatter.formatFileSize(mActivity, f.length()));
        }
    }


    @Override
    public void onDestroy() {
        ImageView imageView = mImagePreview;
        BitmapUtil.freeImageView(imageView);
    }
}
