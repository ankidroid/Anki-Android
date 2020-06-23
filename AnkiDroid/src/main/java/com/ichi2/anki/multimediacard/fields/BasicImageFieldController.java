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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.FileProvider;

import android.text.TextUtils;
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

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.utils.SystemTime;
import com.ichi2.libanki.utils.TimeUtils;
import com.ichi2.utils.BitmapUtil;
import com.ichi2.utils.ExifUtil;
import com.ichi2.utils.Permissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import timber.log.Timber;

public class BasicImageFieldController extends FieldControllerBase implements IFieldController {

    @VisibleForTesting
    static final int ACTIVITY_SELECT_IMAGE = 1;
    private static final int ACTIVITY_TAKE_PICTURE = 2;
    private static final int ACTIVITY_CROP_PICTURE = 3;
    private static final int IMAGE_SAVE_MAX_WIDTH = 1920;

    private ImageView mImagePreview;
    private TextView mImageFileSize;
    private TextView mImageFileSizeWarning;

    private @Nullable String mImagePath;
    private @Nullable Uri mImageUri;
    private @Nullable String mPreviousImagePath; // save the latest path to prevent from cropping or taking photo action canceled
    private @Nullable Uri mPreviousImageUri;
    private @Nullable String mAnkiCacheDirectory;
    private DisplayMetrics mMetrics = null;
    private SystemTime mTime = new SystemTime();


    private int getMaxImageSize() {
        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        return (int) Math.min(height * 0.4, width * 0.6);
    }

    // The NewApi deprecation should be removed with API21
    @SuppressLint("NewApi")
    @Override
    public void createUI(Context context, LinearLayout layout) {
        Timber.d("createUI()");
        mImagePath = mField.getImagePath();
        mImagePreview = new ImageView(mActivity);
        File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir != null) {
            mAnkiCacheDirectory = externalCacheDir.getAbsolutePath();
        }
        LinearLayout.LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        drawUIComponents(context);


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
            try {
                // Store the old image path for deletion / error handling if the user cancels
                image = createNewFile();
                mPreviousImagePath = mImagePath;
                mPreviousImageUri = mImageUri;
                mImageUri = null;
                mImagePath = image.getPath();
                mImageUri = getUriForFile(image);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);

                // Until Android API21 (maybe 22) you must manually handle permissions for image capture w/FileProvider
                // This can be removed once minSDK is >= 22
                // https://medium.com/@quiro91/sharing-files-through-intents-part-2-fixing-the-permissions-before-lollipop-ceb9bb0eec3a
                if (CompatHelper.getSdkVersion() <= Build.VERSION_CODES.LOLLIPOP) {
                    cameraIntent.setClipData(ClipData.newRawUri("", mImageUri));
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                if (cameraIntent.resolveActivity(context.getPackageManager()) != null) {
                    mActivity.startActivityForResultWithoutAnimation(cameraIntent, ACTIVITY_TAKE_PICTURE);
                } else {
                    Timber.w("Device has a camera, but no app to handle ACTION_IMAGE_CAPTURE Intent");
                }
            } catch (IOException e) {
                Timber.w(e, "mBtnCamera::onClickListener() unable to prepare file and launch camera");
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

        setPreviewImage(mImagePath, getMaxImageSize());

        layout.addView(mImagePreview, ViewGroup.LayoutParams.MATCH_PARENT, p);
        layout.addView(mImageFileSize, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mImageFileSizeWarning, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnGallery, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(mBtnCamera, ViewGroup.LayoutParams.MATCH_PARENT);
    }


    private File createNewFile() throws IOException {
        String timeStamp = TimeUtils.getTimestamp(mTime);
        File storageDir = new File(mAnkiCacheDirectory);
        return File.createTempFile("img_" + timeStamp, ".jpg", storageDir);
    }


    @SuppressLint("NewApi") //Conditionally called anything which requires API 16+.
    private void drawUIComponents(Context context) {
        mImagePreview = new ImageView(mActivity);

        DisplayMetrics metrics = getDisplayMetrics();

        int height = metrics.heightPixels;
        int width = metrics.widthPixels;


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
        mImageFileSize.setBackground(null);
        mImageFileSizeWarning.setText(R.string.multimedia_editor_image_compression_failed);
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
        Timber.d("onActivityResult()");
        if (resultCode == Activity.RESULT_CANCELED) {
            Timber.d("Activity was cancelled");
            // Restore the old version of the image if the user cancelled
            switch (requestCode) {
                case ACTIVITY_TAKE_PICTURE:
                case ACTIVITY_CROP_PICTURE:
                    if (!TextUtils.isEmpty(mPreviousImagePath)) {
                        mImagePath = mPreviousImagePath;
                        mImageUri = mPreviousImageUri;
                    }
                    break;
                default:
                    break;
            }
            return;
        }

        mImageFileSizeWarning.setVisibility(View.GONE);
        if (requestCode == ACTIVITY_SELECT_IMAGE) {
            try {
                handleSelectImageIntent(data);
                mImageFileSizeWarning.setVisibility(View.GONE);
            } catch (Exception e) {
                AnkiDroidApp.sendExceptionReport(e, "BasicImageFieldController - handleSelectImageIntent");
                Timber.e(e, "Failed to select image");
                showSomethingWentWrong();
                return;
            }
        } else if (requestCode == ACTIVITY_TAKE_PICTURE) {
            handleTakePictureResult();
        } else {
            Timber.w("Unhandled request code: %d", requestCode);
            return;
        }
        setPreviewImage(mImagePath, getMaxImageSize());
    }


    private void showSomethingWentWrong() {
        UIUtils.showThemedToast(mActivity, mActivity.getResources().getString(R.string.multimedia_editor_something_wrong), false);
    }


    private void handleSelectImageIntent(Intent data) {
        if (data == null) {
            Timber.e("no intent provided");
            showSomethingWentWrong();
            return;
        }

        Timber.i("Handle Select Image. Intent: %s. extras: %s", data, data.getExtras() == null ? "null" : TextUtils.join(", ", data.getExtras().keySet()));
        Uri selectedImage = data.getData();

        if (selectedImage == null) {
            Timber.w("selectedImage was null");
            showSomethingWentWrong();
            return;
        }

        String[] filePathColumn = { MediaColumns.DATA };

        Cursor cursor = mActivity.getContentResolver().query(selectedImage, filePathColumn, null, null, null);

        if (cursor == null) {
            Timber.w("cursor was null");
            showSomethingWentWrong();
            return;
        }

        if (!cursor.moveToFirst()) {
            //TODO: #5909, it would be best to instrument this to see if we can fix the failure
            Timber.w("cursor had no data");
            showSomethingWentWrong();
            return;
        }

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String filePath = cursor.getString(columnIndex);
        cursor.close();

        Timber.i("Decoded image: '%s'", filePath);
        mField.setImagePath(filePath);
    }


    @Override
    public void onFocusLost() {
        // do nothing

    }


    @Override
    public void onDone() {
        // do nothing
    }


    private void rotateAndCompress() {
        Timber.d("rotateAndCompress() on %s", mImagePath);
        // Set the rotation of the camera image and save as png
        File f = new File(mImagePath);
        // use same filename but with png extension for output file
        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        Bitmap b = BitmapUtil.decodeFile(f, IMAGE_SAVE_MAX_WIDTH);
        if (b == null) {
            //#5513 - if we can't decode a bitmap, leave the image alone
            //And display a warning to push users to compress manually.
            Timber.d("rotateAndCompress() unable to decode file %s", mImagePath);
            mImageFileSizeWarning.setVisibility(View.VISIBLE);
            return;
        }

        FileOutputStream out = null;
        try {
            File outFile = createNewFile();
            out = new FileOutputStream(outFile);
            b = ExifUtil.rotateFromCamera(f, b);
            b.compress(Bitmap.CompressFormat.PNG, 90, out);
            if (!f.delete()) {
                Timber.w("rotateAndCompress() delete of pre-compressed image failed %s", mImagePath);
            }
            mImagePath = outFile.getAbsolutePath();
        } catch (FileNotFoundException e) {
            Timber.w(e, "rotateAndCompress() File not found for image compression %s", mImagePath);
        } catch (IOException e) {
            Timber.w(e, "rotateAndCompress() create file failed for file %s", mImagePath);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                Timber.w(e, "rotateAndCompress() Unable to clean up image compression output stream");
            }
        }
    }


    private void setPreviewImage(String imagePath, int maxsize) {
        if (imagePath != null && !"".equals(imagePath)) {
            File f = new File(imagePath);
            setImagePreview(f, maxsize);
        }
    }

    @VisibleForTesting
    void setImagePreview(File f, int maxsize) {
        Bitmap b = BitmapUtil.decodeFile(f, maxsize);
        if (b == null) {
            Timber.i("setImagePreview() could not process image %s", f.getPath());
            return;
        }
        b = ExifUtil.rotateFromCamera(f, b);
        mImagePreview.setImageBitmap(b);
        mImageFileSize.setVisibility(View.VISIBLE);
        mImageFileSize.setText(Formatter.formatFileSize(mActivity, f.length()));
    }


    @Override
    public void onDestroy() {
        ImageView imageView = mImagePreview;
        BitmapUtil.freeImageView(imageView);
    }


    private void handleTakePictureResult() {
        Timber.d("handleTakePictureResult");
        rotateAndCompress();
        mField.setImagePath(mImagePath);
        mField.setHasTemporaryMedia(true);
        showCropDialog(getUriForFile(new File(mImagePath)));
    }


    /**
     * Get Uri based on current image path
     *
     * @param file the file to get URI for
     * @return current image path's uri
     */
    private Uri getUriForFile(File file) {
        Timber.d("getUriForFile() %s", file);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(mActivity, mActivity.getApplicationContext().getPackageName() + ".apkgfileprovider", file);
        } else {
            uri = Uri.fromFile(file);
        }
        return uri;
    }


    public boolean isShowingPreview() {
        return mImageFileSize.getVisibility() == View.VISIBLE;
    }
}
