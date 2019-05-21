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

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.afollestad.materialdialogs.MaterialDialog;
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

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import timber.log.Timber;

public class BasicImageFieldController extends FieldControllerBase implements IFieldController {

    private static final int ACTIVITY_SELECT_IMAGE = 1;
    private static final int ACTIVITY_TAKE_PICTURE = 2;
    private static final int ACTIVITY_CROP_PICTURE = 3;
    private static final int IMAGE_SAVE_MAX_WIDTH = 1920;

    private ImageView mImagePreview;
    private LinearLayout mLinearLayout;

    private String mTempImagePath;
    private String mTempImagePrePath;//save the last path to prevent crop or take photo action canceled
    private DisplayMetrics mMetrics = null;

    private int showCropButton = 0;

    private static final String sAnkiCacheDirectory = Environment.getExternalStorageDirectory() + "/AnkiDroid/cache";

    private long mTimeStamp;

    private int getMaxImageSize() {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        return (int) Math.min(height * 0.4, width * 0.6);
    }


    @Override
    public void createUI(Context context, LinearLayout layout) {
        mImagePreview = new ImageView(mActivity);
        mLinearLayout = layout;
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        LinearLayout.LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        setPreviewImage(mField.getImagePath(), getMaxImageSize());
        mImagePreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mImagePreview.setAdjustViewBounds(true);

        mImagePreview.setMaxHeight((int) Math.round(height * 0.4));
        mImagePreview.setMaxWidth((int) Math.round(width * 0.6));

        Button mBtnGallery = new Button(mActivity);
        mBtnGallery.setText(gtxt(R.string.multimedia_editor_image_field_editing_galery));
        mBtnGallery.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setType("image/*");
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
                storageDir = new File(sAnkiCacheDirectory);
                image = File.createTempFile("img_" + timeStamp, ".png", storageDir);
                mTempImagePrePath = mTempImagePath;
                mTempImagePath = image.getPath();
                Uri uriSavedImage;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    uriSavedImage = FileProvider.getUriForFile(mActivity,
                            mActivity.getApplicationContext().getPackageName() + ".apkgfileprovider",
                            image);
                } else {
                    uriSavedImage = Uri.fromFile(image);
                }
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
                if (cameraIntent.resolveActivity(context.getPackageManager()) != null) {
                    mActivity.startActivityForResultWithoutAnimation(cameraIntent, ACTIVITY_TAKE_PICTURE);
                } else {
                    Timber.w("Device has a camera, but no app to handle ACTION_IMAGE_CAPTURE Intent");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mBtnCamera.setVisibility(View.INVISIBLE);
        }

        // Some hardware has no camera or reports yes but has zero (e.g., cheap devices, and Chromebook emulator)
        if ((!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) &&
                !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) ||
                (CompatHelper.getCompat().getCameraCount() < 1)) {
            mBtnCamera.setVisibility(View.INVISIBLE);
        }

        layout.addView(mImagePreview, ViewGroup.LayoutParams.MATCH_PARENT, p);
        layout.addView(mBtnGallery, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnCamera, ViewGroup.LayoutParams.MATCH_PARENT);

        mkdirAnkiCacheDir();
        mTimeStamp = System.currentTimeMillis();
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
            switch (requestCode) {
                case ACTIVITY_TAKE_PICTURE:
                case ACTIVITY_CROP_PICTURE:
                    if (!TextUtils.isEmpty(mTempImagePrePath)) {
                        mTempImagePath = mTempImagePrePath;
                    }
                    break;
                default:
                    break;
            }
            return;
        }
        if (requestCode == ACTIVITY_SELECT_IMAGE) {
            handleSelectImageResult(data);
        } else if (requestCode == ACTIVITY_TAKE_PICTURE) {
            handleTakePictureResult();
        } else if (requestCode == ACTIVITY_CROP_PICTURE) {
            handleCropResult();
        }

        showCropButton++;

        if (showCropButton == 1) {
            Button cropBtn = new Button(mActivity);
            cropBtn.setText(gtxt(R.string.crop_button));
            cropBtn.setOnClickListener(v -> {
                Uri uri = getCropUri();
                photoCrop(uri);
            });
            mLinearLayout.addView(cropBtn);
        }
    }


    private void rotateAndCompress() {
        // Set the rotation of the camera image and save as png
        File f = new File(mTempImagePath);
        // use same filename but with png extension for output file
        String outPath = mTempImagePath;
        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        Bitmap b = BitmapUtil.decodeFile(f, IMAGE_SAVE_MAX_WIDTH);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(outPath);
            b = ExifUtil.rotateFromCamera(f, b);
            b.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (FileNotFoundException e) {
            Timber.e(e, "Error in BasicImageFieldController.rotateAndCompress()");
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
        if (imagePath != null && !imagePath.equals("")) {
            File f = new File(imagePath);
            Bitmap b = BitmapUtil.decodeFile(f, maxsize);
            b = ExifUtil.rotateFromCamera(f, b);
            mImagePreview.setImageBitmap(b);
        }
    }


    @Override
    public void onDestroy() {
        ImageView imageView = mImagePreview;
        BitmapUtil.freeImageView(imageView);
        showCropButton = 0;
    }


    public void photoCrop(Uri uri) {
        String fileName = mTempImagePath.substring(mTempImagePath.lastIndexOf("/") + 1, mTempImagePath.lastIndexOf(".")) + (mTimeStamp % 1000000);
        File image = new File(sAnkiCacheDirectory + "/" + fileName + ".png");
        if (!image.exists()) {
            try {
                image.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mTempImagePrePath = mTempImagePath;
        mTempImagePath = image.getPath();
        Uri cropUri = Uri.fromFile(image);
        mField.setImagePath(mTempImagePath);
        mField.setHasTemporaryMedia(true);

        Intent intent = new Intent("com.android.camera.action.CROP");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("return-data", false);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropUri);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());
        intent.putExtra("noFaceDetection", true); // no face detection
        mActivity.startActivityForResultWithoutAnimation(intent, ACTIVITY_CROP_PICTURE);
    }


    private void showCropDialog(Uri uri) {
        new MaterialDialog.Builder(mActivity)
                .content(R.string.crop_image)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, which) -> {
                    photoCrop(uri);
                })
                .build().show();
    }


    private void handleSelectImageResult(Intent data) {
        Uri selectedImage = getImageUri(mActivity, data);
        setPreviewImage(mTempImagePath, getMaxImageSize());
        showCropDialog(selectedImage);
    }


    private void handleTakePictureResult() {
        rotateAndCompress();
        mField.setImagePath(mTempImagePath);
        mField.setHasTemporaryMedia(true);
        setPreviewImage(mTempImagePath, getMaxImageSize());
        Uri uri = getCropUri();
        showCropDialog(uri);
    }




    private void handleCropResult() {
        setPreviewImage(mTempImagePath, getMaxImageSize());
        mField.setImagePath(mTempImagePath);
        mField.setHasTemporaryMedia(true);
        rotateAndCompress();//this is a long-running operation.
    }

    /**
     * Get Uri based on current image path
     * @return current image path's uri
     */
    private Uri getCropUri() {
        File file = new File(mTempImagePath);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(mActivity, mActivity.getApplicationContext().getPackageName() + ".apkgfileprovider", file);
        } else {
            uri = Uri.fromFile(file);
        }
        return uri;
    }


    /**
     * Get image uri that adapts various model
     * @return image uri
     */
    private Uri getImageUri(Context context, Intent data) {
        String imagePath = null;
        Uri uri = data.getData();
        if (Build.VERSION.SDK_INT >= 19) {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                    String id = docId.split(":")[1];
                    String selection = MediaStore.Images.Media._ID + "=" + id;
                    imagePath = getImagePath(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
                } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                    Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                    imagePath = getImagePath(context, contentUri, null);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                imagePath = getImagePath(context, uri, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                imagePath = uri.getPath();
            }
        } else {
            uri = data.getData();
            imagePath = getImagePath(context, uri, null);
        }
        File file = new File(imagePath);
        mTempImagePath = imagePath;
        mField.setImagePath(imagePath);
        mField.setHasTemporaryMedia(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(context,
                    mActivity.getApplicationContext().getPackageName() + ".apkgfileprovider",
                    file);
        } else {
            uri = Uri.fromFile(file);
        }

        return uri;
    }


    /**
     * Get image path based on uri and selection args
     * @return image uri
     */
    private String getImagePath(Context context, Uri uri, String selection) {
        String path = null;
        Cursor cursor = context.getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }


    /**
     * Create Cache Directory
     */
    public void mkdirAnkiCacheDir() {
        boolean isSdCardExist = Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
        if (isSdCardExist) {
            File ankiCropCacheDir = new File(sAnkiCacheDirectory);
            if (!ankiCropCacheDir.exists()) {
                try {
                    ankiCropCacheDir.mkdir();
                } catch (Exception e) {
                }
            }
        }
    }
}
